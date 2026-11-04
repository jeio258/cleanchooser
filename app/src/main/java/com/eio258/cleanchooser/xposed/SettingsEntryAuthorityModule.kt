package com.eio258.cleanchooser.xposed

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.eio258.cleanchooser.data.RuleRepository
import com.eio258.cleanchooser.domain.AndroidUid
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.OBSERVABLE_ENTRY_KINDS
import com.eio258.cleanchooser.domain.ObservedEntryRecord
import com.eio258.cleanchooser.domain.SyntheticEntryKeys
import com.eio258.cleanchooser.domain.prioritizeApps
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Hooks Settings-owned final candidate lists that do not have a system_server manager API.
 *
 * The unmodified Settings result is mirrored to the remote-preference observation store first
 * (RemoteObservedEntryPersistence), then List Cleaner applies its package rules. Dream ordering
 * is a best-effort presentation hook: it changes returned candidates only, not the selected dream
 * or enabled state, and OEM Settings may ignore the list. All hooks fail open: a missing OEM
 * class is skipped without side effects.
 */
class SettingsEntryAuthorityModule : XposedModule() {
    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val observationPersistence by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteObservedEntryPersistence(preferences, ::record)
    }
    private val policyProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteEntryPolicy(
            preferences = preferences,
            kinds = SETTINGS_KINDS,
            record = ::record,
        )
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        record("MODULE_LOADED")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != SETTINGS_PACKAGE) return
        var installed = 0
        installed += installVpnHooks(param.classLoader)
        installed += installLegacyAutofillHooks(param.classLoader)
        installed += installDreamOrderingHooks(param.classLoader)
        installed += installAccessibilityNavigationCapture(param.classLoader)
        if (installed > 0) {
            policyProvider.start()
            record("HOOKS_READY package=${param.packageName} new=$installed total=${installedMethods.size}")
        } else {
            record("AUTHORITY_CLASSES_UNAVAILABLE package=${param.packageName}")
        }
    }

    /**
     * Capture the real OEM "Downloaded apps" SubSettings route when the user navigates there in
     * Settings. Purely observational: no fragment, preference, setting, or service state is
     * modified. The regular intent handler may have OEM-only extras absent from
     * ActivityTaskManager logs.
     */
    private fun installAccessibilityNavigationCapture(classLoader: ClassLoader): Int {
        val settings = runCatching {
            Class.forName("com.android.settings.SettingsActivity", false, classLoader)
        }.getOrNull() ?: return 0
        var installed = 0
        ReflectionAccess.hierarchyMethods(settings).filter { method ->
            (method.name == "onCreate" && method.parameterTypes.toList() ==
                listOf(Bundle::class.java)) ||
                (method.name == "onNewIntent" && method.parameterTypes.toList() ==
                    listOf(Intent::class.java))
        }.forEach { method ->
            if (install(method, A11Y_SUBPAGE_CAPTURE_ID, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                runCatching {
                    val activity = chain.thisObject as? Activity ?: return@runCatching
                    if (activity.javaClass.name != SUB_SETTINGS_CLASS) return@runCatching
                    val intent = if (method.name == "onNewIntent") {
                        chain.args.firstOrNull() as? Intent
                    } else activity.intent
                    recordSettingsSubpage(activity.javaClass.name, intent, method.name)
                }.onFailure { record("A11Y_SUBPAGE_CAPTURE_ERROR=${it.javaClass.simpleName}") }
                result
            })) installed++
        }
        return installed
    }

    private fun recordSettingsSubpage(activityName: String, intent: Intent?, event: String) {
        val fragment = runCatching { intent?.extras?.getString(SHOW_FRAGMENT_EXTRA) }.getOrNull()
        record("A11Y_SUBPAGE_CAPTURE event=$event activity=$activityName " +
            "fragment=${safeClassName(fragment)}")
    }

    /**
     * AOSP Settings DreamBackend returns DreamInfo rows already alphabetized.
     * Reorder only the returned presentation list, never enabled/default dream state.
     * Missing or changed OEM implementations are ignored without side effects.
     */
    private fun installDreamOrderingHooks(classLoader: ClassLoader): Int {
        val clazz = runCatching {
            Class.forName("com.android.settingslib.dream.DreamBackend", false, classLoader)
        }.getOrNull() ?: runCatching {
            Class.forName("com.android.settings.dream.DreamBackend", false, classLoader)
        }.getOrNull() ?: return 0
        var installed = 0
        ReflectionAccess.hierarchyMethods(clazz).filter { method ->
            method.name == "getDreamInfos" && method.parameterCount == 0 &&
                List::class.java.isAssignableFrom(method.returnType)
        }.forEach { method ->
            if (install(method, DREAM_HOOK_ID, XposedInterface.Hooker { chain ->
                val original = chain.proceed()
                val values = original as? List<*> ?: return@Hooker original
                if (values.size < 2) return@Hooker original
                val userId = AndroidUid.userId(Process.myUid())
                val ranking = policyProvider.snapshot(userId).priorities(IntentKind.DREAM)
                if (ranking.isEmpty()) return@Hooker original
                val ordered = prioritizeApps(
                    values, ranking,
                    { item ->
                        (ReflectionAccess.readField(item, "componentName", "mComponentName")
                            as? ComponentName)?.packageName.orEmpty()
                    },
                    { userId },
                )
                if (ordered == values) original else ArrayList(ordered)
            })) installed++
        }
        return installed
    }

    private fun installVpnHooks(classLoader: ClassLoader): Int {
        val clazz = runCatching { Class.forName(VPN_SETTINGS_CLASS, false, classLoader) }.getOrNull()
            ?: return 0
        val terminal = ReflectionAccess.hierarchyMethods(clazz)
            .filter { method ->
                method.name == "getVpnApps" && Modifier.isStatic(method.modifiers) &&
                    List::class.java.isAssignableFrom(method.returnType)
            }
            .maxByOrNull(Method::getParameterCount)
            ?: return 0
        return if (install(terminal, VPN_HOOK_ID, vpnHooker())) 1 else 0
    }

    private fun installLegacyAutofillHooks(classLoader: ClassLoader): Int {
        val clazz = runCatching { Class.forName(DEFAULT_AUTOFILL_PICKER_CLASS, false, classLoader) }.getOrNull()
            ?: return 0
        var installed = 0
        ReflectionAccess.hierarchyMethods(clazz)
            .filter { method ->
                method.name == "getCandidates" && method.parameterCount == 0 &&
                    List::class.java.isAssignableFrom(method.returnType)
            }
            .forEach { method ->
                if (install(method, AUTOFILL_HOOK_ID, legacyAutofillHooker())) installed++
            }
        return installed
    }

    private fun vpnHooker() = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val values = original as? List<*> ?: return@Hooker original
        // Mirror the unmodified Settings list first: missing rows mean the authority
        // no longer exposes the package, so this is a complete per-kind snapshot.
        values.groupBy(::vpnUserId).forEach { (userId, entries) ->
            if (userId != null) {
                observationPersistence.replaceKind(
                    IntentKind.VPN,
                    entries.mapNotNull(::vpnPackageRecord),
                    userId = userId,
                )
            }
        }
        val filtered = values.filter { value ->
            val userId = vpnUserId(value) ?: return@filter true
            val packageName = vpnPackageName(value) ?: return@filter true
            val policy = policyProvider.snapshot(userId)
            val selectedPackages = selectedPackagesFor(policy.selected(IntentKind.VPN), IntentKind.VPN)
            selectedPackages.isEmpty() ||
                policy.modeFor(IntentKind.VPN).includes(packageName in selectedPackages, true)
        }.toMutableList()
        filtered.indices.groupBy { vpnUserId(filtered[it]) }.forEach { (userId, positions) ->
            if (userId == null) return@forEach
            val policy = policyProvider.snapshot(userId)
            val ordered = prioritizeApps(
                positions.map { filtered[it] }, policy.priorities(IntentKind.VPN),
                { vpnPackageName(it).orEmpty() }, { userId },
            )
            positions.forEachIndexed { index, position -> filtered[position] = ordered[index] }
        }
        if (filtered == values) original else ArrayList(filtered)
    }

    private fun vpnUserId(value: Any?): Int? =
        ((ReflectionAccess.readField(value, "userId", "mUserId") as? Number)?.toInt()
            ?: (ReflectionAccess.invokeNoArg(value, "getUserId") as? Number)?.toInt())
            ?.takeIf { it in 0..21474 }

    private fun vpnPackageName(value: Any?): String? =
        ReflectionAccess.readString(value, "getPackageName")
            ?: ReflectionAccess.readField(value, "packageName", "mPackageName") as? String

    private fun vpnPackageRecord(value: Any?): ObservedEntryRecord? {
        val userId = vpnUserId(value) ?: return null
        val packageName = vpnPackageName(value) ?: return null
        if (IntentKind.VPN !in OBSERVABLE_ENTRY_KINDS) return null
        val rule = runCatching { SyntheticEntryKeys.packageScopedRule(IntentKind.VPN, packageName) }
            .getOrNull() ?: return null
        return ObservedEntryRecord(
            kind = IntentKind.VPN.name,
            packageName = packageName,
            syntheticClass = rule.className,
            observedAt = System.currentTimeMillis(),
            userId = userId,
        ).validatedOrNull()
    }

    /** Compatibility path for builds that still expose the legacy component picker. */
    private fun legacyAutofillHooker() = XposedInterface.Hooker { chain ->
        val original = chain.proceed()
        val values = original as? List<*> ?: return@Hooker original
        val packages = values.mapNotNull(::autofillPackageName)
        val userId = (ReflectionAccess.readField(chain.thisObject, "mUserId") as? Number)
            ?.toInt()?.takeIf { it in 0..21474 } ?: return@Hooker original

        val policy = policyProvider.snapshot(userId)
        val selectedPackages = selectedPackagesFor(policy.selected(IntentKind.AUTOFILL), IntentKind.AUTOFILL)
        record(
            "AUTOFILL_LEGACY_QUERY before=${values.size} selectedPackages=${selectedPackages.size} mode=${policy.modeFor(IntentKind.AUTOFILL)}"
        )

        // Mirror the unmodified candidate list, then filter.
        runCatching {
            observationPersistence.replaceKind(
                IntentKind.AUTOFILL,
                packages.mapNotNull { packageName ->
                    SystemEntryObservations.packageRecord(
                        IntentKind.AUTOFILL, packageName, userId = userId,
                    )
                },
                userId = userId,
            )
        }

        val filtered = values.filter { value ->
            val packageName = autofillPackageName(value) ?: return@filter true
            policy.modeFor(IntentKind.AUTOFILL).includes(packageName in selectedPackages, selectedPackages.isNotEmpty())
        }
        val ordered = prioritizeApps(
            filtered, policy.priorities(IntentKind.AUTOFILL),
            { autofillPackageName(it).orEmpty() }, { userId },
        )
        if (ordered == values) return@Hooker original
        record(
            "AUTOFILL_LEGACY_FILTER before=${values.size} after=${filtered.size} " +
                "selectedPackages=${selectedPackages.size}"
        )
        ArrayList(ordered)
    }

    private fun autofillPackageName(value: Any?): String? {
        val direct = ReflectionAccess.invokeNoArg(value, "getComponentName") as? ComponentName
            ?: ReflectionAccess.readField(value, "mComponentName", "componentName") as? ComponentName
        if (direct != null) return direct.packageName

        val key = ReflectionAccess.readString(value, "getKey")
            ?: ReflectionAccess.readField(value, "mKey", "key") as? String
            ?: return null
        return ComponentName.unflattenFromString(key)?.packageName
            ?: key.substringBefore('/').takeIf { it.contains('.') }
    }

    private fun safeClassName(value: String?): String? =
        value?.takeIf { it.length <= 160 && it.none(Char::isISOControl) }

    private fun install(method: Method, id: String, hooker: XposedInterface.Hooker): Boolean {
        val key = "$id#${method.toGenericString()}"
        if (!installedMethods.add(key)) return false
        return runCatching {
            method.isAccessible = true
            hook(method).setId(id).intercept(hooker)
            record("HOOK_INSTALLED id=$id method=${method.toGenericString()}")
            true
        }.onFailure {
            installedMethods.remove(key)
            record("HOOK_FAILED id=$id method=${method.toGenericString()} error=${it.javaClass.name}")
        }.getOrDefault(false)
    }

    private fun record(message: String) {
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.SettingsAuthority"
        const val A11Y_SUBPAGE_CAPTURE_ID = "lc-settings-a11y-subpage-capture"
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val SUB_SETTINGS_CLASS = "com.android.settings.SubSettings"
        const val SHOW_FRAGMENT_EXTRA = ":android:show_fragment"
        const val VPN_SETTINGS_CLASS = "com.android.settings.vpn2.VpnSettings"
        const val DEFAULT_AUTOFILL_PICKER_CLASS =
            "com.android.settings.applications.defaultapps.DefaultAutofillPicker"
        const val DREAM_HOOK_ID = "lc-settings-dream-order"
        const val VPN_HOOK_ID = "lc-settings-vpn-authority"
        const val AUTOFILL_HOOK_ID = "lc-settings-autofill-picker"

        val SETTINGS_KINDS = setOf(IntentKind.VPN, IntentKind.AUTOFILL, IntentKind.DREAM)
    }
}

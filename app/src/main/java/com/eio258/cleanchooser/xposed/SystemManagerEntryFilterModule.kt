package com.eio258.cleanchooser.xposed

import android.content.ComponentName
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Process
import android.util.Log
import android.view.inputmethod.InputMethodInfo
import com.eio258.cleanchooser.data.RuleRepository
import com.eio258.cleanchooser.domain.AndroidUid
import com.eio258.cleanchooser.domain.ComponentRule
import com.eio258.cleanchooser.domain.DisplayMode
import com.eio258.cleanchooser.domain.FilterPolicy
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.ManagerIdentity
import com.eio258.cleanchooser.domain.OBSERVABLE_ENTRY_KINDS
import com.eio258.cleanchooser.domain.prioritizeApps
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Filters Android's final manager lists (Accessibility / InputMethod / Print) in system_server.
 *
 * Fail-open contract: every hook first observes the unmodified authority result (process-local
 * store + remote-preference mirror), then applies the user's list rules only when the caller is
 * an ordinary app or a known configuration UI. A missing framework class, an unknown list
 * wrapper, or a policy read failure always degrades to the original result.
 */
class SystemManagerEntryFilterModule : XposedModule() {
    private data class CallerContext(val uid: Int, val pid: Int, val process: String?, val targetUserId: Int? = null)

    @Volatile private var processName = ""
    private val installedMethods = ConcurrentHashMap.newKeySet<String>()
    private val listResults = SafeListResultExtractor(::record)
    private val imeCallerContext = ThreadLocal<CallerContext?>()

    private val preferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }
    private val observationPersistence by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteObservedEntryPersistence(preferences, ::record)
    }
    private val policyProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        RemoteEntryPolicy(
            preferences = preferences,
            kinds = MANAGER_KINDS,
            record = ::record,
        )
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        record("MODULE_LOADED")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        processName = "system"
        policyProvider.start()
        installAccessibilityHooks(param.classLoader)
        installInputMethodHooks(param.classLoader)
        installInputMethodMenuHooks(param.classLoader)
        installPrintHooks(param.classLoader)
    }

    /** Android 16 switcher receives the final IME/subtype list, not the installed catalog. */
    private fun installInputMethodMenuHooks(classLoader: ClassLoader) {
        val clazz = runCatching {
            Class.forName("com.android.server.inputmethod.InputMethodMenuController", false, classLoader)
        }.getOrNull() ?: return
        ReflectionAccess.hierarchyMethods(clazz).filter { it.name == "show" && it.parameterTypes.map { type -> type.name } ==
            listOf("java.util.List", "java.lang.String", "int", "boolean", "int", "int")
        }.forEach { method ->
            install(method, "lc-ime-switcher-menu", XposedInterface.Hooker { chain ->
                val userId = (chain.args[5] as? Int)?.takeIf { it in 0..21474 }
                    ?: return@Hooker chain.proceed()
                val values = chain.args[0] as? List<*> ?: return@Hooker chain.proceed()
                fun info(value: Any?): ServiceInfo? =
                    (ReflectionAccess.readField(value, "mImi") as? InputMethodInfo)?.serviceInfo
                val policy = policyProvider.snapshot(userId)
                val selected = policy.selected(IntentKind.INPUT_METHOD)
                val filtered = values.filter { value ->
                    val service = info(value) ?: return@filter true
                    selected.isEmpty() || policy.modeFor(IntentKind.INPUT_METHOD).includes(
                        ComponentRule(IntentKind.INPUT_METHOD, service.packageName, service.name).id in selected, true,
                    )
                }
                val ordered = prioritizeApps(filtered, policy.priorities(IntentKind.INPUT_METHOD),
                    { info(it)?.packageName.orEmpty() }, { userId })
                if (ordered == values) chain.proceed() else {
                    val replacement = chain.args.toTypedArray()
                    replacement[0] = ArrayList(ordered)
                    chain.proceed(replacement)
                }
            })
        }
    }

    private fun installAccessibilityHooks(classLoader: ClassLoader) {
        ACCESSIBILITY_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull()
                ?: return@forEach
            managerMethods(clazz) { it.name == "getInstalledAccessibilityServiceList" }
                .forEach { method ->
                    install(method, ACCESSIBILITY_HOOK_ID, managerListHooker(
                        kind = IntentKind.ACCESSIBILITY,
                        componentOf = ::accessibilityComponent,
                    ))
                }
        }
    }

    private fun installPrintHooks(classLoader: ClassLoader) {
        PRINT_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull()
                ?: return@forEach
            managerMethods(clazz) { it.name == "getPrintServices" }
                .forEach { method ->
                    install(method, PRINT_HOOK_ID, managerListHooker(
                        kind = IntentKind.PRINT,
                        componentOf = ::printComponent,
                    ))
                }
        }
    }

    /** Android 15+ may return InputMethodInfoSafeList; bridge caller identity to the internal List. */
    private fun installInputMethodHooks(classLoader: ClassLoader) {
        IME_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, classLoader) }.getOrNull()
                ?: return@forEach
            ReflectionAccess.hierarchyMethods(clazz).forEach { method ->
                when {
                    method.name in IME_OUTER_METHODS &&
                        (supportedListResult(method) || method.returnType.name.endsWith("InputMethodInfoSafeList")) ->
                        install(method, IME_OUTER_HOOK_ID, imeOuterHooker())

                    method.name in setOf("getInputMethodListInternal", "getEnabledInputMethodListInternal") && List::class.java.isAssignableFrom(method.returnType) ->
                        install(method, IME_INTERNAL_HOOK_ID, imeInternalHooker())
                }
            }
        }
    }

    private fun managerListHooker(
        kind: IntentKind,
        componentOf: (Any?) -> ServiceInfo?,
    ) = XposedInterface.Hooker { chain ->
        val caller = currentCaller()
        val method = chain.executable as? Method ?: return@Hooker chain.proceed()
        val userId = ManagerCallIdentity.managerTargetUserId(method.name, method.parameterTypes.map { it.name }, chain.args)
            ?: return@Hooker chain.proceed()
        val original = chain.proceed()
        observeComponentResult(original, kind, componentOf, userId)
        val policy = policyProvider.snapshot(userId)
        // ListCleaner's Accessibility preview deliberately queries the installed
        // catalog. Apply the same list-only filter to that manager request; the
        // enabled-services API and persisted accessibility settings are untouched.
        val managerAccessibilityPreview = kind == IntentKind.ACCESSIBILITY &&
            ManagerIdentity.matches(caller.uid, policy.managerAppId)
        val returned = if (!shouldFilter(caller, policy) && !managerAccessibilityPreview) {
            record("AUTHORITY_OBSERVED kind=$kind callerUid=${caller.uid} filterAllowed=false")
            original
        } else {
            filterComponentResult(original, kind, caller.uid, policy, componentOf)
        }
        returned
    }

    private fun imeOuterHooker() = XposedInterface.Hooker { chain ->
        val caller = currentCaller()
        val method = chain.executable as? Method ?: return@Hooker chain.proceed()
        val userId = ManagerCallIdentity.managerTargetUserId(method.name, method.parameterTypes.map { it.name }, chain.args)
            ?: return@Hooker chain.proceed()
        val previous = imeCallerContext.get()
        imeCallerContext.set(caller.copy(targetUserId = userId))
        try {
            val original = chain.proceed()
            val policy = policyProvider.snapshot(userId)
            if (listResults.extract(original) != null) {
                observeComponentResult(original, IntentKind.INPUT_METHOD, ::inputMethodComponent, userId)
                if (shouldFilter(caller, policy)) {
                    filterComponentResult(original, IntentKind.INPUT_METHOD, caller.uid, policy, ::inputMethodComponent)
                } else {
                    record("AUTHORITY_OBSERVED kind=${IntentKind.INPUT_METHOD} callerUid=${caller.uid} filterAllowed=false")
                    original
                }
            } else {
                record(
                    "IME_BRIDGE outerResult=${original?.javaClass?.name ?: "null"} callerUid=${caller.uid} " +
                        "callerProcess=${caller.process ?: "unknown"}"
                )
                original
            }
        } finally {
            if (previous == null) imeCallerContext.remove() else imeCallerContext.set(previous)
        }
    }

    private fun imeInternalHooker() = XposedInterface.Hooker { chain ->
        val caller = imeCallerContext.get() ?: return@Hooker chain.proceed()
        val userId = caller.targetUserId ?: return@Hooker chain.proceed()
        val original = chain.proceed()
        observeComponentResult(original, IntentKind.INPUT_METHOD, ::inputMethodComponent, userId)
        val policy = policyProvider.snapshot(userId)
        if (!shouldFilter(caller, policy)) return@Hooker original
        filterComponentResult(original, IntentKind.INPUT_METHOD, caller.uid, policy, ::inputMethodComponent)
    }

    private fun observeComponentResult(
        original: Any?,
        kind: IntentKind,
        componentOf: (Any?) -> ServiceInfo?,
        userId: Int,
    ) {
        val result = listResults.extract(original) ?: return
        if (kind !in OBSERVABLE_ENTRY_KINDS) return
        result.values.forEach { value ->
            componentOf(value)?.let { service ->
                val component = ComponentName(service.packageName, service.name)
                RuntimeObservedEntryStore.observeComponent(kind, component, userId = userId)
                observationPersistence.merge(
                    listOfNotNull(
                        SystemEntryObservations.componentRecord(kind, component, userId = userId)
                    )
                )
            }
        }
    }

    private fun filterComponentResult(
        original: Any?,
        kind: IntentKind,
        callerUid: Int,
        policy: RemoteEntryPolicy.Snapshot,
        componentOf: (Any?) -> ServiceInfo?,
    ): Any? {
        val result = listResults.extract(original) ?: return original
        if (result.values.isEmpty()) return original

        val selected = policy.selected(kind)
        val priorities = policy.priorities(kind)
        if ((policy.modeFor(kind) == DisplayMode.SHOW_ALL || selected.isEmpty()) && priorities.isEmpty()) {
            record("MANAGER_HIT kind=$kind callerUid=$callerUid count=${result.values.size}")
            return original
        }

        var removed = 0
        val filtered = if (policy.modeFor(kind) == DisplayMode.SHOW_ALL || selected.isEmpty()) {
            result.values
        } else {
            result.values.filter { value ->
                val service = componentOf(value) ?: return@filter true
                val rule = ComponentRule(kind, service.packageName, service.name)
                if (!rule.isValid()) return@filter true
                val include = policy.modeFor(kind).includes(rule.id in selected, selected.isNotEmpty())
                if (!include) removed++
                include
            }
        }
        val ordered = if (priorities.isEmpty() || filtered.size < 2) filtered else prioritizeApps(
            filtered,
            priorities,
            { value -> componentOf(value)?.packageName ?: "" },
            { value -> AndroidUid.userId(componentOf(value)?.applicationInfo?.uid ?: 0) },
        )
        if (removed == 0 && ordered == result.values) return original
        record(
            "MANAGER_FILTER kind=$kind callerUid=$callerUid before=${result.values.size} " +
                "after=${ordered.size} removed=$removed priorities=${priorities.size}"
        )
        return result.rebuild(ordered)
    }

    private fun accessibilityComponent(value: Any?): ServiceInfo? =
        (ReflectionAccess.invokeNoArg(value, "getResolveInfo") as? android.content.pm.ResolveInfo)
            ?.serviceInfo

    private fun printComponent(value: Any?): ServiceInfo? =
        (ReflectionAccess.invokeNoArg(value, "getResolveInfo") as? android.content.pm.ResolveInfo)
            ?.serviceInfo

    private fun inputMethodComponent(value: Any?): ServiceInfo? = when (value) {
        is InputMethodInfo -> value.serviceInfo
        else -> ReflectionAccess.invokeNoArg(value, "getServiceInfo") as? ServiceInfo
    }

    private fun shouldFilter(caller: CallerContext, policy: RemoteEntryPolicy.Snapshot): Boolean {
        if (ManagerIdentity.matches(caller.uid, policy.managerAppId)) return false
        if (FilterPolicy.ordinaryAppCaller(caller.uid)) return true
        if (caller.pid <= 0 || caller.pid == Process.myPid()) return false
        val value = caller.process?.lowercase() ?: return false
        return "settings" in value || "permissioncontroller" in value ||
            "rolecontroller" in value || "role.controller" in value
    }

    private fun currentCaller(): CallerContext {
        val uid = Binder.getCallingUid()
        val pid = Binder.getCallingPid()
        return CallerContext(uid, pid, callerProcessName(pid))
    }

    private fun callerProcessName(pid: Int): String? {
        if (pid <= 0) return null
        return runCatching {
            File("/proc/$pid/cmdline").inputStream().use { input ->
                val buffer = ByteArray(256)
                val count = input.read(buffer)
                if (count <= 0) null else String(buffer, 0, count)
                    .substringBefore('\u0000').trim().ifBlank { null }
            }
        }.getOrNull()
    }

    private fun managerMethods(
        clazz: Class<*>,
        nameFilter: (Method) -> Boolean,
    ): Sequence<Method> = ReflectionAccess.hierarchyMethods(clazz)
        .filterNot { Modifier.isAbstract(it.modifiers) || it.declaringClass.isInterface }
        .filter(nameFilter)
        .filter(::supportedListResult)
        .distinctBy(Method::toGenericString)

    private fun supportedListResult(method: Method): Boolean =
        List::class.java.isAssignableFrom(method.returnType) ||
            method.returnType.name.endsWith("ParceledListSlice")

    private fun install(method: Method, id: String, hooker: XposedInterface.Hooker) {
        val key = "$id#${method.toGenericString()}"
        if (!installedMethods.add(key)) return
        runCatching {
            method.isAccessible = true
            hook(method).setId(id).intercept(hooker)
            record("HOOK_INSTALLED id=$id method=${method.toGenericString()}")
        }.onFailure {
            installedMethods.remove(key)
            record(
                "HOOK_FAILED id=$id method=${method.toGenericString()} " +
                    "error=${it.javaClass.name} message=${it.message?.take(160) ?: "none"}"
            )
        }
    }

    private val diagnosticLimiter = EntryDiagnosticLimiter()

    private fun record(message: String) {
        if (!diagnosticLimiter.allow(message)) return
        val line = "pid=${Process.myPid()} process=$processName $message"
        runCatching { Log.i(TAG, line) }
        runCatching { log(Log.INFO, TAG, line) }
    }

    private companion object {
        const val TAG = "ListCleaner.SystemManagers"
        const val ACCESSIBILITY_HOOK_ID = "lc-accessibility-manager-entries"
        const val IME_OUTER_HOOK_ID = "lc-ime-manager-outer"
        const val IME_INTERNAL_HOOK_ID = "lc-ime-manager-internal"
        const val PRINT_HOOK_ID = "lc-print-manager-entries"

        /** The three managers this module actually hooks; kept explicit instead of a table join. */
        val MANAGER_KINDS = setOf(IntentKind.ACCESSIBILITY, IntentKind.INPUT_METHOD, IntentKind.PRINT)
        val ACCESSIBILITY_CLASSES = listOf("com.android.server.accessibility.AccessibilityManagerService")
        val IME_CLASSES = listOf(
            "com.android.server.inputmethod.InputMethodManagerService",
            "com.android.server.inputmethod.ZeroJankProxy",
        )
        val IME_OUTER_METHODS = setOf("getInputMethodList", "getInputMethodListLegacy",
            "getEnabledInputMethodList", "getEnabledInputMethodListLegacy")
        val PRINT_CLASSES = listOf("com.android.server.print.PrintManagerService")
    }
}

package com.eio258.cleanchooser.xposed

import android.content.ComponentName
import com.eio258.cleanchooser.domain.ComponentRule
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.OBSERVABLE_ENTRY_KINDS
import com.eio258.cleanchooser.domain.ObservedEntryRecord
import com.eio258.cleanchooser.domain.SyntheticEntryKeys
import com.eio258.cleanchooser.domain.isPackageScopedEntry

/**
 * Support layer for the P2-B system-entry modules covering the pieces the shared xposed helpers
 * (ReflectionAccess / HookCallIdentity / RemoteEntryPolicy / RemoteObservedEntryPersistence) do
 * not provide. Everything fails open: a hook that cannot understand a signature must never turn
 * a successful framework query into an exception.
 */

/**
 * Call-identity helpers the shared HookCallIdentity object does not expose yet.
 * The package query layout table mirrors upstream HookCallIdentity.packageQueryLayout; ideally
 * these two functions move into HookCallIdentity itself.
 */
internal object ManagerCallIdentity {
    private data class PackageQueryLayout(val user: Int, val caller: Int? = null)

    private fun packageQueryLayout(name: String, types: List<String>): PackageQueryLayout? {
        if (types.take(2) != listOf("android.content.Intent", "java.lang.String")) return null
        if (types.getOrNull(2) !in setOf("int", "long")) return null
        if (!name.endsWith("Internal")) {
            if (name !in setOf(
                    "queryIntentActivities", "queryIntentActivitiesAsUser",
                    "queryIntentServices", "queryIntentServicesAsUser",
                    "queryIntentContentProviders", "queryIntentContentProvidersAsUser",
                )
            ) return null
            return if (types.size == 4 && types[3] == "int") PackageQueryLayout(3) else null
        }
        val start = if (types.getOrNull(3) == "long") 4 else 3
        val tail = types.drop(start)
        val integers = tail.takeWhile { it == "int" }.size
        if (tail.drop(integers).any { it != "boolean" }) return null
        return when (name) {
            "queryIntentActivitiesInternal" -> when {
                integers == 1 && tail.size == 1 -> PackageQueryLayout(start)
                integers == 2 && tail.size in setOf(2, 4) -> PackageQueryLayout(start + 1, start)
                // Android 16 with an extra long prefix carries callerUid, pid, userId.
                integers == 3 && tail.size == 5 ->
                    PackageQueryLayout(start + 2, if (start == 3) start + 1 else start)
                else -> null
            }
            "queryIntentServicesInternal" -> when {
                start == 3 && integers in 2..3 && tail.size - integers in 1..2 ->
                    PackageQueryLayout(start, start + 1)
                else -> null
            }
            else -> null
        }
    }

    /** A package query's target is never inferred from Binder UID: cross-user queries exist. */
    fun packageManagerTargetUserId(
        methodName: String,
        parameterTypeNames: List<String>,
        args: List<Any?>,
    ): Int? = packageQueryLayout(methodName, parameterTypeNames)
        ?.let { args.getOrNull(it.user) as? Int }?.takeIf { it in 0..21474 }

    /** Public manager signatures carry the target user before or after selection flags. */
    fun managerTargetUserId(name: String, types: List<String>, args: List<Any?>): Int? {
        val index = when (name) {
            "getPrintServices" -> 1
            "getInstalledAccessibilityServiceList",
            "getInputMethodList", "getInputMethodListLegacy", "getInputMethodListInternal",
            "getEnabledInputMethodList", "getEnabledInputMethodListLegacy",
            "getEnabledInputMethodListInternal" -> 0
            else -> return null
        }
        if (types.getOrNull(index) != "int") return null
        return (args.getOrNull(index) as? Int)?.takeIf { it in 0..21474 }
    }
}

/** Package-level selection derived from rule IDs; RemoteEntryPolicy only exposes ID sets. */
internal fun selectedPackagesFor(policyIds: Set<String>, kind: IntentKind): Set<String> =
    policyIds.mapNotNull { id ->
        val rule = ComponentRule.fromId(id) ?: return@mapNotNull null
        if (rule.kind != kind || !kind.isPackageScopedEntry()) return@mapNotNull null
        rule.packageName
    }.toSet()

/** Record builders shared by the observing hooks. */
internal object SystemEntryObservations {
    private const val MAX_LABEL_CHARS = ObservedEntryRecord.MAX_LABEL_CHARS

    fun componentRecord(
        kind: IntentKind,
        component: ComponentName,
        label: String = "",
        userId: Int,
    ): ObservedEntryRecord? {
        if (kind !in OBSERVABLE_ENTRY_KINDS || kind.isPackageScopedEntry()) return null
        val rule = ComponentRule(kind, component.packageName, component.className)
        if (!rule.isValid()) return null
        return ObservedEntryRecord(
            kind = kind.name,
            packageName = rule.packageName,
            syntheticClass = rule.className,
            label = label.take(MAX_LABEL_CHARS),
            activityClass = rule.className,
            observedAt = System.currentTimeMillis(),
            userId = userId,
        ).validatedOrNull()
    }

    fun packageRecord(
        kind: IntentKind,
        packageName: String,
        label: String = "",
        userId: Int,
    ): ObservedEntryRecord? {
        if (kind !in OBSERVABLE_ENTRY_KINDS || !kind.isPackageScopedEntry()) return null
        val rule = runCatching { SyntheticEntryKeys.packageScopedRule(kind, packageName) }.getOrNull()
            ?: return null
        return ObservedEntryRecord(
            kind = kind.name,
            packageName = packageName,
            syntheticClass = rule.className,
            label = label.take(MAX_LABEL_CHARS),
            observedAt = System.currentTimeMillis(),
            userId = userId,
        ).validatedOrNull()
    }
}

/** Bounds chatty per-query logs in hot system_server hooks. One limiter belongs to one module. */
internal class EntryDiagnosticLimiter(
    private val maxEventsPerWindow: Int = 48,
    private val windowMillis: Long = 5_000L,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var window: Long = Long.MIN_VALUE
    private var count: Int = 0

    @Synchronized
    fun allow(message: String): Boolean {
        if (!message.startsWith("QUERY ") &&
            !message.startsWith("RESULT ") &&
            !message.startsWith("SYSTEM_CALLER_BYPASS ") &&
            !message.startsWith("MANAGER_HIT ") &&
            !message.startsWith("AUTHORITY_OBSERVED ")
        ) return true
        val bucket = elapsedMillis() / windowMillis
        if (window != bucket) {
            window = bucket
            count = 0
        }
        count++
        return count <= maxEventsPerWindow
    }
}

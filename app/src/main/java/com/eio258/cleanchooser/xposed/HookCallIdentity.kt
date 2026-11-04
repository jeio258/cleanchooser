package com.eio258.cleanchooser.xposed

/**
 * Reflection-layout helpers for system_server hooks.
 *
 * Internal PackageManager/ShortcutService methods often carry the original caller UID explicitly.
 * Binder.getCallingUid() is not authoritative after framework code clears/restores Binder identity,
 * so hooks prefer the explicit argument whenever a known signature exposes one.
 */
internal object HookCallIdentity {
    data class ShortcutCall(
        val callerUid: Int,
        val callingPackage: String?,
        val queryFlags: Int?,
        val explicitCallerUid: Boolean,
    )

    // Single source of truth for PMS package-query parameter layouts. ManagerCallIdentity
    // (SystemEntrySupport.kt) delegates here with a narrower name whitelist; keep both
    // name sets in sync when adding layouts.
    internal data class PackageQueryLayout(val user: Int, val caller: Int? = null)

    internal fun packageQueryLayout(name: String, types: List<String>): PackageQueryLayout? {
        if (types.take(2) != listOf("android.content.Intent", "java.lang.String")) return null
        if (types.getOrNull(2) !in setOf("int", "long")) return null
        if (!name.endsWith("Internal")) {
            if (name !in setOf("queryIntentActivities", "queryIntentActivitiesAsUser",
                    "queryIntentServices", "queryIntentServicesAsUser",
                    "queryIntentContentProviders", "queryIntentContentProvidersAsUser",
                    "queryIntentReceivers", "queryBroadcastReceivers",
                    "queryBroadcastReceiversAsUser")) return null
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
                // Legacy int flags: privateResolveFlags, filterCallingUid, userId.
                // Android 16 with an extra long prefix carries callerUid, pid, userId.
                integers == 3 && tail.size == 5 ->
                    PackageQueryLayout(start + 2, if (start == 3) start + 1 else start)
                else -> null
            }
            "queryIntentServicesInternal", "queryIntentReceiversInternal",
            "queryBroadcastReceiversInternal" -> when {
                start == 3 && integers in 2..3 && tail.size - integers in 1..2 ->
                    PackageQueryLayout(start, start + 1)
                else -> null
            }
            else -> null
        }
    }

    fun packageManagerCallerUid(
        methodName: String,
        parameterTypeNames: List<String>,
        args: List<Any?>,
        binderUid: Int,
    ): Int = packageQueryLayout(methodName, parameterTypeNames)?.caller
        ?.let { args.getOrNull(it) as? Int }?.takeIf { it >= 0 } ?: binderUid

    fun shortcutTargetUserId(types: List<String>, args: List<Any?>): Int? {
        val component = types.indexOf("android.content.ComponentName")
        if (component < 0) return null
        val flags = component + if (types.getOrNull(component + 1) == "android.content.Intent") 2 else 1
        if (types.getOrNull(flags) != "int" || types.getOrNull(flags + 1) != "int") return null
        return (args.getOrNull(flags + 1) as? Int)?.takeIf { it in 0..21474 }
    }

    fun shortcutCall(
        parameterTypeNames: List<String>,
        args: List<Any?>,
        binderUid: Int,
    ): ShortcutCall {
        val callingPackageIndex = parameterTypeNames.indexOfFirst { it == "java.lang.String" }
        val componentIndex = parameterTypeNames.indexOf("android.content.ComponentName")
        val intsAfterComponent = if (componentIndex >= 0) {
            parameterTypeNames.indices.filter { index ->
                index > componentIndex && parameterTypeNames[index] == "int"
            }
        } else {
            emptyList()
        }

        val queryFlagsIndex = intsAfterComponent.firstOrNull()
        val explicitCallerIndex = intsAfterComponent.getOrNull(3)
        val explicitUid = explicitCallerIndex?.let { args.getOrNull(it) as? Int }
        return ShortcutCall(
            callerUid = explicitUid ?: binderUid,
            callingPackage = callingPackageIndex.takeIf { it >= 0 }
                ?.let { args.getOrNull(it) as? String }
                ?.takeIf { it.isNotBlank() },
            queryFlags = queryFlagsIndex?.let { args.getOrNull(it) as? Int },
            explicitCallerUid = explicitUid != null,
        )
    }
}

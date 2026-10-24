package com.eio258.cleanchooser.data

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.eio258.cleanchooser.ListCleanerApp
import com.eio258.cleanchooser.domain.ComponentStatePolicy

/**
 * Persists the user's desired disabled state separately from Android's current component override.
 * Some apps call PackageManager#setComponentEnabledSetting during startup and would otherwise
 * overwrite a Root `pm disable` performed by ListCleaner.
 */
data class PersistentComponentRef(
    val user: Int,
    val component: ComponentName
)

object PersistentComponentState {
    const val REMOTE_KEY = "root_disabled_components_v1"
    const val LOCAL_PREFS = "root_component_state"
    const val LOCAL_KEY = "disabled_components"
    /** Local-only record of the override value each component had before this app disabled it. */
    const val LOCAL_PRIOR = "prior_overrides"
    private const val MAX_ENTRIES = 20_000

    fun key(user: Int, component: ComponentName): String =
        key(user, component.packageName, component.className)

    fun key(user: Int, packageName: String, className: String): String =
        "$user|$packageName|$className"

    fun parse(key: String): PersistentComponentRef? {
        val parts = key.split('|', limit = 3)
        if (parts.size != 3) return null
        val user = parts[0].toIntOrNull() ?: return null
        val packageName = parts[1]
        val className = parts[2]
        if (!ComponentStatePolicy.valid(packageName, className, user)) return null
        return PersistentComponentRef(user, ComponentName(packageName, className))
    }

    fun isValid(key: String): Boolean = parse(key) != null

    fun sanitize(values: Collection<String>): Set<String> = values.asSequence()
        .filter(::isValid)
        .distinct()
        .take(MAX_ENTRIES)
        .toSet()
}

/** Local source of truth plus a best-effort mirror into libxposed remote preferences. */
class PersistentComponentStore(private val app: ListCleanerApp) {
    private val prefs = app.getSharedPreferences(PersistentComponentState.LOCAL_PREFS, Context.MODE_PRIVATE)

    private fun priorEntries(): Set<String> =
        prefs.getStringSet(PersistentComponentState.LOCAL_PRIOR, emptySet()).orEmpty()

    /** Remembers what the override was before this app disabled the component ("key|prior"). */
    @Synchronized
    fun recordPrior(key: String, priorOverride: Int?) {
        if (priorOverride == null || priorOverride !in 0..4) return
        val next = (priorEntries().filterNot { it.startsWith("$key|") } + "$key|$priorOverride").toSet()
        prefs.edit().putStringSet(PersistentComponentState.LOCAL_PRIOR, next).apply()
    }

    fun priorFor(key: String): Int? =
        priorEntries().firstOrNull { it.startsWith("$key|") }
            ?.substringAfterLast('|')?.toIntOrNull()

    @Synchronized
    fun clearPriors(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val prefixes = keys.toSet()
        val next = priorEntries().filterNot { entry -> prefixes.any { entry.startsWith("$it|") } }.toSet()
        if (next.size == priorEntries().size) return
        prefs.edit().putStringSet(PersistentComponentState.LOCAL_PRIOR, next).apply()
    }

    @Synchronized
    fun disabledKeys(): Set<String> = PersistentComponentState.sanitize(
        prefs.getStringSet(PersistentComponentState.LOCAL_KEY, emptySet()).orEmpty()
    )

    fun isDisabled(component: RootComponent): Boolean =
        PersistentComponentState.key(component.user, component.component) in disabledKeys()

    /**
     * Update local intent first, then mirror the complete set to system_server's remote preferences.
     * A remote write failure does not discard the local intent; the next screen refresh retries it.
     */
    @Synchronized
    fun setDisabled(component: RootComponent, disabled: Boolean, priorOverride: Int? = null): Boolean {
        val key = PersistentComponentState.key(component.user, component.component)
        val next = disabledKeys().toMutableSet().apply {
            if (disabled) add(key) else remove(key)
        }.toSet()
        if (!prefs.edit().putStringSet(PersistentComponentState.LOCAL_KEY, next).commit()) {
            Log.e(TAG, "Failed to persist desired component state for $key")
            return false
        }
        if (disabled) recordPrior(key, priorOverride) else clearPriors(listOf(key))
        return syncRemote(next)
    }

    /**
     * Drops the stored "disabled" intent for the given components and mirrors the remainder.
     * Called after a restore-to-default so the guard and discovery filter stop protecting
     * components the user no longer wants disabled.
     */
    @Synchronized
    fun clear(keys: Collection<String>): Boolean {
        if (keys.isEmpty()) return true
        val current = disabledKeys()
        val next = current - keys.toSet()
        if (next == current) return true
        if (!prefs.edit().putStringSet(PersistentComponentState.LOCAL_KEY, next).commit()) {
            Log.e(TAG, "Failed to clear persisted component state")
            return false
        }
        return syncRemote(next)
    }

    @Synchronized
    fun syncRemote(): Boolean = syncRemote(disabledKeys())

    private fun syncRemote(values: Set<String>): Boolean {
        val session = app.currentSession() ?: return false
        return runCatching {
            val remote = session.service.getRemotePreferences(RuleRepository.REMOTE_PREFS)
            remote.edit()
                .putStringSet(PersistentComponentState.REMOTE_KEY, HashSet(values))
                .commit()
        }.onFailure {
            Log.w(TAG, "Unable to mirror persistent component state to Xposed service", it)
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "ListCleaner.ComponentStore"
    }
}

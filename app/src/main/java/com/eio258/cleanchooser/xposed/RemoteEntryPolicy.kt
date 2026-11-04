package com.eio258.cleanchooser.xposed

import android.content.SharedPreferences
import android.os.SystemClock
import com.eio258.cleanchooser.data.RuleRepository
import com.eio258.cleanchooser.domain.entryGroup
import com.eio258.cleanchooser.domain.DisplayMode
import com.eio258.cleanchooser.domain.EntryGroup
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.ModuleConfig
import kotlinx.serialization.json.Json

/**
 * Remote-preferences fallback policy for specialized Xposed adapters.
 *
 * We have no runtime capture chain in this build, so adapters read the serialized ModuleConfig
 * from the config_v1 remote preference (ComponentStateGuardModule pattern): a preference listener
 * plus a throttled poll keeps every hook within a few seconds of the manager's last write.
 * Missing user profiles fail open instead of borrowing user 0's filtering rules.
 */
internal class RemoteEntryPolicy(
    private val preferences: SharedPreferences,
    private val kinds: Set<IntentKind>,
    private val includePriorities: Boolean = true,
    private val record: (String) -> Unit,
) {
    internal class Snapshot(
        val managerAppId: Int,
        private val selectedIds: Map<IntentKind, Set<String>>,
        private val priorityApps: Map<IntentKind, List<String>>,
        private val modes: Map<IntentKind, DisplayMode>,
    ) {
        fun selected(kind: IntentKind): Set<String> = selectedIds[kind].orEmpty()
        fun priorities(kind: IntentKind): List<String> = priorityApps[kind].orEmpty()
        fun modeFor(kind: IntentKind): DisplayMode = modes[kind] ?: DisplayMode.HIDE_SELECTED
    }

    @Volatile private var config: ModuleConfig? = null
    @Volatile private var nextPoll = 0L
    private val json = Json { ignoreUnknownKeys = true }

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == RuleRepository.KEY_CONFIG) refresh("preference changed")
    }

    fun start() {
        runCatching {
            preferences.registerOnSharedPreferenceChangeListener(listener)
        }.onFailure {
            record("POLICY_LISTENER_FAILED error=${it.javaClass.name}")
        }
        refresh("init")
    }

    @Synchronized
    private fun refresh(reason: String) {
        runCatching {
            config = preferences.getString(RuleRepository.KEY_CONFIG, null)
                ?.let { encoded -> json.decodeFromString(ModuleConfig.serializer(), encoded) }
            record("POLICY_READ reason=$reason config=${config != null}")
        }.onFailure {
            record("POLICY_READ_FAILED reason=$reason error=${it.javaClass.name}")
        }
    }

    private fun current(): ModuleConfig? {
        val now = SystemClock.elapsedRealtime()
        if (now >= nextPoll) {
            nextPoll = now + POLL_INTERVAL_MS
            refresh("poll")
        }
        return config
    }

    fun snapshot(userId: Int): Snapshot {
        val active = current() ?: return Snapshot(-1, emptyMap(), emptyMap(), emptyMap())
        val space = if (userId == 0) active else active.userSpaces[userId]?.toModuleConfig()
            ?: return Snapshot(active.managerAppId, emptyMap(), emptyMap(), emptyMap())

        val selectedIds = kinds.associateWith { kind -> selectedFor(space, kind) }
        val priorityApps = if (includePriorities) {
            space.priorities.apps.filterKeys { kind ->
                kind in kinds && kind.entryGroup() !in space.disabledEntryGroups &&
                    kind !in space.disabledEntryKinds
            }
        } else {
            emptyMap()
        }
        val modes = kinds.associateWith { kind -> space.modeOverrides[kind] ?: space.mode }
        return Snapshot(active.managerAppId, selectedIds, priorityApps, modes)
    }

    private fun selectedFor(config: ModuleConfig, kind: IntentKind): Set<String> {
        if (kind.entryGroup() in config.disabledEntryGroups || kind in config.disabledEntryKinds) {
            return emptySet()
        }
        val ids = config.rules.asSequence()
            .filter { it.kind == kind }
            .mapTo(linkedSetOf()) { it.id }
        if (kind == IntentKind.DIRECT_SHARE) {
            ids.addAll(config.directShareTypes.runtimeIds())
        }
        return ids
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}

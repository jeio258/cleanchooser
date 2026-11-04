package com.eio258.cleanchooser.data

import android.content.Context
import com.eio258.cleanchooser.domain.DisplayMode

/**
 * One-time default upgrades mirroring upstream 5df149e1 (07c35340's all-apps→user-apps part has no
 * persisted counterpart here: workbench app-type filters are in-memory only, so nothing to migrate).
 *
 * Lives outside [RuleRepository] because that file is owned by another change; the in-memory mode is
 * aligned through the public [RuleRepository.setDisplayMode] so no repository edit is required.
 */
object DefaultsMigration {
    private const val PREFS = "rules_local"
    private const val KEY_DISPLAY_MODE = "display_mode"
    private const val KEY_MIGRATED = "profile_hide_selected_default_v1"

    /** Upgrades the old implicit SHOW_ALL profile default once; explicit choices stay intact. */
    fun migrateShowAllToHideSelected(context: Context, repository: RuleRepository) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        if (prefs.getString(KEY_DISPLAY_MODE, null) == DisplayMode.SHOW_ALL.name) {
            repository.setDisplayMode(DisplayMode.HIDE_SELECTED)
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }
}

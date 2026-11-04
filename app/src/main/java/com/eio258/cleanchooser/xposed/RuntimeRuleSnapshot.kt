package com.eio258.cleanchooser.xposed

import com.eio258.cleanchooser.domain.BrowserLinkConfig
import com.eio258.cleanchooser.domain.DisplayMode
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.OpenTypeConfig
import com.eio258.cleanchooser.domain.PriorityConfig
import com.eio258.cleanchooser.domain.VisibilityCompatConfig
import com.eio258.cleanchooser.domain.selectedKinds

internal data class RuntimeRuleSnapshot(
    val configured: Set<String>,
    val displayMode: DisplayMode,
    val priorities: PriorityConfig,
    val openTypes: OpenTypeConfig,
    val browserLinks: BrowserLinkConfig,
    val diagnostic: Boolean,
    val managerAppId: Int = -1,
    val digest: String = "",
    val hiddenFromApps: Set<String> = emptySet(),
    val visibilityCompat: VisibilityCompatConfig = VisibilityCompatConfig(),
    /** Component ids (PROCESS_TEXT) in the user's text-menu order. */
    val textMenuOrder: List<String> = emptyList(),
    /** Per-Android-user rule spaces; user 0 stays in the top-level fields. */
    val userSpaces: Map<Int, com.eio258.cleanchooser.domain.UserSpaceRules> = emptyMap(),
    /** Direct Share per-MIME family rules. */
    val directShareTypes: com.eio258.cleanchooser.domain.DirectShareTypeConfig =
        com.eio258.cleanchooser.domain.DirectShareTypeConfig(),
    /** AOSP presentation filters (SystemUI/DocumentsUI/Settings surfaces). */
    val surfaceFilters: com.eio258.cleanchooser.domain.SurfaceFilterConfig =
        com.eio258.cleanchooser.domain.SurfaceFilterConfig(),
) {
    private val selectedKinds: Set<IntentKind> = selectedKinds(configured)
    val allSelectedPackages: Set<String> = visibilityCompat.activePackages()

    fun hasSelection(kind: IntentKind): Boolean = kind in selectedKinds
}

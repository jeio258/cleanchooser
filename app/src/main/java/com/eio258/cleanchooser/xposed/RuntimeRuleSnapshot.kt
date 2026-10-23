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
) {
    private val selectedKinds: Set<IntentKind> = selectedKinds(configured)
    val allSelectedPackages: Set<String> = visibilityCompat.activePackages()

    fun hasSelection(kind: IntentKind): Boolean = kind in selectedKinds
}

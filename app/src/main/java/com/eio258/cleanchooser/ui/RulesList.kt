package com.eio258.cleanchooser.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.domain.ComponentCandidate
import com.eio258.cleanchooser.domain.DisplayMode
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.matchesBrowserHost
import com.eio258.cleanchooser.domain.matchesOpenPreset

@StringRes
private fun displayModeHelpRes(mode: DisplayMode): Int = when (mode) {
    DisplayMode.HIDE_SELECTED -> R.string.rules_mode_hide_selected
    DisplayMode.SHOW_SELECTED -> R.string.rules_mode_show_selected
    DisplayMode.SHOW_ALL -> R.string.rules_mode_show_all
}

/**
 * Rules segment of the workbench: one app per row, every scope filter sourced from
 * [WorkbenchFilters]. Drill-down lives on the detail page.
 */
@Composable
fun RulesList(
    state: MainState,
    vm: MainViewModel,
    filters: WorkbenchFilters,
    onDetail: (String) -> Unit
) {
    val bulkLockRevision by vm.bulkLockRevision.collectAsState()
    var editingTitle by remember { mutableStateOf<ComponentCandidate?>(null) }
    var showCustomTypes by remember { mutableStateOf(false) }
    var showBrowserHosts by remember { mutableStateOf(false) }

    editingTitle?.let { item ->
        ComponentTitleDialog(
            item,
            state.priorities.titles[item.rule.id],
            onSave = { vm.setComponentTitle(item.rule.id, it) },
            onDismiss = { editingTitle = null }
        )
    }
    if (showCustomTypes) {
        CustomOpenTypeDialog(
            config = state.openTypesExplicit,
            onSave = vm::setCustomOpenDefinition,
            onDismiss = { showCustomTypes = false }
        )
    }
    if (showBrowserHosts) {
        BrowserHostDialog(
            config = state.browserLinks,
            onSave = vm::setBrowserHosts,
            onDismiss = { showBrowserHosts = false }
        )
    }

    // Drop scoped filters whose configuration disappeared while this page was open.
    LaunchedEffect(filters.openPreset, state.openTypesExplicit.customDefinitions) {
        val preset = filters.openPreset
        if (preset?.isCustom == true && preset !in state.openTypesExplicit.customDefinitions) {
            vm.updateWorkbenchFilters { it.copy(openPreset = null) }
        }
    }
    LaunchedEffect(filters.browserHost, state.browserAvailableHosts) {
        val host = filters.browserHost
        if (host != null && host !in state.browserAvailableHosts) {
            vm.updateWorkbenchFilters { it.copy(browserHost = null) }
        }
    }

    val typedOpenPreset = filters.openPreset.takeIf { filters.channel == IntentKind.OPEN }
    val deepLinkHost = filters.browserHost.takeIf { filters.channel == IntentKind.DEEP_LINK }

    val typedSelected = typedOpenPreset?.let { state.openTypes.selectedRules(it) }.orEmpty()
    val explicitDeepLinkSelected = deepLinkHost?.let { state.browserLinks.selectedRules(it) }.orEmpty()
    val genericDeepLinkSelected = state.selected.filterTo(linkedSetOf()) { it.kind == IntentKind.DEEP_LINK }
    val deepLinkSelected = genericDeepLinkSelected + explicitDeepLinkSelected
    val deepLinkScoped = deepLinkHost != null
    val lockScope = deepLinkHost?.let(::browserRuleBulkLockScope)
        ?: ruleBulkLockScope(filters.channel, filters.openPreset)
    val lockScopeCandidates = remember(
        state.candidates, filters.channel, typedOpenPreset, deepLinkHost,
        state.openTypesExplicit.customDefinitions, deepLinkSelected
    ) {
        when {
            typedOpenPreset != null -> state.candidates.filter {
                it.rule.kind == IntentKind.OPEN &&
                    it.matchesOpenPreset(typedOpenPreset, state.openTypesExplicit.customDefinitions)
            }
            deepLinkHost != null -> state.candidates.filter {
                it.rule.kind == IntentKind.DEEP_LINK &&
                    (it.matchesBrowserHost(deepLinkHost) || it.rule in deepLinkSelected)
            }
            else -> state.candidates.filter { filters.channel == null || it.rule.kind == filters.channel }
        }
    }
    val lockRulesByPackage = remember(lockScopeCandidates, bulkLockRevision) {
        lockScopeCandidates.groupBy { it.rule.packageName }
            .mapValues { (_, items) -> items.map { it.rule } }
    }
    val baseShownGroups = remember(
        state.groups, state.candidates, typedOpenPreset, deepLinkHost,
        state.openTypesExplicit.customDefinitions, typedSelected, deepLinkSelected,
        state.query, filters.view
    ) {
        when {
            typedOpenPreset != null -> groupCandidates(
                state.candidates.filter { it.matchesOpenPreset(typedOpenPreset, state.openTypesExplicit.customDefinitions) },
                typedSelected,
                IntentKind.OPEN,
                state.query,
                filters.view
            )
            deepLinkHost != null -> groupCandidates(
                state.candidates.filter {
                    it.rule.kind == IntentKind.DEEP_LINK &&
                        (it.matchesBrowserHost(deepLinkHost) || it.rule in deepLinkSelected)
                },
                deepLinkSelected,
                IntentKind.DEEP_LINK,
                state.query,
                filters.view
            )
            else -> state.groups
        }
    }
    val lockFilteredGroups = if (filters.view == UiFilter.LOCKED) {
        bulkLockRevision
        baseShownGroups.filter { group ->
            if (deepLinkScoped) {
                vm.bulkLockState(
                    lockScope,
                    group.packageName,
                    lockRulesByPackage[group.packageName].orEmpty().map { it.id }
                ) != BulkLockState.NONE
            } else {
                vm.ruleBulkLockState(
                    filters.channel,
                    filters.openPreset,
                    group.packageName,
                    lockRulesByPackage[group.packageName].orEmpty()
                ) != BulkLockState.NONE
            }
        }
    } else {
        baseShownGroups
    }
    val shownGroups = lockFilteredGroups.filter { filters.appType.matches(it.appType) }
    val activeSelected = when {
        typedOpenPreset != null -> typedSelected
        deepLinkHost != null -> deepLinkSelected
        else -> state.selected
    }
    val visibleRules = shownGroups.flatMap { it.components }.map { it.rule }.distinct()

    val presetTitle = when {
        typedOpenPreset != null -> state.openTypes.localizedTitle(typedOpenPreset)
        deepLinkHost != null -> deepLinkHost
        else -> null
    }
    val usageHelp = if (presetTitle == null) {
        stringResource(R.string.rules_usage_help)
    } else {
        stringResource(R.string.rules_typed_help, presetTitle)
    }
    val helpText = stringResource(R.string.rules_page_intro) + "\n\n" + usageHelp + "\n\n" +
        stringResource(displayModeHelpRes(state.displayMode))

    Column(Modifier.fillMaxSize()) {
        ListHeader(
            channels = listOf<IntentKind?>(null) + IntentKind.entries,
            selectedChannel = filters.channel,
            channelLabel = { kind ->
                kind?.let { stringResource(it.titleRes()) } ?: stringResource(R.string.common_all)
            },
            onChannel = { next ->
                vm.updateWorkbenchFilters { current ->
                    current.copy(
                        channel = next,
                        openPreset = if (next == IntentKind.OPEN) current.openPreset else null,
                        browserHost = if (next == IntentKind.DEEP_LINK) current.browserHost else null
                    )
                }
            },
            view = filters.view,
            viewTitle = { stringResource(it.titleRes()) },
            onView = { next ->
                vm.updateWorkbenchFilters { current -> current.copy(view = next) }
            },
            appType = filters.appType,
            onAppType = { next ->
                vm.updateWorkbenchFilters { current -> current.copy(appType = next) }
            },
            helpText = helpText,
            loading = state.loading,
            extraFilter = {
                when (filters.channel) {
                    IntentKind.OPEN -> OpenPresetFilterMenu(
                        selected = filters.openPreset,
                        config = state.openTypesExplicit,
                        onSelected = { preset ->
                            vm.updateWorkbenchFilters { current -> current.copy(openPreset = preset) }
                        },
                        onManageCustom = { showCustomTypes = true }
                    )
                    IntentKind.DEEP_LINK -> BrowserHostFilterMenu(
                        selected = filters.browserHost,
                        config = state.browserLinks,
                        availableHosts = state.browserAvailableHosts,
                        onSelected = { host ->
                            vm.updateWorkbenchFilters { current -> current.copy(browserHost = host) }
                        },
                        onManage = { showBrowserHosts = true }
                    )
                    else -> Unit
                }
            },
            onSelectAll = {
                when {
                    typedOpenPreset != null ->
                        vm.selectOpenTypeRules(typedOpenPreset, visibleRules, lockScope)
                    deepLinkHost != null ->
                        vm.selectBrowserHostRules(deepLinkHost, visibleRules, lockScope)
                    else -> vm.selectRules(visibleRules, filters.channel, filters.openPreset)
                }
            },
            onSelectNone = {
                when {
                    typedOpenPreset != null ->
                        vm.deselectOpenTypeRules(typedOpenPreset, visibleRules, lockScope)
                    deepLinkHost != null ->
                        vm.deselectBrowserHostRules(deepLinkHost, visibleRules, lockScope)
                    else -> vm.deselectRules(visibleRules, filters.channel, filters.openPreset)
                }
            },
            onInvert = {
                when {
                    typedOpenPreset != null ->
                        vm.invertOpenTypeRules(typedOpenPreset, visibleRules, lockScope)
                    deepLinkHost != null ->
                        vm.invertBrowserHostRules(deepLinkHost, visibleRules, lockScope)
                    else -> vm.invertRules(visibleRules, filters.channel, filters.openPreset)
                }
            }
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "rules-summary") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        stringResource(
                            R.string.list_summary_format,
                            if (presetTitle == null) {
                                stringResource(R.string.app_list_count, shownGroups.size)
                            } else {
                                stringResource(R.string.app_list_count_type, shownGroups.size, presetTitle)
                            },
                            stringResource(state.displayMode.titleRes())
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.error?.let {
                        Text(
                            stringResource(R.string.rules_refresh_incomplete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (filters.openPreset == null && filters.view != UiFilter.SHOW_SELECTED && state.candidates.any {
                            it.rule in state.selected && (it.unavailable || it.restricted)
                        }) {
                        TextButton(
                            onClick = {
                                vm.updateWorkbenchFilters { current ->
                                    current.copy(channel = null, view = UiFilter.SHOW_SELECTED)
                                }
                                vm.setQuery("")
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) {
                            Text(stringResource(R.string.rules_view_unmatched))
                        }
                    }
                }
            }
            shownGroups.forEach { group ->
                item(key = "app|${group.packageName}", contentType = "app") {
                    AppRow(
                        group = group,
                        selected = activeSelected,
                        lockState = if (deepLinkScoped) vm.bulkLockState(
                            lockScope,
                            group.packageName,
                            lockRulesByPackage[group.packageName].orEmpty().map { it.id }
                        ) else vm.ruleBulkLockState(
                            filters.channel,
                            filters.openPreset,
                            group.packageName,
                            lockRulesByPackage[group.packageName].orEmpty()
                        ),
                        onOpen = { onDetail(group.packageName) },
                        onSelect = { selected ->
                            when {
                                typedOpenPreset != null ->
                                    vm.setOpenTypeGroupSelected(typedOpenPreset, group, selected)
                                deepLinkHost != null ->
                                    vm.setBrowserHostGroupSelected(deepLinkHost, group, selected)
                                else -> vm.setGroupSelected(group, selected)
                            }
                        },
                        onLock = {
                            if (deepLinkScoped) vm.setBulkAppLocked(
                                lockScope,
                                group.packageName,
                                lockRulesByPackage[group.packageName].orEmpty().map { it.id },
                                true
                            ) else vm.setRuleAppLocked(
                                filters.channel,
                                filters.openPreset,
                                group.packageName,
                                lockRulesByPackage[group.packageName].orEmpty(),
                                true
                            )
                        },
                        onUnlock = {
                            if (deepLinkScoped) vm.setBulkAppLocked(
                                lockScope,
                                group.packageName,
                                lockRulesByPackage[group.packageName].orEmpty().map { it.id },
                                false
                            ) else vm.setRuleAppLocked(
                                filters.channel,
                                filters.openPreset,
                                group.packageName,
                                lockRulesByPackage[group.packageName].orEmpty(),
                                false
                            )
                        }
                    )
                }
            }
            if (!state.loading && shownGroups.isEmpty()) {
                item(key = "rules-empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_matching_components))
                    }
                }
            }
        }
    }
}

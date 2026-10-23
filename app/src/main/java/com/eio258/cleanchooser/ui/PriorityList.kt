package com.eio258.cleanchooser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.PriorityListFilter
import com.eio258.cleanchooser.domain.matchesBrowserHost
import com.eio258.cleanchooser.domain.matchesOpenPreset
import com.eio258.cleanchooser.domain.priorityAppGroups
import com.eio258.cleanchooser.domain.priorityCandidates
import kotlin.math.roundToInt

/**
 * Workbench "priority" segment: the ranked app list with long-press drag ordering.
 * Filters live in [WorkbenchFilters]; per-app details moved to the detail page.
 */
@Composable
fun PriorityList(
    state: MainState,
    vm: MainViewModel,
    filters: WorkbenchFilters,
    onDetail: (String) -> Unit
) {
    var showCustomTypes by rememberSaveable { mutableStateOf(false) }
    var showBrowserHosts by rememberSaveable { mutableStateOf(false) }
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

    val kind = filters.channel ?: IntentKind.SHARE
    val viewFilter = filters.view
    val appTypeFilter = filters.appType
    val openPreset = filters.openPreset.takeIf { kind == IntentKind.OPEN }
    val browserHost = filters.browserHost.takeIf { kind == IntentKind.DEEP_LINK }
    val bulkLockRevision by vm.bulkLockRevision.collectAsState()

    LaunchedEffect(state.openTypesExplicit.customDefinitions, openPreset) {
        if (openPreset?.isCustom == true && openPreset !in state.openTypesExplicit.customDefinitions) {
            vm.updateWorkbenchFilters { it.copy(openPreset = null) }
        }
    }
    LaunchedEffect(state.browserAvailableHosts, browserHost) {
        if (browserHost != null && browserHost !in state.browserAvailableHosts) {
            vm.updateWorkbenchFilters { it.copy(browserHost = null) }
        }
    }

    val typedOpenPreset = openPreset.takeIf { kind == IntentKind.OPEN }
    val deepLinkHost = browserHost.takeIf { kind == IntentKind.DEEP_LINK }

    val typedSelected = typedOpenPreset?.let { state.openTypes.selectedRules(it) }.orEmpty()
    val explicitDeepLinkSelected = deepLinkHost?.let { state.browserLinks.selectedRules(it) }.orEmpty()
    val genericDeepLinkSelected = state.selected.filterTo(linkedSetOf()) { it.kind == IntentKind.DEEP_LINK }
    val deepLinkSelected = genericDeepLinkSelected + explicitDeepLinkSelected
    val deepLinkScoped = deepLinkHost != null
    val scopedCandidates = when {
        typedOpenPreset != null ->
            state.candidates.filter { it.matchesOpenPreset(typedOpenPreset, state.openTypesExplicit.customDefinitions) }
        deepLinkHost != null -> state.candidates.filter {
            it.rule.kind == IntentKind.DEEP_LINK &&
                (it.matchesBrowserHost(deepLinkHost) || it.rule in deepLinkSelected)
        }
        else -> state.candidates
    }
    val explicitTypedPriority = typedOpenPreset?.let { state.openTypesExplicit.priorities[it].orEmpty() }.orEmpty()
    val genericOpenPriority = state.priorities.apps[IntentKind.OPEN].orEmpty()
    val inheritsOpenPriority = typedOpenPreset != null && explicitTypedPriority.isEmpty() && genericOpenPriority.isNotEmpty()
    val hasExplicitOpenPriority = typedOpenPreset != null && explicitTypedPriority.isNotEmpty()
    val explicitDeepLinkPriority = deepLinkHost?.let { state.browserLinks.priorities[it].orEmpty() }.orEmpty()
    val genericDeepLinkPriority = state.priorities.apps[IntentKind.DEEP_LINK].orEmpty()
    val inheritsDeepLinkPriority = deepLinkScoped && explicitDeepLinkPriority.isEmpty() && genericDeepLinkPriority.isNotEmpty()
    val hasExplicitDeepLinkPriority = deepLinkHost != null && explicitDeepLinkPriority.isNotEmpty()
    val rankedRaw = when {
        typedOpenPreset != null -> state.openTypes.priorities[typedOpenPreset].orEmpty()
        deepLinkHost != null && explicitDeepLinkPriority.isNotEmpty() -> explicitDeepLinkPriority
        deepLinkHost != null -> genericDeepLinkPriority
        else -> state.priorities.apps[kind].orEmpty()
    }
    val lockScope = deepLinkHost?.let(::browserPriorityBulkLockScope) ?: priorityBulkLockScope(kind, openPreset)
    val scopedExtraSelected = if (deepLinkScoped) explicitDeepLinkSelected else typedSelected
    val baseGroups = remember(scopedCandidates, state.selected, scopedExtraSelected, state.displayMode, kind, openPreset, browserHost, rankedRaw, state.query, viewFilter, bulkLockRevision) {
        priorityAppGroups(
            scopedCandidates,
            state.selected,
            state.displayMode,
            kind,
            rankedRaw,
            state.query,
            when (viewFilter) {
                UiFilter.ALL, UiFilter.LOCKED -> PriorityListFilter.ALL
                UiFilter.HIDE_SELECTED -> PriorityListFilter.UNSELECTED
                UiFilter.SHOW_SELECTED -> PriorityListFilter.SELECTED
            },
            scopedExtraSelected
        )
    }
    val lockFilteredGroups = if (viewFilter == UiFilter.LOCKED) {
        bulkLockRevision
        baseGroups.filter { vm.bulkLockState(lockScope, it.packageName, emptyList()) != BulkLockState.NONE }
    } else {
        baseGroups
    }
    val groups = lockFilteredGroups.filter { appTypeFilter.matches(it.appType) }
    val moveTargets = groups.filter { it.rank != null }.sortedBy { it.rank }.map { it.packageName }
    val visibleSaved = priorityCandidates(scopedCandidates, state.selected, state.displayMode, kind, scopedExtraSelected)
        .map { it.rule.packageName }.toSet()
    val hiddenSavedCount = rankedRaw.count { it !in visibleSaved }

    val listState = rememberLazyListState()
    val dragKey = "${kind.name}|${openPreset?.name ?: browserHost ?: "ALL"}"
    val dragState = remember(listState) { PriorityDragState(listState) }
    val drag = dragState.session
    val currentSaved by rememberUpdatedState(rankedRaw)
    val currentVisible by rememberUpdatedState(moveTargets)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val speed = with(density) { 640.dp.toPx() }
    LaunchedEffect(kind, openPreset, browserHost, viewFilter, appTypeFilter, state.query, rankedRaw, moveTargets) { dragState.cancel() }
    DisposableEffect(dragState) { onDispose { dragState.cancel() } }
    LaunchedEffect(drag?.packageName) {
        if (dragState.session != null) {
            var previous = withFrameNanos { it }
            while (dragState.session != null) {
                val now = withFrameNanos { it }
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
                previous = now
                val delta = dragState.scrollSpeed(edge, speed) * seconds
                if (delta != 0f) {
                    listState.scrollBy(delta)
                    dragState.retarget()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().pointerInput(kind, openPreset, browserHost, viewFilter, appTypeFilter, state.query) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        if (dragState.start(position.y, dragKey, currentVisible, currentSaved)) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, amount ->
                        if (dragState.session != null) {
                            change.consume()
                            dragState.move(amount.y)
                        }
                    },
                    onDragCancel = { dragState.cancel() },
                    onDragEnd = {
                        dragState.finish()?.let { finished ->
                            when {
                                typedOpenPreset != null ->
                                    vm.moveOpenTypePriorityTo(typedOpenPreset, finished.packageName, finished.target, finished.visible, finished.saved)
                                deepLinkHost != null ->
                                    vm.moveBrowserHostPriorityTo(deepLinkHost, finished.packageName, finished.target, finished.visible, finished.saved)
                                else -> vm.movePriorityTo(kind, finished.packageName, finished.target, finished.visible, finished.saved)
                            }
                        }
                    }
                )
            },
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "controls") {
                ListHeader(
                    channels = IntentKind.entries,
                    selectedChannel = kind,
                    channelLabel = { stringResource(it.titleRes()) },
                    onChannel = { entry ->
                        vm.updateWorkbenchFilters {
                            it.copy(
                                channel = entry,
                                openPreset = if (entry == IntentKind.OPEN) it.openPreset else null,
                                browserHost = if (entry == IntentKind.DEEP_LINK) it.browserHost else null
                            )
                        }
                    },
                    view = viewFilter,
                    viewTitle = {
                        stringResource(
                            when (it) {
                                UiFilter.ALL -> R.string.common_all
                                UiFilter.HIDE_SELECTED -> R.string.priority_unselected
                                UiFilter.SHOW_SELECTED -> R.string.priority_selected
                                UiFilter.LOCKED -> R.string.filter_locked
                            }
                        )
                    },
                    onView = { value -> vm.updateWorkbenchFilters { it.copy(view = value) } },
                    appType = appTypeFilter,
                    onAppType = { value -> vm.updateWorkbenchFilters { it.copy(appType = value) } },
                    helpText = stringResource(R.string.priority_intro) + "\n\n" +
                        stringResource(R.string.priority_drag_help),
                    loading = state.loading,
                    extraFilter = {
                        when (kind) {
                            IntentKind.OPEN -> OpenPresetFilterMenu(
                                selected = openPreset,
                                config = state.openTypesExplicit,
                                onSelected = { preset -> vm.updateWorkbenchFilters { it.copy(openPreset = preset) } },
                                onManageCustom = { showCustomTypes = true }
                            )
                            IntentKind.DEEP_LINK -> BrowserHostFilterMenu(
                                selected = browserHost,
                                config = state.browserLinks,
                                availableHosts = state.browserAvailableHosts,
                                onSelected = { host -> vm.updateWorkbenchFilters { it.copy(browserHost = host) } },
                                onManage = { showBrowserHosts = true }
                            )
                            else -> Unit
                        }
                    },
                    onSelectAll = {
                        when {
                            typedOpenPreset != null ->
                                vm.selectOpenTypePriorityApps(typedOpenPreset, groups.map { it.packageName }, lockScope)
                            deepLinkHost != null ->
                                vm.selectBrowserHostPriorityApps(deepLinkHost, groups.map { it.packageName }, lockScope)
                            else -> vm.selectPriorityApps(kind, groups.map { it.packageName }, lockScope)
                        }
                    },
                    onSelectNone = {
                        when {
                            typedOpenPreset != null ->
                                vm.deselectOpenTypePriorityApps(typedOpenPreset, groups.map { it.packageName }, lockScope)
                            deepLinkHost != null ->
                                vm.deselectBrowserHostPriorityApps(deepLinkHost, groups.map { it.packageName }, lockScope)
                            else -> vm.deselectPriorityApps(kind, groups.map { it.packageName }, lockScope)
                        }
                    },
                    onInvert = {
                        when {
                            typedOpenPreset != null ->
                                vm.invertOpenTypePriorityApps(typedOpenPreset, groups.map { it.packageName }, lockScope)
                            deepLinkHost != null ->
                                vm.invertBrowserHostPriorityApps(deepLinkHost, groups.map { it.packageName }, lockScope)
                            else -> vm.invertPriorityApps(kind, groups.map { it.packageName }, lockScope)
                        }
                    }
                )
            }

            item(key = "summary") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    val presetTitle = when {
                        typedOpenPreset != null -> state.openTypes.localizedTitle(typedOpenPreset)
                        deepLinkHost != null -> deepLinkHost
                        else -> null
                    }
                    Text(
                        if (presetTitle == null) stringResource(R.string.app_list_count, groups.size)
                        else stringResource(R.string.app_list_count_type, groups.size, presetTitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (typedOpenPreset != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    inheritsOpenPriority -> stringResource(R.string.priority_source_inherited)
                                    hasExplicitOpenPriority -> stringResource(R.string.priority_source_dedicated, presetTitle.orEmpty())
                                    else -> stringResource(R.string.priority_source_none)
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (inheritsOpenPriority) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasExplicitOpenPriority) {
                                TextButton(onClick = { vm.resetOpenTypePriority(typedOpenPreset) }) {
                                    Text(stringResource(R.string.priority_restore_inheritance))
                                }
                            }
                        }
                    } else if (deepLinkHost != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    inheritsDeepLinkPriority -> stringResource(R.string.priority_browser_source_inherited)
                                    hasExplicitDeepLinkPriority -> stringResource(R.string.priority_source_dedicated, deepLinkHost)
                                    else -> stringResource(R.string.priority_source_none)
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (inheritsDeepLinkPriority) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasExplicitDeepLinkPriority) {
                                TextButton(onClick = { vm.resetBrowserHostPriority(deepLinkHost) }) {
                                    Text(stringResource(R.string.priority_restore_inheritance))
                                }
                            }
                        }
                    }
                    if (hiddenSavedCount > 0) {
                        Text(stringResource(R.string.priority_hidden_saved, hiddenSavedCount), style = MaterialTheme.typography.bodySmall)
                    }
                    if (rankedRaw.size >= 200) {
                        Text(
                            stringResource(R.string.priority_limit_reached),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state.error != null) {
                        Text(stringResource(R.string.rules_refresh_incomplete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            groups.forEach { group ->
                val packageName = group.packageName
                val first = group.components.first()
                item(key = "app|$dragKey|$packageName", contentType = "app") {
                    val marker = MaterialTheme.colorScheme.primary
                    val rankText = group.rank?.let {
                        stringResource(
                            if (inheritsOpenPriority || inheritsDeepLinkPriority)
                                R.string.priority_rank_inherited else R.string.priority_rank,
                            it
                        )
                    } ?: stringResource(R.string.priority_not_prioritized)
                    AppEntryRow(
                        title = first.appLabel,
                        subtitle = stringResource(R.string.priority_app_summary, rankText, group.components.size),
                        icon = first.appIcon,
                        rank = group.rank,
                        checkState = if (group.rank != null) androidx.compose.ui.state.ToggleableState.On
                        else androidx.compose.ui.state.ToggleableState.Off,
                        onCheck = {
                            val checked = group.rank == null
                            when {
                                typedOpenPreset != null -> {
                                    if (checked) vm.pinOpenTypeApp(typedOpenPreset, packageName)
                                    else vm.removeOpenTypePriority(typedOpenPreset, packageName)
                                }
                                deepLinkHost != null -> {
                                    if (checked) vm.pinBrowserHostApp(deepLinkHost, packageName)
                                    else vm.removeBrowserHostPriority(deepLinkHost, packageName)
                                }
                                checked -> vm.pinApp(kind, packageName)
                                else -> vm.removePriority(kind, packageName)
                            }
                        },
                        lockState = vm.bulkLockState(lockScope, packageName, emptyList()),
                        onLock = { vm.setBulkAppLocked(lockScope, packageName, emptyList(), true) },
                        onUnlock = { vm.setBulkAppLocked(lockScope, packageName, emptyList(), false) },
                        onOpen = { onDetail(packageName) },
                        modifier = Modifier
                            .alpha(if (drag?.packageName == packageName) 0.3f else 1f)
                            .drawWithContent {
                                drawContent()
                                if (drag?.target == packageName && drag.packageName != packageName) {
                                    val y = if (drag.movingDown) size.height - 2.dp.toPx() else 2.dp.toPx()
                                    drawLine(
                                        marker,
                                        Offset(0f, y),
                                        Offset(size.width, y),
                                        3.dp.toPx()
                                    )
                                }
                            }
                    )
                }
            }

            if (!state.loading && groups.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(
                                when {
                                    state.query.isNotBlank() -> R.string.priority_empty_search
                                    viewFilter == UiFilter.SHOW_SELECTED -> R.string.priority_empty_selected
                                    viewFilter == UiFilter.HIDE_SELECTED -> R.string.priority_empty_unselected
                                    viewFilter == UiFilter.LOCKED -> R.string.no_matching_components
                                    else -> R.string.priority_empty_category
                                }
                            )
                        )
                    }
                }
            }
        }

        drag?.let { moving ->
            groups.firstOrNull { it.packageName == moving.packageName }?.let { group ->
                val first = group.components.first()
                Surface(
                    Modifier.fillMaxWidth().offset { IntOffset(0, moving.top.roundToInt()) }.zIndex(1f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(first.appIcon, first.appLabel)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(first.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(
                                    R.string.priority_drag_target,
                                    groups.firstOrNull { it.packageName == moving.target }?.rank?.toString() ?: "?"
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

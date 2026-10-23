package com.eio258.cleanchooser.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.data.CleanupKind
import com.eio258.cleanchooser.data.RootComponent

@StringRes
private fun componentKindHelpRes(kind: CleanupKind?): Int = when (kind) {
    null -> R.string.root_kind_all_help
    CleanupKind.LAUNCHER -> R.string.root_kind_launcher_help
    CleanupKind.TILE -> R.string.root_kind_tile_help
    CleanupKind.SHORTCUT -> R.string.root_kind_shortcut_help
    CleanupKind.WIDGET -> R.string.root_kind_widget_help
}

private fun componentAppSelectionRank(items: List<RootComponent>): Int {
    val editable = items.filter { it.blocked == null && it.enabled != null }
    val disabledCount = editable.count { it.enabled == false }
    return when {
        editable.isNotEmpty() && disabledCount == editable.size -> 0
        disabledCount > 0 -> 1
        else -> 2
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentsList(
    state: MainState,
    vm: MainViewModel,
    filters: WorkbenchFilters,
    onDetail: (String) -> Unit
) {
    val scan by vm.componentScan.collectAsStateWithLifecycle()
    val busy by vm.componentBusy.collectAsStateWithLifecycle()
    val message by vm.componentMessage.collectAsStateWithLifecycle()
    val rootNotice by vm.componentRootNotice.collectAsStateWithLifecycle()
    val bulkLockRevision by vm.bulkLockRevision.collectAsStateWithLifecycle()

    rootNotice?.let { notice ->
        ModalBottomSheet(
            onDismissRequest = vm::dismissComponentRootNotice,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(
                    stringResource(R.string.root_permission_required),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = vm::dismissComponentRootNotice) {
                        Text(stringResource(R.string.root_permission_ack))
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { vm.refreshComponents() }

    val kind = filters.componentKind
    val viewFilter = filters.componentView
    val appTypeFilter = filters.appType

    val kindItems = scan.items.filter { kind == null || it.kind == kind }
    val lockItemsByApp = remember(kindItems, bulkLockRevision) {
        kindItems.groupBy(::componentBulkLockAppId)
    }
    val scopeItems = kindItems.filter { appTypeFilter.matches(it.appType) }
    val baseVisible = scopeItems.filter {
        (when (viewFilter) {
            UiFilter.ALL, UiFilter.LOCKED -> true
            UiFilter.SHOW_SELECTED -> it.enabled == false
            UiFilter.HIDE_SELECTED -> it.enabled == true
        }) &&
            (state.query.isBlank() ||
                listOf(it.label, it.owner, it.component.flattenToString())
                    .any { text -> text.contains(state.query, true) })
    }
    val baseGroups = baseVisible.groupBy { "${it.user}|${it.component.packageName}" }.entries
        .sortedWith(
            compareBy<Map.Entry<String, List<RootComponent>>> { componentAppSelectionRank(it.value) }
                .thenBy { it.value.first().owner.lowercase() }
                .thenBy { it.key }
        )
    val groups = if (viewFilter == UiFilter.LOCKED) {
        bulkLockRevision
        baseGroups.filter { (appKey, _) ->
            vm.componentBulkLockState(kind, appKey, lockItemsByApp[appKey].orEmpty()) != BulkLockState.NONE
        }
    } else {
        baseGroups
    }
    val visible = groups.flatMap { it.value }

    val helpText = stringResource(R.string.root_selection_semantics) + "\n\n" +
        stringResource(R.string.root_change_semantics) + "\n\n" +
        stringResource(componentKindHelpRes(kind))

    Column(Modifier.fillMaxSize()) {
        ListHeader(
            channels = listOf<CleanupKind?>(null) + CleanupKind.entries,
            selectedChannel = kind,
            channelLabel = { entry ->
                entry?.let { stringResource(it.titleRes()) } ?: stringResource(R.string.common_all)
            },
            onChannel = { entry -> vm.updateWorkbenchFilters { it.copy(componentKind = entry) } },
            view = viewFilter,
            viewTitle = { stringResource(it.titleRes()) },
            onView = { entry -> vm.updateWorkbenchFilters { it.copy(componentView = entry) } },
            appType = appTypeFilter,
            onAppType = { entry -> vm.updateWorkbenchFilters { it.copy(appType = entry) } },
            viewOptions = UiFilter.entries,
            helpText = helpText,
            loading = busy,
            onSelectAll = { vm.changeComponentsBulk(kind, visible, enable = false) },
            onInvert = { vm.invertComponentsBulk(kind, visible) }
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "status") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        stringResource(R.string.root_summary, groups.size, visible.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (scan.warning.isNotBlank()) {
                        Text(
                            scan.warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            groups.forEach { (appKey, unsorted) ->
                val components = unsorted.sortedWith(
                    compareBy({ it.kind.ordinal }, { it.label.lowercase() }, { it.id })
                )
                val editableComponents = components.filter { it.blocked == null && it.enabled != null }
                val disabledCount = editableComponents.count { it.enabled == false }
                val selectionState = when {
                    disabledCount == 0 -> ToggleableState.Off
                    disabledCount == editableComponents.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }
                val first = components.first()
                val lockItems = lockItemsByApp[appKey].orEmpty()

                item(key = "app|$appKey") {
                    val kindTitles = CleanupKind.entries
                        .filter { entry -> components.any { it.kind == entry } }
                        .map { stringResource(it.titleRes()) }
                    val subtitle = buildString {
                        append(
                            stringResource(
                                R.string.root_disabled_summary,
                                components.count { it.enabled == false },
                                components.size,
                                kindTitles.joinToString(" · ")
                            )
                        )
                        if (editableComponents.size < components.size) {
                            append(" · ")
                            append(
                                stringResource(
                                    R.string.root_display_only_count,
                                    components.size - editableComponents.size
                                )
                            )
                        }
                    }
                    AppEntryRow(
                        title = first.owner,
                        subtitle = subtitle,
                        icon = first.icon,
                        checkState = selectionState,
                        onCheck = {
                            if (!busy && editableComponents.isNotEmpty()) {
                                vm.changeComponents(
                                    editableComponents,
                                    selectionState == ToggleableState.On
                                )
                            }
                        },
                        lockState = vm.componentBulkLockState(kind, appKey, lockItems),
                        onLock = { vm.setComponentAppLocked(kind, appKey, lockItems, true) },
                        onUnlock = { vm.setComponentAppLocked(kind, appKey, lockItems, false) },
                        onOpen = { onDetail(first.component.packageName) }
                    )
                }
            }

            if (visible.isEmpty() && !busy) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.root_no_components),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }
}

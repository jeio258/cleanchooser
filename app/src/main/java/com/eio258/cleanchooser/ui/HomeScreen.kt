package com.eio258.cleanchooser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eio258.cleanchooser.BuildConfig
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.data.CleanupKind
import com.eio258.cleanchooser.data.PersistentComponentState
import com.eio258.cleanchooser.data.RootComponent
import com.eio258.cleanchooser.data.RootComponentScan
import com.eio258.cleanchooser.domain.IntentKind

/**
 * Home follows the approved mockup frame: health strip, three task cards, entry rows.
 * A card expands in place instead of leaving the page, so the collapsed first frame stays
 * identical to the reference while the rows remain operable.
 */
@Composable
fun HomeScreen(
    state: MainState,
    vm: MainViewModel,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    onCollectDiagnostics: () -> Unit,
    collectingDiagnostics: Boolean,
) {
    val scan by vm.componentScan.collectAsStateWithLifecycle()
    val disabledKeys by vm.disabledComponentKeys.collectAsStateWithLifecycle()
    val visibilityScopes by vm.visibilityScopes.collectAsStateWithLifecycle()
    val textMenuOrder by vm.textMenuOrder.collectAsStateWithLifecycle()
    val channel = IntentKind.SHARE
    val ranked = state.priorities.apps[channel].orEmpty()
    val preview = remember(state.groups, state.selected) {
        state.groups.sortedWith(
            compareBy(
                { group -> group.components.count { it.rule in state.selected } },
                { it.appLabel.lowercase() }
            )
        ).take(3)
    }
    val hiddenApps = state.selected.map { it.packageName }.distinct().size
    val disabled = scan.items.filter { it.enabled == false && it.blocked == null }
    val kindCounts = remember(disabled) {
        CleanupKind.entries.associateWith { kind -> disabled.count { it.kind == kind } }
    }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var resetSheetOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (scan.items.isEmpty()) vm.refreshComponents() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HealthRow(state, onOpenLogs = vm::openLogs)

        SectionLabel(stringResource(R.string.home_section_cleanup))

        TaskCard(
            icon = Icons.Rounded.VisibilityOff,
            title = stringResource(R.string.home_rules_title),
            summary = stringResource(R.string.home_rules_summary, state.groups.size, hiddenApps),
            expanded = expanded == "rules",
            onHeaderClick = { expanded = if (expanded == "rules") null else "rules" },
            onOpenAll = { vm.openWorkbench(WorkbenchTab.RULES) },
            openAllLabel = stringResource(R.string.home_view_all_count, state.groups.size)
        ) {
            if (preview.isEmpty()) {
                EmptyHint(stringResource(R.string.home_loading))
            } else {
                preview.forEachIndexed { index, group ->
                    if (index > 0) CardDivider()
                    val total = group.components.size
                    val picked = group.components.count { it.rule in state.selected }
                    val kinds = group.components.map { it.rule.kind }.distinct()
                        .map { stringResource(it.titleRes()) }
                        .joinToString(" · ")
                    AppEntryRow(
                        title = group.appLabel,
                        subtitle = stringResource(R.string.home_row_selection, picked, total, kinds),
                        icon = group.appIcon,
                        checkState = when {
                            picked == 0 -> androidx.compose.ui.state.ToggleableState.Off
                            picked == total -> androidx.compose.ui.state.ToggleableState.On
                            else -> androidx.compose.ui.state.ToggleableState.Indeterminate
                        },
                        onCheck = { vm.setGroupSelected(group, picked != total) },
                        onOpen = { vm.openDetail(group.packageName) },
                        onLock = {
                            vm.setRuleAppLocked(channel, null, group.packageName, group.components.map { it.rule }, true)
                        },
                        onUnlock = {
                            vm.setRuleAppLocked(channel, null, group.packageName, group.components.map { it.rule }, false)
                        },
                        lockState = vm.ruleBulkLockState(channel, null, group.packageName, group.components.map { it.rule })
                    )
                }
            }
        }

        TaskCard(
            icon = Icons.AutoMirrored.Rounded.Sort,
            title = stringResource(R.string.home_priority_title),
            summary = stringResource(
                R.string.home_priority_summary,
                state.priorities.apps.values.flatten().distinct().size
            ),
            expanded = expanded == "priority",
            onHeaderClick = { expanded = if (expanded == "priority") null else "priority" },
            onOpenAll = { vm.openWorkbench(WorkbenchTab.PRIORITY) },
            openAllLabel = stringResource(R.string.home_adjust)
        ) {
            if (ranked.isEmpty()) {
                EmptyHint(stringResource(R.string.home_no_ranked))
            } else {
                ranked.take(3).forEachIndexed { index, packageName ->
                    if (index > 0) CardDivider()
                    val group = state.groups.firstOrNull { it.packageName == packageName }
                    AppEntryRow(
                        title = group?.appLabel ?: packageName,
                        subtitle = stringResource(R.string.home_rank_position, index + 1),
                        icon = group?.appIcon,
                        rank = index + 1,
                        onOpen = { vm.openDetail(packageName) },
                        trailing = {
                            IconButton(
                                onClick = { vm.movePriority(channel, packageName, -1, ranked) },
                                enabled = index > 0
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowUpward,
                                    stringResource(R.string.priority_move_up),
                                    Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { vm.movePriority(channel, packageName, 1, ranked) },
                                enabled = index < ranked.size - 1
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowDownward,
                                    stringResource(R.string.priority_move_down),
                                    Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }
            }
        }

        TaskCard(
            icon = Icons.Rounded.Widgets,
            title = stringResource(R.string.home_components_title),
            summary = when {
                scan.items.isEmpty() -> stringResource(R.string.home_components_unscanned)
                disabled.isEmpty() -> stringResource(R.string.home_components_summary_none)
                (kindCounts[CleanupKind.LAUNCHER] ?: 0) > 0 -> stringResource(
                    R.string.home_components_summary_kinds_launcher,
                    kindCounts[CleanupKind.TILE] ?: 0,
                    kindCounts[CleanupKind.SHORTCUT] ?: 0,
                    kindCounts[CleanupKind.WIDGET] ?: 0,
                    kindCounts[CleanupKind.LAUNCHER] ?: 0
                )
                else -> stringResource(
                    R.string.home_components_summary_kinds,
                    kindCounts[CleanupKind.TILE] ?: 0,
                    kindCounts[CleanupKind.SHORTCUT] ?: 0,
                    kindCounts[CleanupKind.WIDGET] ?: 0
                )
            },
            expanded = expanded == "components",
            onHeaderClick = { expanded = if (expanded == "components") null else "components" },
            onOpenAll = { vm.openWorkbench(WorkbenchTab.COMPONENTS) },
            openAllLabel = stringResource(R.string.home_manage)
        ) {
            when {
                scan.items.isEmpty() -> EmptyHint(stringResource(R.string.home_not_scanned))
                disabled.isEmpty() -> EmptyHint(stringResource(R.string.home_no_disabled))
                else -> disabled.take(3).forEachIndexed { index, item ->
                    if (index > 0) CardDivider()
                    AppEntryRow(
                        title = item.label.takeIf { it.any(Char::isLetterOrDigit) }
                            ?: item.component.className.substringAfterLast('.'),
                        subtitle = stringResource(
                            R.string.home_component_row,
                            stringResource(item.kind.titleRes()),
                            item.owner
                        ),
                        icon = item.icon,
                        onOpen = {
                            vm.selectWorkbenchTab(WorkbenchTab.COMPONENTS)
                            vm.openDetail(item.component.packageName)
                        },
                        trailing = {
                            Switch(checked = true, onCheckedChange = { vm.changeComponent(item, true) })
                        }
                    )
                }
            }
        }

        SectionLabel(stringResource(R.string.home_section_entries))
        AppCard {
            Column {
                HomeActionRow(
                    stringResource(R.string.dashboard_restore_json),
                    Icons.Rounded.FileDownload,
                    onRestore
                )
                CardDivider()
                HomeActionRow(
                    stringResource(R.string.dashboard_export_json),
                    Icons.Rounded.FileUpload,
                    onExport
                )
                CardDivider()
                HomeActionRow(
                    stringResource(R.string.logs_title),
                    Icons.Rounded.BugReport,
                    vm::openLogs
                )
                CardDivider()
                HomeActionRow(
                    stringResource(R.string.home_reset_default),
                    Icons.Rounded.RestartAlt,
                    { resetSheetOpen = true }
                )
                CardDivider()
                HomeActionRow(
                    stringResource(R.string.home_diagnostic_short),
                    Icons.Rounded.BugReport,
                    onCollectDiagnostics,
                    enabled = !collectingDiagnostics
                )
            }
        }

        Text(
            stringResource(R.string.app_version_format, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    if (resetSheetOpen) {
        ResetDefaultsSheet(
            state = state,
            disabledKeys = disabledKeys,
            scan = scan,
            visibilityScopeCount = visibilityScopes.size,
            textMenuCount = textMenuOrder.size,
            onDismiss = { resetSheetOpen = false },
            onRestoreRules = vm::resetRules,
            onRestorePriority = vm::resetPriorities,
            onRestoreOpenTypes = vm::resetOpenTypes,
            onRestoreBrowserHosts = vm::resetBrowserHosts,
            onRestoreTextMenu = vm::resetTextMenuOrder,
            onRestoreVisibility = vm::resetVisibility,
            onRestoreKind = vm::restoreComponentsByKind,
            onRestoreAll = {
                resetSheetOpen = false
                vm.resetMenuConfig()
                vm.restoreAllComponents()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResetDefaultsSheet(
    state: MainState,
    disabledKeys: Set<String>,
    scan: RootComponentScan,
    visibilityScopeCount: Int,
    textMenuCount: Int,
    onDismiss: () -> Unit,
    onRestoreRules: () -> Unit,
    onRestorePriority: () -> Unit,
    onRestoreOpenTypes: () -> Unit,
    onRestoreBrowserHosts: () -> Unit,
    onRestoreTextMenu: () -> Unit,
    onRestoreVisibility: () -> Unit,
    onRestoreKind: (CleanupKind) -> Unit,
    onRestoreAll: () -> Unit,
) {
    // Components this app disabled are the only root-managed entries a restore may touch.
    val restorable = remember(scan.items, disabledKeys) {
        scan.items.filter {
            it.enabled == false && it.blocked == null &&
                PersistentComponentState.key(it.user, it.component) in disabledKeys
        }
    }
    val kindCounts = remember(restorable) {
        CleanupKind.entries.associateWith { kind -> restorable.count { it.kind == kind } }
    }
    val sections = listOf(
        ResetRow(
            stringResource(R.string.reset_section_item_count, stringResource(R.string.reset_section_rules), state.selected.size),
            state.selected.size, onRestoreRules
        ),
        ResetRow(
            stringResource(R.string.reset_section_item_count, stringResource(R.string.reset_section_priority), state.priorities.apps.values.flatten().distinct().size),
            state.priorities.apps.values.flatten().distinct().size, onRestorePriority
        ),
        ResetRow(
            stringResource(R.string.reset_section_item_count, stringResource(R.string.reset_section_open_types), state.openTypes.rules.values.sumOf { it.size }),
            state.openTypes.rules.values.sumOf { it.size }, onRestoreOpenTypes
        ),
        ResetRow(
            stringResource(R.string.reset_section_item_count, stringResource(R.string.reset_section_browser_hosts), state.browserLinks.rules.values.sumOf { it.size }),
            state.browserLinks.rules.values.sumOf { it.size }, onRestoreBrowserHosts
        ),
        ResetRow(
            stringResource(R.string.reset_section_item_count, stringResource(R.string.reset_section_text_menu), textMenuCount),
            textMenuCount, onRestoreTextMenu
        ),
        ResetRow(
            stringResource(R.string.reset_section_item_count, stringResource(R.string.reset_section_visibility), state.hiddenFromApps.size + visibilityScopeCount),
            state.hiddenFromApps.size + visibilityScopeCount, onRestoreVisibility
        )
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                stringResource(R.string.reset_default_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.reset_default_pick),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            sections.forEach { section -> ResetSectionRow(section) }
            SectionLabel(stringResource(R.string.reset_section_components))
            Text(
                stringResource(R.string.reset_default_components_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val componentKinds = CleanupKind.entries.filter { (kindCounts[it] ?: 0) > 0 }
            if (componentKinds.isEmpty()) {
                Text(
                    stringResource(R.string.reset_default_components_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                componentKinds.forEach { kind ->
                    ResetSectionRow(
                        ResetRow(
                            stringResource(
                                R.string.reset_kind_count,
                                stringResource(kind.titleRes()),
                                kindCounts[kind] ?: 0
                            ),
                            kindCounts[kind] ?: 0
                        ) { onRestoreKind(kind) }
                    )
                }
            }
            Text(
                stringResource(R.string.reset_default_keep_diagnostic),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Button(
                    onClick = onRestoreAll,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.reset_default_all)) }
            }
        }
    }
}

private data class ResetRow(val label: String, val count: Int, val onRestore: () -> Unit)

@Composable
private fun ResetSectionRow(section: ResetRow) {
    var confirming by remember { mutableStateOf(false) }
    val label = section.label
    val onRestore = section.onRestore
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (confirming) {
            TextButton(onClick = { confirming = false }) {
                Text(stringResource(R.string.common_cancel))
            }
            Button(
                onClick = {
                    confirming = false
                    onRestore()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) { Text(stringResource(R.string.reset_default_confirm_yes)) }
        } else {
            TextButton(onClick = { confirming = true }, enabled = section.count > 0) {
                Text(stringResource(R.string.reset_default_restore_one))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TaskCard(
    icon: ImageVector,
    title: String,
    summary: String,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    onOpenAll: () -> Unit,
    openAllLabel: String,
    content: @Composable () -> Unit,
) {
    AppCard {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onHeaderClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(34.dp).background(
                        MaterialTheme.colorScheme.primaryContainer,
                        AppShape.tile
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).rotate(if (expanded) 90f else 0f),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    CardDivider()
                    content()
                    TextButton(
                        onClick = onOpenAll,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(openAllLabel)
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (enabled) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun HealthRow(state: MainState, onOpenLogs: () -> Unit) {
    val status = state.module
    val titleRes = when {
        !status.connected -> R.string.module_lsposed_disconnected
        status.outdated -> R.string.module_old_running
        status.error != null || (status.scopeKnown && status.missingScope.isNotEmpty()) ->
            R.string.module_needs_attention
        status.resolverLoaded -> R.string.module_loaded
        else -> R.string.module_lsposed_connected
    }
    val healthy = status.connected && !status.outdated && status.error == null &&
        (!status.scopeKnown || status.missingScope.isEmpty())
    val hits = state.runtime.queryHits + state.runtime.visibilityHits + state.runtime.orderingHits

    AppCard(Modifier.padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpenLogs)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(9.dp).background(
                    if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    CircleShape
                )
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.home_health_hits, hits),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

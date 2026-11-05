package com.eio258.cleanchooser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eio258.cleanchooser.BuildConfig
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.domain.IntentKind

/**
 * Third page: only actionable status, four compact blocks. The long runtime sentence is
 * shown once, inside the runtime block, instead of being repeated in the sync block.
 */
@Composable
fun StatusScreen(
    state: MainState,
    vm: MainViewModel,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    collectingDiagnostics: Boolean,
    onCollectDiagnostics: () -> Unit,
    onInspectFile: () -> Unit
) {
    val fileCheckStatus by vm.fileCheckStatus.collectAsState()
    val checkingFile by vm.checkingFile.collectAsState()
    var modeMenu by remember { mutableStateOf(false) }
    var showScope by remember { mutableStateOf(false) }
    var showAppScope by remember { mutableStateOf(false) }

    if (showScope) {
        ScopeDialog(state.module, vm::refreshModuleStatus) { showScope = false }
    }
    if (showAppScope) {
        AppScopePickerDialog(
            selected = state.hiddenFromApps,
            onSelectedChange = vm::setHiddenFromApps
        ) { showAppScope = false }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        StatusCard(stringResource(R.string.dashboard_global_mode)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(state.displayMode.titleRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.dashboard_global_mode_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    TextButton(onClick = { modeMenu = true }) {
                        Text(stringResource(R.string.dashboard_switch))
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.width(16.dp))
                    }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        com.eio258.cleanchooser.domain.DisplayMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(stringResource(mode.titleRes())) },
                                leadingIcon = {
                                    if (mode == state.displayMode) Icon(Icons.Rounded.Check, null)
                                },
                                onClick = { modeMenu = false; vm.setDisplayMode(mode) }
                            )
                        }
                    }
                }
            }
        }

        StatusCard(stringResource(R.string.capability_title)) {
            runtimeCapabilities(state.module, state.runtime).forEach { capability ->
                val name = stringResource(
                    when (capability.capability) {
                        RuntimeCapability.FILTERING -> R.string.capability_filtering
                        RuntimeCapability.ORDERING -> R.string.capability_ordering
                        RuntimeCapability.PACKAGE_VISIBILITY -> R.string.capability_visibility
                    }
                )
                val status = when (capability.state) {
                    CapabilityState.OBSERVED -> stringResource(R.string.capability_observed, capability.hits)
                    CapabilityState.LOADED_UNOBSERVED -> stringResource(R.string.capability_loaded_unobserved)
                    CapabilityState.MISSING_SCOPE -> stringResource(R.string.capability_missing_scope)
                    CapabilityState.OUTDATED -> stringResource(R.string.capability_outdated)
                    CapabilityState.DISCONNECTED -> stringResource(R.string.capability_disconnected)
                    CapabilityState.NOT_READY -> stringResource(R.string.capability_not_ready)
                }
                KeyValueRow(
                    label = name,
                    value = status,
                    valueColor = when (capability.state) {
                        CapabilityState.OBSERVED -> MaterialTheme.colorScheme.primary
                        CapabilityState.MISSING_SCOPE,
                        CapabilityState.DISCONNECTED,
                        CapabilityState.OUTDATED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (!state.runtime.ready) {
                Text(
                    state.runtime.message,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ActionRow {
                TextButton(onClick = { showScope = true; vm.refreshModuleStatus() }) {
                    Text(stringResource(R.string.scope_title))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = vm::refreshModuleStatus) {
                    Text(stringResource(R.string.scope_recheck))
                }
            }
        }

        StatusCard(stringResource(R.string.dashboard_sync_status)) {
            if (state.syncStatus.isNotBlank() && state.syncStatus != state.runtime.message) {
                Text(
                    state.syncStatus,
                    modifier = Modifier.padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.module.scopeKnown) {
                KeyValueRow(
                    stringResource(R.string.scope_granted),
                    state.module.grantedScope.size.toString()
                )
            }
            KeyValueRow(
                stringResource(R.string.dashboard_app_visibility),
                state.hiddenFromApps.size.toString()
            )
            ActionRow {
                TextButton(onClick = { showAppScope = true }) {
                    Text(stringResource(R.string.dashboard_manage_app_visibility, state.hiddenFromApps.size))
                }
            }
        }

        StatusCard(stringResource(R.string.dashboard_data_backup)) {
            ActionRow {
                Button(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_restore_json))
                }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_export_json))
                }
            }
        }

        StatusCard(stringResource(R.string.dashboard_diagnostics)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.diagnostic_mode), Modifier.weight(1f))
                Switch(state.diagnosticMode, vm::setDiagnosticMode)
            }
            state.error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            fileCheckStatus?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            ActionRow {
                OutlinedButton(
                    onClick = onInspectFile,
                    enabled = !checkingFile,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(
                            if (checkingFile) R.string.dashboard_inspecting_file
                            else R.string.dashboard_inspect_file
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = onCollectDiagnostics,
                    enabled = !collectingDiagnostics,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(R.string.dashboard_export_diagnostics),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
}

@Composable
private fun StatusCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            style = MaterialTheme.typography.titleSmall
        )
        AppCard {
            Column(Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}

@Composable
private fun KeyValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Overview page (the upgraded status page): a permanent pill row on top, one compact
 * row per workbench surface, the space list, and a three-row maintenance section.
 * Independently renderable; the shell screens are handled by the integrator.
 */
@Composable
fun OverviewScreen(
    state: MainState,
    vm: MainViewModel,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    collectingDiagnostics: Boolean,
    onCollectDiagnostics: () -> Unit,
    onInspectFile: () -> Unit
) {
    val scan by vm.componentScan.collectAsStateWithLifecycle()
    val disabledKeys by vm.disabledComponentKeys.collectAsStateWithLifecycle()
    val visibilityScopes by vm.visibilityScopes.collectAsStateWithLifecycle()
    val textMenuOrder by vm.textMenuOrder.collectAsStateWithLifecycle()
    val spaces by vm.spaceSelectorItems.collectAsStateWithLifecycle()
    val activeSpace by vm.activeSpaceId.collectAsStateWithLifecycle()
    val fileCheckStatus by vm.fileCheckStatus.collectAsState()
    val checkingFile by vm.checkingFile.collectAsState()
    var resetSheetOpen by rememberSaveable { mutableStateOf(false) }

    val caps = remember(state.module, state.runtime) {
        runtimeCapabilities(state.module, state.runtime).associateBy { it.capability }
    }
    fun capabilityLevel(capability: RuntimeCapability?): OverviewFaceLevel = when (caps[capability]?.state) {
        CapabilityState.OBSERVED -> OverviewFaceLevel.OK
        CapabilityState.LOADED_UNOBSERVED -> OverviewFaceLevel.UNOBSERVED
        null -> OverviewFaceLevel.OK
        else -> OverviewFaceLevel.DEGRADED
    }

    val disabledComponents = scan.items.count { it.enabled == false && it.blocked == null }
    val faces = listOf(
        OverviewFace(
            WorkbenchTab.RULES,
            Icons.Rounded.Share,
            state.selected.size,
            caps[RuntimeCapability.FILTERING]?.hits,
            capabilityLevel(RuntimeCapability.FILTERING)
        ),
        OverviewFace(
            WorkbenchTab.PRIORITY,
            Icons.AutoMirrored.Rounded.Sort,
            state.priorities.apps.values.flatten().distinct().size,
            caps[RuntimeCapability.ORDERING]?.hits,
            capabilityLevel(RuntimeCapability.ORDERING)
        ),
        OverviewFace(
            WorkbenchTab.COMPONENTS,
            Icons.Rounded.Widgets,
            disabledComponents,
            null,
            if (scan.items.isEmpty()) OverviewFaceLevel.UNOBSERVED else OverviewFaceLevel.OK
        ),
        OverviewFace(
            WorkbenchTab.TEXT,
            Icons.Rounded.Keyboard,
            state.candidates.count { it.rule.kind == IntentKind.PROCESS_TEXT && it.rule in state.selected },
            null,
            if (state.runtime.textMenuObserved) OverviewFaceLevel.OK else OverviewFaceLevel.UNOBSERVED
        ),
        OverviewFace(
            WorkbenchTab.DIRECT_SHARE,
            Icons.Rounded.Link,
            state.candidates.count { it.rule.kind == IntentKind.DIRECT_SHARE && it.rule in state.selected },
            null,
            if (state.runtime.ready) OverviewFaceLevel.OK else OverviewFaceLevel.DEGRADED
        ),
        OverviewFace(
            WorkbenchTab.SYSTEM_ENTRY,
            Icons.Rounded.Settings,
            state.candidates.count { it.rule.kind in SYSTEM_ENTRY_KINDS && it.rule in state.selected },
            caps[RuntimeCapability.PACKAGE_VISIBILITY]?.hits,
            capabilityLevel(RuntimeCapability.PACKAGE_VISIBILITY)
        )
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OverviewModePill(state.displayMode, vm::setDisplayMode)
            Spacer(Modifier.weight(1f))
            SpaceSelectorBar(
                spaces = spaces,
                selectedUserId = activeSpace,
                onSelect = vm::switchSpace
            )
        }

        OverviewSectionLabel(stringResource(R.string.overview_section_faces))
        AppCard {
            Column {
                faces.forEach { face ->
                    OverviewFaceRow(face) {
                        vm.selectWorkbenchTab(face.tab)
                        vm.setDestination(Destination.ENTRIES)
                    }
                }
            }
        }

        OverviewSectionLabel(stringResource(R.string.overview_section_spaces))
        AppCard {
            Column {
                spaces.forEach { space ->
                    OverviewSpaceRow(
                        icon = if (space.userId == 0) Icons.Rounded.Smartphone else Icons.Rounded.Person,
                        title = space.title,
                        active = space.userId == activeSpace,
                        summary = if (space.userId == activeSpace) {
                            stringResource(R.string.overview_space_summary, state.selected.size)
                        } else {
                            null
                        },
                        onSwitch = { vm.switchSpace(space.userId) }
                    )
                }
            }
        }

        OverviewSectionLabel(stringResource(R.string.overview_section_ops))
        AppCard {
            Column {
                OverviewActionRow(stringResource(R.string.logs_title), Icons.Rounded.History, vm::openLogs)
                OverviewMenuRow(
                    title = stringResource(R.string.home_reset_default) + " · " +
                        stringResource(R.string.home_diagnostic_short),
                    icon = Icons.Rounded.RestartAlt
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_reset_default)) },
                        leadingIcon = { Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp)) },
                        onClick = { resetSheetOpen = true }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dashboard_export_diagnostics)) },
                        leadingIcon = { Icon(Icons.Rounded.BugReport, null, Modifier.size(18.dp)) },
                        enabled = !collectingDiagnostics,
                        onClick = onCollectDiagnostics
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dashboard_inspect_file)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) },
                        enabled = !checkingFile,
                        onClick = onInspectFile
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.diagnostic_mode)) },
                        trailingIcon = {
                            Checkbox(checked = state.diagnosticMode, onCheckedChange = null)
                        },
                        onClick = { vm.setDiagnosticMode(!state.diagnosticMode) }
                    )
                }
                OverviewMenuRow(
                    title = stringResource(R.string.dashboard_data_backup),
                    icon = Icons.Rounded.FileUpload
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dashboard_restore_json)) },
                        leadingIcon = { Icon(Icons.Rounded.FileDownload, null, Modifier.size(18.dp)) },
                        onClick = onRestore
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dashboard_export_json)) },
                        leadingIcon = { Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp)) },
                        onClick = onExport
                    )
                }
            }
        }

        state.error?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        fileCheckStatus?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 14.dp),
                style = MaterialTheme.typography.bodySmall
            )
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

private enum class OverviewFaceLevel { OK, UNOBSERVED, DEGRADED }

private data class OverviewFace(
    val tab: WorkbenchTab,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val rules: Int,
    val hits: Long?,
    val level: OverviewFaceLevel,
)

/** Small gray section caption above each overview card ("[zh]" / "[zh]" / "[zh]"). */
@Composable
private fun OverviewSectionLabel(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OverviewModePill(
    mode: com.eio258.cleanchooser.domain.DisplayMode,
    onSelect: (com.eio258.cleanchooser.domain.DisplayMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.dashboard_global_mode),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(mode.titleRes()),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            com.eio258.cleanchooser.domain.DisplayMode.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(stringResource(candidate.titleRes())) },
                    leadingIcon = {
                        if (candidate == mode) Icon(Icons.Rounded.Check, null)
                    },
                    onClick = { expanded = false; onSelect(candidate) }
                )
            }
        }
    }
}

/**
 * One surface row in the "[zh]" card: 20dp circular primary icon, a bold 12.5sp
 * name and a 10.5sp gray summary. Abnormal surfaces get a 3dp red edge bar, a dimmed
 * name and the reason appended to the summary.
 */
@Composable
private fun OverviewFaceRow(face: OverviewFace, onOpen: () -> Unit) {
    val abnormal = face.level != OverviewFaceLevel.OK
    val degraded = face.level == OverviewFaceLevel.DEGRADED
    val baseSummary = stringResource(
        R.string.overview_face_summary,
        face.rules,
        face.hits?.toString() ?: "—"
    )
    val reason = if (abnormal) {
        stringResource(
            if (degraded) R.string.overview_face_degraded
            else R.string.overview_face_unobserved
        )
    } else {
        null
    }
    val reasonColor = if (degraded) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurfaceVariant
    val summary = buildAnnotatedString {
        append(baseSummary)
        if (reason != null) {
            append(" · ")
            withStyle(SpanStyle(color = reasonColor)) { append(reason) }
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (abnormal) {
            Box(
                Modifier.width(3.dp).height(28.dp).background(
                    MaterialTheme.colorScheme.error,
                    RoundedCornerShape(2.dp)
                )
            )
        }
        Box(
            Modifier.size(20.dp).background(
                if (abnormal) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.primary,
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                face.icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (abnormal) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onPrimary
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(face.tab.labelRes()),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (abnormal) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                summary,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * One row in the "[zh]" card: 20dp circular icon (phone for the local user, person for
 * clones), the space title and an optional summary; the active row carries a "[zh]"
 * badge, the others a compact "[zh]" text button.
 */
@Composable
private fun OverviewSpaceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    active: Boolean,
    summary: String?,
    onSwitch: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (active) Modifier else Modifier.clickable(onClick = onSwitch))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(20.dp).background(
                MaterialTheme.colorScheme.primary,
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            summary?.let {
                Text(
                    it,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (active) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    stringResource(R.string.overview_space_active),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        } else {
            TextButton(
                onClick = onSwitch,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    stringResource(R.string.dashboard_switch),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** One compact maintenance row: circular icon + name + chevron. */
@Composable
private fun OverviewActionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(20.dp).background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (enabled) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/** Maintenance row whose chevron tap opens an inline dropdown of sub-actions. */
@Composable
private fun OverviewMenuRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OverviewActionRow(title, icon, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, content = menuContent)
    }
}

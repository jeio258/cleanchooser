package com.eio258.cleanchooser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eio258.cleanchooser.BuildConfig
import com.eio258.cleanchooser.R

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

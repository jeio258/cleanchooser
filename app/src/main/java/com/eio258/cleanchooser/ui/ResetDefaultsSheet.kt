package com.eio258.cleanchooser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.data.CleanupKind
import com.eio258.cleanchooser.data.PersistentComponentState
import com.eio258.cleanchooser.data.RootComponentScan

/** Shared reset sheet: pick per-section restores or wipe everything back to defaults. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResetDefaultsSheet(
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
            Text(
                stringResource(R.string.reset_default_scope_hint),
                style = MaterialTheme.typography.labelSmall,
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

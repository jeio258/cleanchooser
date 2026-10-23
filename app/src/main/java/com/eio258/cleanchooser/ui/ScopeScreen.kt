package com.eio258.cleanchooser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eio258.cleanchooser.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScopeDialog(status: ModuleStatus, refresh: () -> Unit, dismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.scope_title), style = MaterialTheme.typography.titleMedium)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.scope_intro), style = MaterialTheme.typography.bodySmall)
                SectionText(stringResource(R.string.scope_detected_hosts))
                if (status.detection.hosts.isEmpty()) Text(stringResource(R.string.scope_no_hosts))
                status.detection.hosts.forEach { host ->
                    Text(
                        stringResource(
                            R.string.scope_host_details,
                            host.packageName,
                            host.className,
                            host.processName,
                            host.scenarios.joinToString(" · ")
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (host.requiresManualScope) {
                        Text(
                            stringResource(R.string.scope_manual_host_warning),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (host.packageName == "android") {
                        Text(stringResource(R.string.scope_android_system_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
                val unconfirmed = status.detection.installedCandidates - status.detection.hosts.map { it.packageName }.toSet()
                if (unconfirmed.isNotEmpty()) {
                    Text(
                        stringResource(R.string.scope_unconfirmed_installed, unconfirmed.sorted().joinToString("\n")),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                SectionText(stringResource(R.string.scope_granted))
                Text(
                    if (!status.scopeKnown) stringResource(R.string.scope_grant_unknown)
                    else status.grantedScope.sorted().joinToString("\n").ifEmpty { stringResource(R.string.common_none) },
                    style = MaterialTheme.typography.bodySmall
                )
                if (status.scopeKnown && status.extraScope.isNotEmpty()) {
                    Text(
                        stringResource(R.string.scope_extra_grants, status.extraScope.sorted().joinToString("\n")),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                SectionText(stringResource(R.string.scope_running_targets))
                if (status.runningTargets.isEmpty()) Text(stringResource(R.string.scope_no_running_targets))
                status.runningTargets.forEach { target ->
                    val stateLabel = when (target.state) {
                        "UP_TO_DATE" -> stringResource(R.string.scope_target_up_to_date)
                        "STALE" -> stringResource(R.string.scope_target_stale)
                        "RELOADING" -> stringResource(R.string.scope_target_reloading)
                        "FAILED" -> stringResource(R.string.scope_target_failed)
                        else -> target.state
                    }
                    Text(
                        stringResource(R.string.scope_target_details, target.processName, stateLabel, target.version),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(stringResource(R.string.scope_runtime_warning), style = MaterialTheme.typography.bodySmall)
                status.detection.warnings.forEach {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                status.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                status.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.scope_advanced), style = MaterialTheme.typography.labelSmall)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = refresh) {
                    Text(stringResource(R.string.scope_recheck))
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                TextButton(onClick = dismiss) { Text(stringResource(R.string.common_close)) }
            }
        }
    }
}

@Composable
private fun SectionText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
}

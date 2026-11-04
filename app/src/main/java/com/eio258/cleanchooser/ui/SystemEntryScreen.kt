package com.eio258.cleanchooser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.state.ToggleableState
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.domain.ComponentCandidate
import com.eio258.cleanchooser.domain.IntentKind

/**
 * Settings-entry workbench: every Android subsystem list this module can manage (input methods,
 * accessibility services, VPN, print, notification listeners, ...) grouped by entry kind.
 * Hidden entries stay in the list so they can be restored.
 */
internal val SYSTEM_ENTRY_KINDS: List<IntentKind> = listOf(
    IntentKind.INPUT_METHOD,
    IntentKind.AUTOFILL,
    IntentKind.ACCESSIBILITY,
    IntentKind.VPN,
    IntentKind.PRINT,
    IntentKind.NOTIFICATION_LISTENER,
    IntentKind.WALLPAPER,
    IntentKind.DREAM,
    IntentKind.NFC_HCE,
    IntentKind.CALL_SCREENING,
    IntentKind.DOCUMENT_PROVIDER,
    IntentKind.PHOTO_SOURCE,
    IntentKind.HOME,
    IntentKind.ASSISTANT,
    IntentKind.BROWSER,
)

internal fun IntentKind.systemEntryTitle(): String = when (this) {
    IntentKind.INPUT_METHOD -> "输入法"
    IntentKind.AUTOFILL -> "自动填充"
    IntentKind.ACCESSIBILITY -> "无障碍"
    IntentKind.VPN -> "VPN"
    IntentKind.PRINT -> "打印服务"
    IntentKind.NOTIFICATION_LISTENER -> "通知读取"
    IntentKind.WALLPAPER -> "壁纸"
    IntentKind.DREAM -> "屏保"
    IntentKind.NFC_HCE -> "NFC 卡模拟"
    IntentKind.CALL_SCREENING -> "来电筛选"
    IntentKind.DOCUMENT_PROVIDER -> "文档提供器"
    IntentKind.PHOTO_SOURCE -> "云相册来源"
    IntentKind.HOME -> "桌面"
    IntentKind.ASSISTANT -> "助手"
    IntentKind.BROWSER -> "浏览器"
    else -> name
}

@Composable
fun SystemEntryScreen(state: MainState, vm: MainViewModel) {
    val candidates = state.candidates.filter { it.rule.kind in SYSTEM_ENTRY_KINDS }
    val grouped = remember(candidates) {
        SYSTEM_ENTRY_KINDS.mapNotNull { kind ->
            val rows = candidates.filter { it.rule.kind == kind }
                .sortedWith(compareBy({ it.unavailable }, { it.normalizedAppLabel }))
            if (rows.isEmpty()) null else kind to rows
        }
    }
    val hiddenCount = candidates.count { it.rule in state.selected }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
        ) {
            item(key = "status") {
                SystemEntryBanner(
                    ready = state.runtime.ready,
                    hiddenCount = hiddenCount
                )
            }
            grouped.forEach { (kind, rows) ->
                item(key = "header|$kind") {
                    SectionLabel(
                        kind.systemEntryTitle(),
                        "${rows.count { it.rule !in state.selected }}/${rows.size} 可见"
                    )
                }
                items(rows, key = { "${it.rule.kind}|${it.rule.id}" }) { row ->
                    val hidden = row.rule in state.selected
                    SystemEntryRow(
                        candidate = row,
                        hidden = hidden,
                        onToggle = { vm.toggle(row.rule) },
                    )
                }
            }
            if (grouped.isEmpty()) {
                item(key = "empty") {
                    EmptyHint(stringResource(R.string.text_menu_empty))
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        val hiddenRules = candidates.filter { it.rule in state.selected }.map { it.rule }
                        vm.deselectRules(hiddenRules, filter = null, preset = null)
                    },
                    enabled = hiddenCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.text_menu_clear_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SystemEntryBanner(ready: Boolean, hiddenCount: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .background(
                if (ready) colors.secondaryContainer.copy(alpha = 0.5f) else colors.surfaceContainerHigh,
                AppShape.tile
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(7.dp).background(
                if (ready) colors.primary else colors.error,
                androidx.compose.foundation.shape.CircleShape
            )
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    if (ready) R.string.text_menu_observed else R.string.text_menu_saved_pending
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                stringResource(R.string.system_entry_hidden_hint, hiddenCount),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String, hint: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            hint,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SystemEntryRow(
    candidate: ComponentCandidate,
    hidden: Boolean,
    onToggle: () -> Unit,
) {
    AppEntryRow(
        title = candidate.activityLabel.ifBlank { candidate.appLabel },
        subtitle = "${candidate.appLabel} · ${candidate.rule.packageName}",
        icon = candidate.appIcon,
        onOpen = {},
        modifier = Modifier.alpha(if (hidden) 0.6f else 1f),
        checkState = if (hidden) ToggleableState.On else ToggleableState.Off,
        onCheck = onToggle,
        showChevron = false,
        trailing = {
            if (hidden) {
                Text(
                    stringResource(R.string.text_menu_hidden),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (candidate.unavailable) {
                Text(
                    stringResource(R.string.system_entry_unobserved),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

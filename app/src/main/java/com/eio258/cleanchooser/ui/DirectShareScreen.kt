package com.eio258.cleanchooser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eio258.cleanchooser.R
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.state.ToggleableState
import com.eio258.cleanchooser.domain.ComponentCandidate
import com.eio258.cleanchooser.domain.DirectShareType
import com.eio258.cleanchooser.domain.DirectShareTypeRules
import com.eio258.cleanchooser.domain.IntentKind

/**
 * Direct Share / XXXXXXXXXXX：XXXXX + XXXX + MIME XXX。
 * XXXXX DIRECT_SHARE XX（vm.toggle）；XXXXXXXXXXX，XXXXXXX。
 */
@Composable
fun DirectShareScreen(state: MainState, vm: MainViewModel) {
    val candidates = state.candidates.filter { it.rule.kind == IntentKind.DIRECT_SHARE }
    var familyFilter by remember { mutableStateOf<DirectShareType?>(null) }

    val rows = remember(candidates, state.selected, familyFilter) {
        buildDirectShareRows(candidates, state.selected, familyFilter)
    }
    val hiddenCount = rows.count { it.hidden }

    Column(Modifier.fillMaxSize()) {
        StatusBanner(ready = state.runtime.ready, candidates = candidates.size, hiddenCount = hiddenCount)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FamilyChip(label = stringResource(R.string.direct_share_family_all), selected = familyFilter == null) { familyFilter = null }
            DirectShareType.entries.forEach { family ->
                FamilyChip(
                    label = stringResource(familyLabelRes(family)),
                    selected = familyFilter == family,
                ) { familyFilter = if (familyFilter == family) null else family }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
        ) {
            item(key = "sort-hint") {
                Text(
                    stringResource(R.string.direct_share_sort_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }
            items(rows, key = { it.candidate.rule.id }) { row ->
                AppEntryRow(
                    title = row.title,
                    subtitle = row.subtitle,
                    icon = row.icon,
                    onOpen = {},
                    modifier = Modifier.alpha(if (row.hidden) 0.6f else 1f),
                    checkState = if (row.hidden) ToggleableState.On else ToggleableState.Off,
                    onCheck = { vm.toggle(row.candidate.rule) },
                    showChevron = false,
                    trailing = {
                        if (row.families.isNotEmpty()) {
                            Text(
                                row.families.map { stringResource(familyLabelRes(it)) }
                                    .joinToString("/"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (row.hidden) {
                            Text(
                                stringResource(R.string.direct_share_hidden),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (candidates.isEmpty()) stringResource(R.string.direct_share_empty)
                            else stringResource(R.string.direct_share_family_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            TextButton(
                onClick = { rows.filter { it.hidden }.forEach { vm.toggle(it.candidate.rule) } },
                enabled = hiddenCount > 0,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.direct_share_restore_all, hiddenCount), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private data class DirectShareRow(
    val candidate: ComponentCandidate,
    val title: String,
    val subtitle: String,
    val icon: Bitmap?,
    val families: Set<DirectShareType>,
    val hidden: Boolean,
)

/** Targets without MIME evidence fall back to the All family. */
private fun buildDirectShareRows(
    candidates: List<ComponentCandidate>,
    selected: Set<com.eio258.cleanchooser.domain.ComponentRule>,
    familyFilter: DirectShareType?,
): List<DirectShareRow> {
    val rows = candidates.map { candidate ->
        val families = DirectShareTypeRules.observed(candidate.evidence)
        DirectShareRow(
            candidate = candidate,
            title = candidate.activityLabel.ifBlank { candidate.appLabel },
            subtitle = candidate.rule.packageName,
            icon = candidate.appIcon,
            families = families,
            hidden = candidate.rule in selected,
        )
    }
    val visible = if (familyFilter == null) rows else rows.filter { familyFilter in it.families }
    return visible.sortedWith(
        compareBy<DirectShareRow> { if (it.hidden) 1 else 0 }
            .thenBy { it.title.lowercase() }
    )
}

@Composable
private fun StatusBanner(ready: Boolean, candidates: Int, hiddenCount: Int) {
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
        Box(
            Modifier.size(7.dp).background(
                if (ready) colors.primary else colors.error,
                androidx.compose.foundation.shape.CircleShape
            )
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (ready) stringResource(R.string.direct_share_status_connected) else stringResource(R.string.direct_share_status_waiting),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            val summary = stringResource(
                R.string.direct_share_summary, candidates, hiddenCount,
            )
            Text(
                summary,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FamilyChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    TextButton(onClick = onSelect, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun familyLabelRes(family: DirectShareType): Int = when (family) {
    DirectShareType.TEXT -> R.string.direct_share_type_text
    DirectShareType.IMAGE -> R.string.direct_share_type_image
    DirectShareType.VIDEO -> R.string.direct_share_type_video
    DirectShareType.AUDIO -> R.string.direct_share_type_audio
    DirectShareType.FILE -> R.string.direct_share_type_file
}

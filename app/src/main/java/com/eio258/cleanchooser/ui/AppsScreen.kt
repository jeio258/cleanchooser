package com.eio258.cleanchooser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.data.CleanupKind
import com.eio258.cleanchooser.data.RootComponent
import com.eio258.cleanchooser.domain.DisplayMode
import com.eio258.cleanchooser.domain.ComponentCandidate
import com.eio258.cleanchooser.domain.displayNameComparator

/**
 * App-node master list: every package aggregated across the surfaces it hits
 * (share panel, open-with, text menu, direct share, tiles, system entries), plus the
 * standalone system-entry and tile nodes. The mode pill, space selector and search stay
 * resident at the top; navigation into [NodeDetailScreen] is wired by MainActivity.
 */
@Composable
fun AppsScreen(state: MainState, vm: MainViewModel) {
    val spaces by vm.spaceSelectorItems.collectAsStateWithLifecycle()
    val activeSpace by vm.activeSpaceId.collectAsStateWithLifecycle()
    val scan by vm.componentScan.collectAsStateWithLifecycle()
    val summaries = remember(state) { vm.nodeSummaries(state) }
    val query = state.query

    val appNodes = remember(summaries, query) {
        if (query.isBlank()) summaries else summaries.filter { summary ->
            summary.appLabel.contains(query, ignoreCase = true) ||
                summary.packageName.contains(query, ignoreCase = true) ||
                summary.entries.any { it.matchesQuery(query) }
        }
    }
    val entryNodes = remember(state.candidates, query) {
        state.candidates
            .filter { NodeSurface.SYSTEM_ENTRY.contains(it.rule.kind) && it.matchesQuery(query) }
            .sortedWith(
                java.util.Comparator<ComponentCandidate> { a, b ->
                    displayNameComparator().compare(a.appLabel.lowercase(), b.appLabel.lowercase())
                }.thenComparator { a, b -> a.rule.packageName.compareTo(b.rule.packageName) }
            )
    }
    val tileNodes = remember(scan, query) {
        val needle = query.trim().lowercase()
        scan.items.filter { item ->
            item.kind == CleanupKind.TILE && (needle.isEmpty() ||
                item.label.lowercase().contains(needle) ||
                item.component.className.lowercase().contains(needle) ||
                item.component.packageName.lowercase().contains(needle))
        }
    }

    LaunchedEffect(Unit) { if (scan.items.isEmpty()) vm.refreshComponents() }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModePill(mode = state.displayMode, onChange = vm::setDisplayMode)
                Spacer(Modifier.weight(1f))
                SpaceSelectorBar(
                    spaces = spaces,
                    selectedUserId = activeSpace,
                    onSelect = vm::switchSpace
                )
            }
            CompactSearchField(query = query, onQueryChange = vm::setQuery)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header-apps") {
                SectionLabel(
                    stringResource(R.string.app_type_filter),
                    stringResource(R.string.app_list_count, appNodes.size)
                )
            }
            if (appNodes.isEmpty()) {
                item(key = "empty-apps") { EmptyHint(stringResource(R.string.no_matching_components)) }
            }
            items(appNodes, key = { "app|${it.packageName}" }) { summary ->
                NodeAppRow(
                    summary = summary,
                    activeSpaceId = activeSpace,
                    onOpen = { vm.openNodeDetail(summary.packageName) }
                )
            }

            item(key = "header-nodes") {
                SectionLabel(stringResource(R.string.node_section_entries))
            }
            if (entryNodes.isEmpty() && tileNodes.isEmpty()) {
                item(key = "empty-nodes") { EmptyHint(stringResource(R.string.no_matching_components)) }
            }
            items(entryNodes, key = { "sys|${it.rule.id}" }) { candidate ->
                NodeEntryNodeRow(
                    candidate = candidate,
                    hidden = candidate.rule in state.selected,
                    onOpen = { vm.openNodeDetail(candidate.rule.packageName) }
                )
            }
            items(tileNodes, key = { "tile|${it.id}" }) { item ->
                TileNodeRow(item = item, onOpen = { vm.openNodeDetail(item.component.packageName) })
            }
        }
    }
}

@Composable
private fun NodeAppRow(summary: NodeSummary, activeSpaceId: Int, onOpen: () -> Unit) {
    AppEntryRow(
        title = summary.appLabel,
        subtitle = stringResource(
            R.string.node_row_summary,
            summary.surfaces.size,
            summary.surfaces.map { stringResource(it.titleRes) }.joinToString("/")
        ),
        icon = summary.appIcon,
        onOpen = onOpen,
        trailing = {
            if (activeSpaceId != 0) NodeUserBadge(activeSpaceId)
            NodeStatusDots(
                hidden = summary.hiddenCount > 0,
                ranked = summary.rankKinds.isNotEmpty()
            )
        }
    )
}

@Composable
private fun NodeEntryNodeRow(
    candidate: com.eio258.cleanchooser.domain.ComponentCandidate,
    hidden: Boolean,
    onOpen: () -> Unit,
) {
    AppEntryRow(
        title = candidate.activityLabel.ifBlank { candidate.appLabel },
        subtitle = stringResource(
            R.string.list_summary_format,
            candidate.appLabel,
            stringResource(candidate.rule.kind.titleRes())
        ),
        icon = candidate.appIcon,
        onOpen = onOpen,
        modifier = Modifier.alpha(if (hidden) 0.6f else 1f),
        showChevron = true,
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

@Composable
private fun TileNodeRow(item: RootComponent, onOpen: () -> Unit) {
    AppEntryRow(
        title = item.label.takeIf { it.any(Char::isLetterOrDigit) }
            ?: item.component.className.substringAfterLast('.'),
        subtitle = stringResource(
            R.string.list_summary_format,
            stringResource(item.kind.titleRes()),
            item.owner
        ),
        icon = item.icon,
        onOpen = onOpen,
        showChevron = true,
        trailing = {
            if (item.enabled == false && item.blocked == null) {
                Text(
                    stringResource(R.string.text_menu_hidden),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

/** Small "user N" pill marking rules that belong to a non-zero space. */
@Composable
internal fun NodeUserBadge(userId: Int) {
    Text(
        stringResource(R.string.space_user_title, userId),
        modifier = Modifier
            .clip(AppShape.tile)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        maxLines = 1
    )
}

/** Aggregate status dots for one node row: red = has hidden entries, blue = has ranking. */
@Composable
private fun NodeStatusDots(hidden: Boolean, ranked: Boolean) {
    if (!hidden && !ranked) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hidden) {
            Box(
                Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape)
            )
        }
        if (ranked) {
            Box(
                Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

/** Resident global-mode pill with an inline dropdown for the three DisplayMode values. */
@Composable
private fun ModePill(mode: DisplayMode, onChange: (DisplayMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(AppShape.tile)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(
                    R.string.compact_filter_format,
                    stringResource(R.string.dashboard_global_mode),
                    stringResource(mode.titleRes())
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DisplayMode.entries.forEach { value ->
                DropdownMenuItem(
                    text = { Text(stringResource(value.titleRes())) },
                    trailingIcon = if (value == mode) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        expanded = false
                        if (value != mode) onChange(value)
                    }
                )
            }
        }
    }
}

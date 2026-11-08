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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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

    val appTypeFilter = vm.workbenchFilters.appType
    val appNodes = remember(summaries, query, appTypeFilter) {
        val inClass = summaries.filter { appTypeFilter.matches(it.appType) }
        if (query.isBlank()) inClass else inClass.filter { summary ->
            summary.appLabel.contains(query, ignoreCase = true) ||
                summary.packageName.contains(query, ignoreCase = true) ||
                summary.entries.any { it.matchesQuery(query) }
        }
    }
    // Rules whose package cannot be resolved still need to be visible and adjustable; they are
    // listed in their own trailing bucket instead of being hidden.
    val appSections = remember(appNodes) {
        sectionsByAppType(appNodes, { it.appType }, { it.isMissing })
    }
    val orphanCount = appSections.firstOrNull { it.type == null }?.items?.size ?: 0
    var confirmClearOrphans by remember { mutableStateOf(false) }

    if (confirmClearOrphans) {
        AlertDialog(
            onDismissRequest = { confirmClearOrphans = false },
            title = { Text(stringResource(R.string.app_clear_orphan_title)) },
            text = { Text(stringResource(R.string.app_clear_orphan_message, orphanCount)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearRulesWithoutPackage()
                    confirmClearOrphans = false
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearOrphans = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    CompactSearchField(query = query, onQueryChange = vm::setQuery)
                }
                AppTypeTrigger(
                    current = appTypeFilter,
                    onSelect = { mode -> vm.updateWorkbenchFilters { it.copy(appType = mode) } }
                )
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (appSections.isEmpty()) {
                item(key = "empty-apps") { EmptyHint(stringResource(R.string.no_matching_components)) }
            }
            val showTypeHeaders = appSections.needsTypeHeaders()
            appSections.forEach { section ->
                item(key = "header-apps-" + (section.type?.name ?: "missing")) {
                    SectionLabel(
                        title = when {
                            showTypeHeaders -> appSectionTitle(section.type)
                            section.type == null ->
                                stringResource(R.string.app_section_missing_count, section.items.size)
                            else -> stringResource(R.string.app_list_count, section.items.size)
                        },
                        hint = if (showTypeHeaders) section.items.size.toString() else null,
                        compact = true,
                        trailing = if (section.type == null) {
                            {
                                TextButton(onClick = { confirmClearOrphans = true }) {
                                    Text(
                                        stringResource(R.string.app_clear_orphan_rules),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        } else {
                            null
                        }
                    )
                }
                items(section.items, key = { "app|" + it.packageName }) { summary ->
                    NodeAppRow(
                        summary = summary,
                        activeSpaceId = activeSpace,
                        onOpen = { vm.openNodeDetail(summary.packageName) }
                    )
                }
            }

        }
    }
}

@Composable
private fun NodeAppRow(summary: NodeSummary, activeSpaceId: Int, onOpen: () -> Unit) {
    AppEntryRow(
        title = summary.appLabel,
        // For unresolvable packages the only honest content is what the rule actually controls:
        // which surface it targets and which component inside it.
        subtitle = if (summary.isMissing) missingRowSubtitle(summary) else "",
        icon = summary.appIcon,
        onOpen = onOpen,
        iconMissing = summary.isMissing,
        trailing = {
            if (activeSpaceId != 0) NodeUserBadge(activeSpaceId)
            NodeStatusDots(
                hidden = summary.hiddenCount > 0,
                ranked = summary.rankKinds.isNotEmpty()
            )
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
private fun NodeStatusDots(hidden: Boolean, ranked: Boolean, neutral: Boolean = false) {
    if (!hidden && !ranked && !neutral) return
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
        if (neutral) {
            Box(
                Modifier.size(8.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
            )
        }
    }
}

/** Resident global-mode pill with an inline dropdown for the three DisplayMode values. */

/** "Owner label · package" for component rows; never repeats the package twice. */
/** "Share panel · ExternalLinkActivity" - states what the rule acts on when the app is unknown. */
@Composable
private fun missingRowSubtitle(summary: NodeSummary): String {
    val first = summary.entries.firstOrNull() ?: return stringResource(R.string.app_row_missing_hint)
    val surface = stringResource(first.rule.kind.titleRes())
    val component = first.rule.className.substringAfterLast('.')
    return surface + " · " + component
}

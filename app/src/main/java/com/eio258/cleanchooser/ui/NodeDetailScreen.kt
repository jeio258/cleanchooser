package com.eio258.cleanchooser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.data.RootComponent
import com.eio258.cleanchooser.domain.ComponentCandidate
import com.eio258.cleanchooser.domain.IntentKind

/**
 * Node detail aggregation page: one package, every surface it appears on rendered as its
 * own section (share panel / open-with / text menu / direct share / tiles / system entries).
 * Surfaces the package does not hit collapse into a single hint line, and clone-space
 * rules are shown read-only with user badges. The "view all" links jump into the matching
 * workbench segment; the back stack and destination wiring belong to MainActivity.
 */
@Composable
fun NodeDetailScreen(packageName: String, state: MainState, vm: MainViewModel) {
    val scan by vm.componentScan.collectAsStateWithLifecycle()
    val spaces by vm.spaceSelectorItems.collectAsStateWithLifecycle()
    val activeSpace by vm.activeSpaceId.collectAsStateWithLifecycle()
    val detail = remember(packageName, state, scan) { vm.nodeDetail(packageName, state) }
    val spaceTitle = spaces.firstOrNull { it.userId == activeSpace }?.title
        ?: stringResource(R.string.space_user_title, activeSpace)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        item(key = "hero") {
            NodeHero(
                appLabel = detail.appLabel,
                packageName = detail.packageName,
                appIcon = detail.appIcon,
                surfaceCount = detail.groups.size,
                spaceTitle = spaceTitle
            )
        }

        detail.groups.forEach { group ->
            item(key = "header-${group.surface.name}") {
                SectionLabel(
                    stringResource(group.surface.titleRes),
                    stringResource(
                        R.string.system_entry_visible_count,
                        totalOf(group) - group.hiddenCount,
                        totalOf(group)
                    )
                )
            }
            items(group.candidates, key = { "${group.surface.name}|${it.rule.id}" }) { candidate ->
                NodeCandidateRow(
                    candidate = candidate,
                    hidden = candidate.rule in state.selected,
                    rank = vm.nodeRank(candidate.rule.kind, packageName, state.priorities),
                    onToggle = { vm.toggle(candidate.rule) }
                )
            }
            items(group.components, key = { "tile|${it.id}" }) { component ->
                NodeTileRow(component = component, vm = vm)
            }
            item(key = "link-${group.surface.name}") {
                ViewAllInSurfaceLink(group.surface) {
                    vm.closeNodeDetail()
                    vm.selectWorkbenchTab(group.surface.tab)
                    vm.setDestination(Destination.ENTRIES)
                }
            }
        }

        if (detail.missedSurfaces.isNotEmpty()) {
            item(key = "missed") {
                Text(
                    stringResource(
                        R.string.node_surfaces_absent,
                        detail.missedSurfaces.map { stringResource(it.titleRes) }
                            .joinToString(" / ")
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        detail.cloneRules.forEach { clone ->
            item(key = "clone-header-${clone.userId}") {
                SectionLabel(stringResource(R.string.node_clone_section, clone.userId))
            }
            items(clone.rules, key = { "clone|${clone.userId}|${it.id}" }) { rule ->
                NodeCloneRuleRow(userId = clone.userId, rule = rule)
            }
        }
    }
}

private fun totalOf(group: NodeSurfaceGroup): Int = group.candidates.size + group.components.size

@Composable
private fun NodeHero(
    appLabel: String,
    packageName: String,
    appIcon: android.graphics.Bitmap?,
    surfaceCount: Int,
    spaceTitle: String,
) {
    AppCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(bitmap = appIcon, appLabel = appLabel, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.node_detail_summary, surfaceCount, spaceTitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NodeCandidateRow(
    candidate: ComponentCandidate,
    hidden: Boolean,
    rank: Int?,
    onToggle: () -> Unit,
) {
    AppEntryRow(
        title = candidate.activityLabel.ifBlank { candidate.appLabel },
        subtitle = stringResource(
            R.string.list_summary_format,
            candidate.appLabel,
            stringResource(candidate.rule.kind.titleRes())
        ),
        icon = candidate.appIcon,
        onOpen = {},
        modifier = Modifier.alpha(if (hidden) 0.6f else 1f),
        checkState = if (hidden) ToggleableState.On else ToggleableState.Off,
        onCheck = onToggle,
        rank = rank,
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

@Composable
private fun NodeTileRow(component: RootComponent, vm: MainViewModel) {
    AppEntryRow(
        title = component.label.takeIf { it.any(Char::isLetterOrDigit) }
            ?: component.component.className.substringAfterLast('.'),
        subtitle = stringResource(
            R.string.list_summary_format,
            stringResource(component.kind.titleRes()),
            component.owner
        ),
        icon = component.icon,
        onOpen = {},
        showChevron = false,
        trailing = {
            Switch(
                checked = component.enabled == true,
                onCheckedChange = { vm.changeComponent(component, it) }
            )
        }
    )
}

@Composable
private fun ViewAllInSurfaceLink(surface: NodeSurface, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(start = 8.dp)) {
        Text(
            stringResource(R.string.node_view_all_in_surface, stringResource(surface.titleRes)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, Modifier.size(16.dp))
    }
}

/** Read-only clone-space rule row; the user badge is the only signal, edits stay in that space. */
@Composable
private fun NodeCloneRuleRow(userId: Int, rule: com.eio258.cleanchooser.domain.ComponentRule) {
    AppEntryRow(
        title = stringResource(rule.kind.titleRes()),
        subtitle = rule.className,
        icon = null,
        onOpen = {},
        showChevron = false,
        trailing = { NodeUserBadge(userId) }
    )
}

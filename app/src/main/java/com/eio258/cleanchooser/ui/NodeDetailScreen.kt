package com.eio258.cleanchooser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.data.RootComponent
import com.eio258.cleanchooser.domain.ComponentCandidate

/**
 * Node detail aggregation page: one package, every surface it appears on rendered as its
 * own section (share panel / open-with / text menu / direct share / tiles / system entries).
 * The hero sits directly on the background, each surface is one white compact card, missed
 * surfaces collapse into a hint line, and clone-space rules are read-only user-badge rows.
 * The "view all" links jump into the matching workbench segment; back and destination
 * wiring belong to MainActivity.
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
                NodeSectionHeader(surface = group.surface)
            }
            item(key = "card-${group.surface.name}") {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    shape = AppShape.tile,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        group.candidates.forEach { candidate ->
                            NodeCandidateRow(
                                surface = group.surface,
                                candidate = candidate,
                                hidden = candidate.rule in state.selected,
                                rank = vm.nodeRank(candidate.rule.kind, packageName, state.priorities),
                                onToggle = { vm.toggle(candidate.rule) }
                            )
                        }
                        group.components.forEach { component ->
                            NodeTileRow(surface = group.surface, component = component, vm = vm)
                        }
                        if (group.components.isNotEmpty()) {
                            TextButton(
                                onClick = { vm.restoreComponentsForApp(packageName) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    stringResource(R.string.detail_restore_components),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
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
            item(key = "clone-card-${clone.userId}") {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    shape = AppShape.tile,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        clone.rules.forEach { rule ->
                            NodeCloneRuleRow(userId = clone.userId, rule = rule)
                        }
                    }
                }
            }
        }
    }
}

private fun surfaceIcon(surface: NodeSurface): ImageVector = when (surface) {
    NodeSurface.SHARE_PANEL -> Icons.Rounded.Share
    NodeSurface.OPEN_WITH -> Icons.Rounded.Description
    NodeSurface.TEXT_MENU -> Icons.Rounded.Keyboard
    NodeSurface.DIRECT_SHARE -> Icons.Rounded.Link
    NodeSurface.TILE -> Icons.Rounded.Widgets
    NodeSurface.SYSTEM_ENTRY -> Icons.Rounded.Settings
}

/** Small circular icon chip shared by section headers and compact rows. */
@Composable
private fun SurfaceIconChip(
    surface: NodeSurface,
    chipSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    primaryTinted: Boolean,
) {
    Box(
        Modifier
            .size(chipSize)
            .clip(CircleShape)
            .background(
                if (primaryTinted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            surfaceIcon(surface),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = if (primaryTinted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Section header: face icon on a primary circle plus the face name in muted caps-ish text. */
@Composable
private fun NodeSectionHeader(surface: NodeSurface) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SurfaceIconChip(
            surface = surface,
            chipSize = 24.dp,
            iconSize = 14.dp,
            primaryTinted = true
        )
        Text(
            stringResource(surface.titleRes),
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Hero row directly on the background: 44dp icon, bold label, package, and badge row. */
@Composable
private fun NodeHero(
    appLabel: String,
    packageName: String,
    appIcon: android.graphics.Bitmap?,
    surfaceCount: Int,
    spaceTitle: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppIcon(bitmap = appIcon, appLabel = appLabel, size = 44.dp)
        Column(Modifier.weight(1f)) {
            // The app name lives in the top bar (DetailTopBar); repeating it here made the page
            // read as two stacked headers, so the hero keeps only the package and the badges.
            Text(
                packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.node_hero_surface_badge, surfaceCount),
                    modifier = Modifier
                        .clip(AppShape.tile)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
                Text(
                    spaceTitle,
                    modifier = Modifier
                        .clip(AppShape.tile)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1
                )
            }
        }
    }
}

/** Compact title style shared by every row inside the surface cards. */
private val CompactTitleStyle = TextStyle(
    fontSize = 12.5.sp,
    lineHeight = 17.sp,
    fontWeight = FontWeight.Medium
)

/** One compact entry row: face chip, title, status, then the trailing control. */
@Composable
private fun NodeCompactRow(
    surface: NodeSurface,
    title: String,
    hidden: Boolean = false,
    status: @Composable (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .alpha(if (hidden) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SurfaceIconChip(
            surface = surface,
            chipSize = 20.dp,
            iconSize = 12.dp,
            primaryTinted = false
        )
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = CompactTitleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        status?.invoke()
        trailing()
    }
}

@Composable
private fun NodeCandidateRow(
    surface: NodeSurface,
    candidate: ComponentCandidate,
    hidden: Boolean,
    rank: Int?,
    onToggle: () -> Unit,
) {
    NodeCompactRow(
        surface = surface,
        title = candidate.activityLabel.ifBlank { candidate.appLabel },
        hidden = hidden,
        status = {
            when {
                hidden -> NodeStatusBadge(
                    text = stringResource(R.string.text_menu_hidden),
                    container = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    content = MaterialTheme.colorScheme.error
                )
                rank != null -> NodeStatusBadge(
                    text = stringResource(R.string.detail_rank, rank),
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary
                )
                candidate.unavailable -> Text(
                    stringResource(R.string.system_entry_unobserved),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailing = {
            Switch(checked = hidden, onCheckedChange = { onToggle() })
        }
    )
}

@Composable
private fun NodeTileRow(surface: NodeSurface, component: RootComponent, vm: MainViewModel) {
    NodeCompactRow(
        surface = surface,
        title = component.label.takeIf { it.any(Char::isLetterOrDigit) }
            ?: component.component.className.substringAfterLast('.'),
        trailing = {
            Switch(
                checked = component.enabled == true,
                onCheckedChange = { vm.changeComponent(component, it) }
            )
        }
    )
}

/** Tiny status pill used for the hidden/ranked states of compact rows. */
@Composable
private fun NodeStatusBadge(text: String, container: Color, content: Color) {
    Text(
        text,
        modifier = Modifier
            .clip(AppShape.tile)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = content,
        maxLines = 1
    )
}

@Composable
private fun ViewAllInSurfaceLink(surface: NodeSurface, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(start = 8.dp)) {
        Text(
            stringResource(R.string.node_view_all_in_surface, stringResource(surface.titleRes)),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, Modifier.size(16.dp))
    }
}

/** Read-only clone-space rule row; the user badge is the only signal, edits stay in that space. */
@Composable
private fun NodeCloneRuleRow(userId: Int, rule: com.eio258.cleanchooser.domain.ComponentRule) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            stringResource(rule.kind.titleRes()),
            modifier = Modifier.weight(1f),
            style = CompactTitleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        NodeUserBadge(userId)
    }
}

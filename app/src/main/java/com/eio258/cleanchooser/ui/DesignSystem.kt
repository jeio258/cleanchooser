package com.eio258.cleanchooser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.domain.AppTypeFilter
import com.eio258.cleanchooser.domain.DisplayMode

internal val AppShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Shared shape/spacing tokens so every screen uses the same card and row geometry. */
internal object AppShape {
    val card = RoundedCornerShape(20.dp)
    val tile = RoundedCornerShape(12.dp)
    val image = RoundedCornerShape(10.dp)
}

@Composable
internal fun SegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.surface
                            else Color.Transparent
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShape.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        content = content
    )
}

/** The one application row used by rules, ordering, components and the home previews. */
@Composable
internal fun AppEntryRow(
    title: String,
    subtitle: String,
    icon: Bitmap?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    checkState: androidx.compose.ui.state.ToggleableState? = null,
    onCheck: (() -> Unit)? = null,
    rank: Int? = null,
    lockState: BulkLockState = BulkLockState.NONE,
    onLock: (() -> Unit)? = null,
    onUnlock: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    showChevron: Boolean = true,
) {
    val swipeable = if (onLock != null && onUnlock != null) {
        Modifier.bulkLockSwipe(onLock = onLock, onUnlock = onUnlock)
    } else {
        Modifier
    }
    Row(
        modifier.fillMaxWidth()
            .then(swipeable)
            .clickable(onClickLabel = stringResource(R.string.app_row_open_detail), onClick = onOpen)
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (rank != null) {
            // localization:ignore stable rank marker, not prose
            Text(
                "#$rank",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 2.dp)
            )
        }
        if (checkState != null) {
            SelectionMark(
                state = checkState,
                onClick = onCheck ?: {}
            )
        }
        AppIcon(bitmap = icon, appLabel = title, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (lockState != BulkLockState.NONE) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = stringResource(
                    if (lockState == BulkLockState.PARTIAL) R.string.bulk_lock_partial
                    else R.string.bulk_lock_full
                ),
                modifier = Modifier.size(18.dp),
                tint = if (lockState == BulkLockState.PARTIAL) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke(this)
        if (showChevron) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/** Compact circular selection mark shared by every row type. */
@Composable
internal fun SelectionMark(
    state: androidx.compose.ui.state.ToggleableState,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val on = state == androidx.compose.ui.state.ToggleableState.On
    val half = state == androidx.compose.ui.state.ToggleableState.Indeterminate
    val selectedDescription = stringResource(
        if (on || half) R.string.selection_mark_selected else R.string.selection_mark_not_selected
    )
    Box(
        modifier
            .size(40.dp)
            .semantics { stateDescription = selectedDescription }
            .then(
                if (onClick != null) Modifier.clickable(
                    enabled = enabled,
                    role = Role.Checkbox,
                    onClick = onClick
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(
                    when {
                        on -> colors.primary
                        half -> colors.primary.copy(alpha = 0.18f)
                        else -> Color.Transparent
                    },
                    CircleShape
                )
                .border(
                    1.5.dp,
                    when {
                        on || half -> colors.primary
                        else -> colors.outline
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                on -> Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colors.onPrimary
                )
                half -> Box(
                    Modifier.size(8.dp).background(colors.primary, CircleShape)
                )
            }
        }
    }
}

@Composable
internal fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Shared status banner: one status dot plus a title and a one-line summary. */
@Composable
internal fun StatusBanner(
    ok: Boolean,
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .background(
                if (ok) colors.secondaryContainer.copy(alpha = 0.5f) else colors.surfaceContainerHigh,
                AppShape.tile
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(7.dp).background(
                if (ok) colors.primary else colors.error,
                androidx.compose.foundation.shape.CircleShape
            )
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                summary,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

/** Shared section header: a bold title with an optional one-line hint. */
@Composable
internal fun SectionLabel(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier.fillMaxWidth()
            .padding(
                start = if (compact) 16.dp else 14.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = if (compact) 2.dp else 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = if (compact) MaterialTheme.typography.labelMedium
            else MaterialTheme.typography.titleSmall,
            fontWeight = if (compact) FontWeight.SemiBold else null,
            color = if (compact) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
        if (hint != null) {
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
}

@Composable
internal fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

/**
 * Shared resident global-mode pill with an inline dropdown for the three DisplayMode
 * values. AppsScreen keeps a private copy for now; new shells use this one.
 */
@Composable
internal fun ModePill(mode: DisplayMode, onChange: (DisplayMode) -> Unit) {
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

@Composable
internal fun AppTypeTrigger(
    current: AppTypeFilter,
    onSelect: (AppTypeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = current != AppTypeFilter.ALL
    Box(modifier) {
        Row(
            Modifier
                .clip(AppShape.tile)
                .background(
                    if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .clickable(role = Role.Button) { expanded = true }
                .heightIn(min = 36.dp)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(current.titleRes()),
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppTypeFilter.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.titleRes())) },
                    trailingIcon = if (mode == current) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    }
                )
            }
        }
    }
}

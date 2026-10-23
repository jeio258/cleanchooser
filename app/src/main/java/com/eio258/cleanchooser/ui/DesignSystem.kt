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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eio258.cleanchooser.R

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
    Box(
        modifier
            .size(40.dp)
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
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

@Composable
internal fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

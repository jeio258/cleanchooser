package com.eio258.cleanchooser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.domain.IntentKind

@Composable
internal fun AppRow(
    group: AppGroup,
    selected: Set<com.eio258.cleanchooser.domain.ComponentRule>,
    lockState: BulkLockState,
    onOpen: () -> Unit,
    onSelect: (Boolean) -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    rank: Int? = null,
) {
    val selectedCount = group.components.count { it.rule in selected }
    val selectionState = when {
        selectedCount == 0 -> ToggleableState.Off
        selectedCount == group.components.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val kindTitles = mutableListOf<String>()
    for (kind in IntentKind.entries) {
        if (group.components.any { it.rule.kind == kind }) kindTitles += stringResource(kind.titleRes())
    }
    AppEntryRow(
        title = group.appLabel,
        subtitle = stringResource(
            R.string.app_row_selection_summary,
            selectedCount,
            group.components.size,
            kindTitles.joinToString(" · ")
        ),
        icon = group.appIcon,
        checkState = selectionState,
        onCheck = { onSelect(selectionState != ToggleableState.On) },
        rank = rank,
        lockState = lockState,
        onLock = onLock,
        onUnlock = onUnlock,
        onOpen = onOpen
    )
}

@Composable
internal fun Modifier.bulkLockSwipe(
    enabled: Boolean = true,
    onLock: () -> Unit,
    onUnlock: () -> Unit
): Modifier {
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    return pointerInput(enabled, threshold, onLock, onUnlock) {
        if (enabled) {
            var totalDrag = 0f
            detectHorizontalDragGestures(
                onDragStart = { totalDrag = 0f },
                onHorizontalDrag = { change, amount ->
                    totalDrag += amount
                    change.consume()
                },
                onDragCancel = { totalDrag = 0f },
                onDragEnd = {
                    when {
                        totalDrag >= threshold -> onLock()
                        totalDrag <= -threshold -> onUnlock()
                    }
                    totalDrag = 0f
                }
            )
        }
    }
}

@Composable
internal fun AppIcon(bitmap: Bitmap?, appLabel: String, size: androidx.compose.ui.unit.Dp = 40.dp) {
    if (bitmap == null) {
        Icon(Icons.Rounded.Apps, null, Modifier.size(size))
        return
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.app_icon_description, appLabel),
        modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)),
        contentScale = ContentScale.Fit
    )
}

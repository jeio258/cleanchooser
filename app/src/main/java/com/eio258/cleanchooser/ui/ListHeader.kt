package com.eio258.cleanchooser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.domain.AppTypeFilter

private enum class FilterMenu { NONE, APP_TYPE, VIEW, SELECTION }

/**
 * Header shared by every workbench segment. The three filter entries stay visible with their
 * current value; tapping one opens a styled selection panel instead of the platform popup menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ListHeader(
    channels: List<T>,
    selectedChannel: T,
    channelLabel: @Composable (T) -> String,
    onChannel: (T) -> Unit,
    view: UiFilter,
    viewTitle: @Composable (UiFilter) -> String,
    onView: (UiFilter) -> Unit,
    appType: AppTypeFilter,
    onAppType: (AppTypeFilter) -> Unit,
    viewOptions: List<UiFilter> = UiFilter.entries,
    helpText: String? = null,
    loading: Boolean = false,
    extraFilter: (@Composable () -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    onSelectNone: (() -> Unit)? = null,
    onInvert: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(FilterMenu.NONE) }
    var helpOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(channels.size) { index ->
                val value = channels[index]
                FilterChip(
                    selected = value == selectedChannel,
                    onClick = { onChannel(value) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    label = {
                        Text(
                            channelLabel(value),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (value == selectedChannel) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    FilterTrigger(
                        label = stringResource(R.string.app_type_filter),
                        value = stringResource(appType.titleRes()),
                        active = appType != AppTypeFilter.ALL,
                        onClick = { menu = FilterMenu.APP_TYPE }
                    )
                }
                item {
                    FilterTrigger(
                        label = stringResource(R.string.view_filter),
                        value = viewTitle(view),
                        active = view != UiFilter.ALL,
                        onClick = { menu = FilterMenu.VIEW }
                    )
                }
                if (onSelectAll != null || onSelectNone != null || onInvert != null) {
                    item {
                        FilterTrigger(
                            label = stringResource(R.string.selection_actions),
                            value = null,
                            active = false,
                            onClick = { menu = FilterMenu.SELECTION }
                        )
                    }
                }
                extraFilter?.let { slot -> item { slot() } }
            }
            if (helpText != null) {
                IconButton(onClick = { helpOpen = true }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = stringResource(R.string.list_header_help),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        HorizontalDivider()

        if (menu != FilterMenu.NONE) {
            ModalBottomSheet(
                onDismissRequest = { menu = FilterMenu.NONE },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
                    Text(
                        when (menu) {
                            FilterMenu.APP_TYPE -> stringResource(R.string.app_type_filter)
                            FilterMenu.VIEW -> stringResource(R.string.view_filter)
                            else -> stringResource(R.string.selection_actions)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    when (menu) {
                        FilterMenu.APP_TYPE -> AppTypeFilter.entries.forEach { mode ->
                            FilterSheetRow(
                                label = stringResource(mode.titleRes()),
                                selected = mode == appType,
                                onClick = { onAppType(mode); menu = FilterMenu.NONE }
                            )
                        }
                        FilterMenu.VIEW -> viewOptions.forEach { mode ->
                            FilterSheetRow(
                                label = viewTitle(mode),
                                selected = mode == view,
                                onClick = { onView(mode); menu = FilterMenu.NONE }
                            )
                        }
                        else -> {
                            onSelectAll?.let { action ->
                                FilterSheetRow(stringResource(R.string.select_all), null) {
                                    action(); menu = FilterMenu.NONE
                                }
                            }
                            onSelectNone?.let { action ->
                                FilterSheetRow(stringResource(R.string.select_none), null) {
                                    action(); menu = FilterMenu.NONE
                                }
                            }
                            onInvert?.let { action ->
                                FilterSheetRow(stringResource(R.string.invert_selection), null) {
                                    action(); menu = FilterMenu.NONE
                                }
                            }
                        }
                    }
                }
            }
        }

        if (helpOpen && helpText != null) {
            ModalBottomSheet(
                onDismissRequest = { helpOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                    Text(
                        stringResource(R.string.list_header_help),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        helpText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterTrigger(
    label: String,
    value: String?,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(AppShape.tile)
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (value == null) label else "$label · $value",
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.padding(start = 2.dp).size(14.dp),
            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FilterSheetRow(label: String, selected: Boolean?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected == true) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Spacer(Modifier.size(18.dp))
        }
        Text(
            label,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected == true) FontWeight.Medium else FontWeight.Normal,
            color = if (selected == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

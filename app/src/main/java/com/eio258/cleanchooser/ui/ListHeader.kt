package com.eio258.cleanchooser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/**
 * Header shared by every rule/priority/component segment.
 *
 * One control row only: a "Filters" trigger carrying the active-filter count, the segment's own
 * extra trigger (open-type preset / browser host), and a horizontal strip of removable summary
 * chips for the filters actually in effect. The full filter surface - entry type (up to 36
 * values), view, app type and the batch actions - lives in a single bottom sheet, so the list
 * keeps its space no matter how many entry kinds exist.
 *
 * Segment state stays owned by the caller through [onChannel]/[onView]/[onAppType]; nothing here
 * survives recomposition except the sheet/query toggles.
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
    openSheetSignal: Int = 0,
) {
    // Labels are resolved once per composition so the sheet can filter on plain strings.
    val labels = channels.map { channelLabel(it) }
    val fallbackChannel = channels.firstOrNull() ?: selectedChannel
    val channelActive = selectedChannel != fallbackChannel
    val viewActive = view != UiFilter.ALL
    val appTypeActive = appType != AppTypeFilter.ALL
    val activeCount = (if (channelActive) 1 else 0) + (if (viewActive) 1 else 0) +
        (if (appTypeActive) 1 else 0)
    val hasBatch = onSelectAll != null || onSelectNone != null || onInvert != null

    var sheetOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var channelQuery by remember { mutableStateOf("") }

    // The collapsed shell toolbar routes its filter button through this local signal, so the
    // six segments keep their original call signatures.
    val shellSignal = LocalFilterSheetSignal.current
    val effectiveSignal = if (openSheetSignal > 0) openSheetSignal else shellSignal
    if (effectiveSignal > 0) {
        LaunchedEffect(effectiveSignal) { sheetOpen = true }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterTrigger(
                label = stringResource(R.string.filter_action),
                value = activeCount.takeIf { it > 0 }?.toString(),
                active = activeCount > 0,
                onClick = { sheetOpen = true }
            )
            extraFilter?.let { slot -> slot() }
            if (activeCount > 0) {
                LazyRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (channelActive) {
                        item {
                            ActiveFilterChip(
                                text = labels.getOrNull(channels.indexOf(selectedChannel))
                                    ?: stringResource(R.string.filter_action),
                                hint = stringResource(R.string.filter_section_channel),
                                onRemove = { onChannel(fallbackChannel) }
                            )
                        }
                    }
                    if (viewActive) {
                        item {
                            ActiveFilterChip(
                                text = viewTitle(view),
                                hint = stringResource(R.string.view_filter),
                                onRemove = { onView(UiFilter.ALL) }
                            )
                        }
                    }
                    if (appTypeActive) {
                        item {
                            ActiveFilterChip(
                                text = stringResource(appType.titleRes()),
                                hint = stringResource(R.string.app_type_filter),
                                onRemove = { onAppType(AppTypeFilter.ALL) }
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
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

        if (sheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { sheetOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 18.dp)
                ) {
                    Text(
                        stringResource(R.string.filter_action),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    if (channels.size > 1) {
                        SheetSectionLabel(stringResource(R.string.filter_section_channel))
                        if (channels.size > 10) {
                            OutlinedTextField(
                                value = channelQuery,
                                onValueChange = { channelQuery = it },
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.filter_search_hint)) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            )
                        }
                        channels.forEachIndexed { index, value ->
                            val label = labels[index]
                            if (channelQuery.isBlank() ||
                                label.contains(channelQuery, ignoreCase = true)
                            ) {
                                FilterSheetRow(
                                    label = label,
                                    selected = value == selectedChannel,
                                    onClick = { onChannel(value) }
                                )
                            }
                        }
                    }

                    if (viewOptions.size > 1) {
                        SheetSectionLabel(stringResource(R.string.view_filter))
                        viewOptions.forEach { mode ->
                            FilterSheetRow(
                                label = viewTitle(mode),
                                selected = mode == view,
                                onClick = { onView(mode) }
                            )
                        }
                    }

                    if (AppTypeFilter.entries.size > 1) {
                        SheetSectionLabel(stringResource(R.string.app_type_filter))
                        AppTypeFilter.entries.forEach { mode ->
                            FilterSheetRow(
                                label = stringResource(mode.titleRes()),
                                selected = mode == appType,
                                onClick = { onAppType(mode) }
                            )
                        }
                    }

                    if (hasBatch) {
                        SheetSectionLabel(stringResource(R.string.selection_actions))
                        onSelectAll?.let { action ->
                            FilterSheetRow(stringResource(R.string.select_all), null) {
                                action(); sheetOpen = false
                            }
                        }
                        onSelectNone?.let { action ->
                            FilterSheetRow(stringResource(R.string.select_none), null) {
                                action(); sheetOpen = false
                            }
                        }
                        onInvert?.let { action ->
                            FilterSheetRow(stringResource(R.string.invert_selection), null) {
                                action(); sheetOpen = false
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { sheetOpen = false }) {
                            Text(stringResource(R.string.filter_done))
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

/** A filter currently in effect; tapping the chip clears it. */
@Composable
private fun ActiveFilterChip(text: String, hint: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(AppShape.tile)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(role = Role.Button, onClick = onRemove)
            .heightIn(min = 36.dp)
            .padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.Rounded.Close,
            contentDescription = stringResource(R.string.filter_remove, hint),
            modifier = Modifier.padding(start = 4.dp).size(14.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
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
        Icon(
            Icons.Rounded.Tune,
            contentDescription = null,
            modifier = Modifier.padding(end = 5.dp).size(14.dp),
            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
    )
}

@Composable
private fun FilterSheetRow(label: String, selected: Boolean?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppShape.tile)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected == true) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected == true) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (selected == true) {
            Text(
                stringResource(R.string.filter_selected_mark),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

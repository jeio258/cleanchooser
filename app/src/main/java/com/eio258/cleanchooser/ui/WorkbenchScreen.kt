package com.eio258.cleanchooser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eio258.cleanchooser.R
import kotlinx.coroutines.delay

/**
 * Lets the shell ask the visible segment header to open its unified filter sheet, without
 * hoisting every filter field up through the six segment composables.
 */
internal val LocalFilterSheetSignal = compositionLocalOf { 0 }

/**
 * Entry-view shell.
 *
 * Expanded: face chips, search field, mode and space controls. As soon as the list is scrolled
 * down the header collapses to a single command bar - face name as a dropdown, search, filters
 * and the space pill - giving the list roughly 90dp more height. Scrolling back up restores the
 * full header. The six segment bodies are untouched: they own their lists, batch actions and
 * persistence.
 */
@Composable
fun WorkbenchScreen(state: MainState, vm: MainViewModel) {
    val spaces by vm.spaceSelectorItems.collectAsStateWithLifecycle()
    val activeSpace by vm.activeSpaceId.collectAsStateWithLifecycle()
    val collapse = remember { HeaderCollapse() }
    var filterSignal by remember { mutableIntStateOf(0) }
    var faceMenu by remember { mutableStateOf(false) }
    var wantSearchFocus by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    // Filter signal is a one-shot request. Resetting it on a face switch keeps the leftover
    // value from opening the *next* face's filter sheet on its first composition.
    val selectFace: (WorkbenchTab) -> Unit = { tab ->
        filterSignal = 0
        faceMenu = false
        wantSearchFocus = false
        vm.selectWorkbenchTab(tab)
    }

    Column(Modifier.fillMaxSize().nestedScroll(collapse.connection)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .clip(AppShape.tile)
                        .then(
                            if (collapse.collapsed) {
                                Modifier.clickable(role = Role.Button) { faceMenu = true }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(vm.workbenchTab.labelRes()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (collapse.collapsed) {
                        Icon(
                            Icons.Rounded.ArrowDropDown,
                            contentDescription = stringResource(R.string.workbench_switch_face),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DropdownMenu(expanded = faceMenu, onDismissRequest = { faceMenu = false }) {
                    WorkbenchTab.entries.forEach { tab ->
                        DropdownMenuItem(
                            text = { Text(stringResource(tab.labelRes())) },
                            onClick = { selectFace(tab) }
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !collapse.collapsed) {
                ModePill(mode = state.displayMode, onChange = vm::setDisplayMode)
            }
            AnimatedVisibility(visible = collapse.collapsed) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = {
                            collapse.expand()
                            wantSearchFocus = true
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.search_hint),
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // The sheet covers the whole screen, so the header stays collapsed while
                    // it is open; closing it returns straight to the compact list.
                    IconButton(
                        onClick = { filterSignal += 1 },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.filter_action),
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            SpaceSelectorBar(
                spaces = spaces,
                selectedUserId = activeSpace,
                onSelect = vm::switchSpace
            )
            IconButton(
                onClick = {
                    if (vm.workbenchTab == WorkbenchTab.COMPONENTS) vm.refreshComponents()
                    else vm.refresh(forceCatalog = true)
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.logs_refresh),
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = !collapse.collapsed,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                CompactSearchField(
                    query = state.query,
                    onQueryChange = vm::setQuery,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    focusRequester = searchFocus
                )
                FaceChipsRow(
                    selectedTab = vm.workbenchTab,
                    onSelect = { selectFace(WorkbenchTab.entries[it]) },
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }

        // Expanding from the collapsed bar focuses the field once the header is laid out.
        LaunchedEffect(wantSearchFocus, collapse.collapsed) {
            if (wantSearchFocus && !collapse.collapsed) {
                delay(160)
                runCatching { searchFocus.requestFocus() }
                wantSearchFocus = false
            }
        }

        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalFilterSheetSignal provides filterSignal) {
                when (vm.workbenchTab) {
                    WorkbenchTab.RULES -> RulesList(
                        state,
                        vm,
                        vm.workbenchFilters,
                        onDetail = vm::openDetail
                    )
                    WorkbenchTab.PRIORITY -> PriorityList(
                        state,
                        vm,
                        vm.workbenchFilters,
                        onDetail = vm::openDetail
                    )
                    WorkbenchTab.COMPONENTS -> ComponentsList(
                        state,
                        vm,
                        vm.workbenchFilters,
                        onDetail = vm::openDetail
                    )
                    WorkbenchTab.TEXT -> TextMenuScreen(state, vm)
                    WorkbenchTab.DIRECT_SHARE -> DirectShareScreen(state, vm)
                    WorkbenchTab.SYSTEM_ENTRY -> SystemEntryScreen(state, vm)
                }
            }
        }
    }
}

/**
 * Collapse state driven by the list's own scroll, so no segment has to report its scroll
 * position. A downward drag past the threshold collapses; an upward drag of a few dp expands
 * again. The accumulator resets on every transition to keep the two thresholds independent.
 */
private class HeaderCollapse {
    var collapsed by mutableStateOf(false)
        private set

    private var acc = 0f

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val dy = consumed.y
            if (dy == 0f) return Offset.Zero
            acc = (acc + dy).coerceIn(-260f, 260f)
            if (!collapsed && acc <= -36f) {
                collapsed = true
                acc = 0f
            } else if (collapsed && acc >= 14f) {
                collapsed = false
                acc = 0f
            }
            return Offset.Zero
        }
    }

    fun expand() {
        if (collapsed) {
            collapsed = false
            acc = 0f
        }
    }
}

/**
 * Face selector as a horizontally scrollable chip row (replaces the six-segment SegmentedTabs).
 * The active face is highlighted with the primary container color, matching the chip style used
 * by the shared list headers.
 */
@Composable
private fun FaceChipsRow(
    selectedTab: WorkbenchTab,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(WorkbenchTab.entries) { index, tab ->
            val selected = tab == selectedTab
            FilterChip(
                selected = selected,
                onClick = { onSelect(index) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                label = {
                    Text(
                        stringResource(tab.labelRes()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

package com.eio258.cleanchooser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eio258.cleanchooser.R
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.Icons
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Entry-view shell aligned with the AppsScreen header: face title with the global mode
 * pill and space pill, a resident search field, and a horizontally scrollable row of
 * face chips. The six face bodies (RulesList, PriorityList, ComponentsList,
 * TextMenuScreen, DirectShareScreen, SystemEntryScreen) keep their own filter headers.
 * The legacy MainToolbar search in MainActivity is retained for other destinations.
 */
@Composable
fun WorkbenchScreen(state: MainState, vm: MainViewModel) {
    val spaces by vm.spaceSelectorItems.collectAsStateWithLifecycle()
    val activeSpace by vm.activeSpaceId.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(vm.workbenchTab.labelRes()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            ModePill(mode = state.displayMode, onChange = vm::setDisplayMode)
            SpaceSelectorBar(
                spaces = spaces,
                selectedUserId = activeSpace,
                onSelect = vm::switchSpace
            )
            IconButton(onClick = {
                if (vm.workbenchTab == WorkbenchTab.COMPONENTS) vm.refreshComponents()
                else vm.refresh(forceCatalog = true)
            }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.logs_refresh))
            }
        }
        CompactSearchField(
            query = state.query,
            onQueryChange = vm::setQuery,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        FaceChipsRow(
            selectedTab = vm.workbenchTab,
            onSelect = { vm.selectWorkbenchTab(WorkbenchTab.entries[it]) },
            modifier = Modifier.padding(vertical = 6.dp)
        )
        Box(Modifier.weight(1f)) {
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

/**
 * Face selector as a horizontally scrollable chip row (replaces the six-segment
 * SegmentedTabs). The active face is highlighted with the primary container color,
 * matching the chip style used by the shared list headers.
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

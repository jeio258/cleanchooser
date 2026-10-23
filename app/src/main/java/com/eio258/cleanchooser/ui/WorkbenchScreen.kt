package com.eio258.cleanchooser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Workbench shell: three segments over the shared list pages. The top bar (with the
 * always-visible search field) and the bottom navigation live in MainActivity.
 */
@Composable
fun WorkbenchScreen(state: MainState, vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        SegmentedTabs(
            labels = WorkbenchTab.entries.map { stringResource(it.labelRes()) },
            selectedIndex = vm.workbenchTab.ordinal,
            onSelect = { vm.selectWorkbenchTab(WorkbenchTab.entries[it]) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
            }
        }
    }
}

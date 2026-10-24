package com.eio258.cleanchooser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.data.CleanupKind
import com.eio258.cleanchooser.data.RootComponent
import com.eio258.cleanchooser.domain.ComponentCandidate
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.matchesBrowserHost
import com.eio258.cleanchooser.domain.matchesOpenPreset

private enum class DetailSegment { ALL, CHECKED, UNCHECKED }

/**
 * App detail page of the master/detail workbench layout.
 *
 * The top bar (back arrow + title) and the hidden bottom navigation are owned by MainActivity,
 * so this screen starts directly with its content. The context (rules, ordering or components)
 * follows the currently selected workbench segment, and every selection/lock primitive is the
 * same one the list pages use, so both views stay consistent.
 */
@Composable
fun AppDetailScreen(packageName: String, state: MainState, vm: MainViewModel) {
    val filters = vm.workbenchFilters
    val bulkLockRevision by vm.bulkLockRevision.collectAsStateWithLifecycle()
    val scan by vm.componentScan.collectAsStateWithLifecycle()
    val busy by vm.componentBusy.collectAsStateWithLifecycle()
    val componentsContext = vm.workbenchTab == WorkbenchTab.COMPONENTS

    var segment by rememberSaveable(
        packageName, componentsContext, filters.componentKind,
        filters.channel, filters.openPreset, filters.browserHost
    ) {
        mutableStateOf(if (componentsContext) DetailSegment.ALL else DetailSegment.UNCHECKED)
    }

    val ruleCandidate = state.candidates.firstOrNull { it.rule.packageName == packageName }
    val componentItem = scan.items.firstOrNull { it.component.packageName == packageName }
    val headerLabel = (if (componentsContext) componentItem?.owner else ruleCandidate?.appLabel)
        ?: ruleCandidate?.appLabel
        ?: componentItem?.owner
    val headerIcon = (if (componentsContext) componentItem?.icon else ruleCandidate?.appIcon)
        ?: ruleCandidate?.appIcon
        ?: componentItem?.icon

    Column(Modifier.fillMaxSize()) {
        DetailHeader(packageName, headerLabel, headerIcon)
        if (componentsContext) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                TextButton(onClick = { vm.restoreComponentsForApp(packageName) }) {
                    Text(stringResource(R.string.detail_restore_components))
                }
            }
            ComponentsDetailBody(
                packageName = packageName,
                vm = vm,
                kind = filters.componentKind,
                componentItems = scan.items.filter {
                    it.component.packageName == packageName &&
                        (filters.componentKind == null || it.kind == filters.componentKind)
                },
                busy = busy,
                bulkLockRevision = bulkLockRevision,
                segment = segment,
                onSegment = { segment = it }
            )
        } else {
            RulesDetailBody(
                packageName = packageName,
                state = state,
                vm = vm,
                filters = filters,
                showRanking = vm.workbenchTab == WorkbenchTab.PRIORITY,
                bulkLockRevision = bulkLockRevision,
                segment = segment,
                onSegment = { segment = it }
            )
        }
    }
}

@Composable
private fun DetailHeader(packageName: String, label: String?, icon: Bitmap?) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(bitmap = icon, appLabel = label ?: packageName)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                label ?: stringResource(R.string.detail_app_unknown),
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
        }
    }
}

@Composable
private fun DetailStats(candidates: Int, checked: Int, checkedLabel: String, locked: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatBlock(candidates.toString(), stringResource(R.string.detail_stat_candidates), Modifier.weight(1f))
        StatBlock(checked.toString(), checkedLabel, Modifier.weight(1f))
        StatBlock(locked.toString(), stringResource(R.string.detail_stat_locked), Modifier.weight(1f))
    }
}

@Composable
private fun StatBlock(value: String, label: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailSegments(
    entries: List<DetailSegment>,
    labels: List<String>,
    selected: DetailSegment,
    onSelect: (DetailSegment) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        entries.forEachIndexed { index, entry ->
            FilterChip(
                selected = entry == selected,
                onClick = { onSelect(entry) },
                label = { Text(labels[index]) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DetailActionBar(
    selectAllEnabled: Boolean,
    invertEnabled: Boolean,
    lockEnabled: Boolean,
    appLocked: Boolean,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onToggleAppLock: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onSelectAll, enabled = selectAllEnabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.select_all))
            }
            TextButton(onClick = onInvert, enabled = invertEnabled, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.invert_selection))
            }
            TextButton(onClick = onToggleAppLock, enabled = lockEnabled, modifier = Modifier.weight(1f)) {
                Icon(
                    if (appLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(
                        if (appLocked) R.string.detail_unlock_app else R.string.detail_lock_app
                    )
                )
            }
        }
    }
}

@Composable
private fun RankRow(
    rank: Int?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            rank?.let { stringResource(R.string.detail_rank, it) }
                ?: stringResource(R.string.detail_not_ranked),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = if (rank == null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary
        )
        TextButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp))
            Text(stringResource(R.string.priority_move_up))
        }
        TextButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp))
            Text(stringResource(R.string.priority_move_down))
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColumnScope.RulesDetailBody(
    packageName: String,
    state: MainState,
    vm: MainViewModel,
    filters: WorkbenchFilters,
    showRanking: Boolean,
    bulkLockRevision: Long,
    segment: DetailSegment,
    onSegment: (DetailSegment) -> Unit
) {
    val channel = filters.channel
    val openPreset = filters.openPreset.takeIf { channel == IntentKind.OPEN }
    val browserHost = filters.browserHost.takeIf { channel == IntentKind.DEEP_LINK }
    val deepLinkScoped = browserHost != null
    val lockScope = browserHost?.let(::browserRuleBulkLockScope)
        ?: ruleBulkLockScope(channel, openPreset)

    val typedSelected = openPreset?.let { state.openTypes.selectedRules(it) }.orEmpty()
    val genericDeepLinkSelected = remember(state.selected) {
        state.selected.filterTo(linkedSetOf()) { it.kind == IntentKind.DEEP_LINK }
    }
    val explicitDeepLinkSelected = browserHost?.let { state.browserLinks.selectedRules(it) }.orEmpty()
    val deepLinkSelected = genericDeepLinkSelected + explicitDeepLinkSelected
    val activeSelected = when {
        openPreset != null -> typedSelected
        browserHost != null -> deepLinkSelected
        else -> state.selected
    }
    val customDefinitions = state.openTypesExplicit.customDefinitions
    val appCandidates = remember(state.candidates, packageName) {
        state.candidates.filter { it.rule.packageName == packageName }
    }
    val scopedCandidates = remember(appCandidates, channel, openPreset, browserHost, deepLinkSelected, customDefinitions) {
        appCandidates.filter { candidate ->
            when {
                openPreset != null -> candidate.matchesOpenPreset(openPreset, customDefinitions)
                browserHost != null -> candidate.rule.kind == IntentKind.DEEP_LINK &&
                    (candidate.matchesBrowserHost(browserHost) || candidate.rule in deepLinkSelected)
                channel != null -> candidate.rule.kind == channel
                else -> true
            }
        }
    }
    val scopeRules = scopedCandidates.map { it.rule }

    val lockedRules = remember(scopedCandidates, bulkLockRevision, channel, openPreset, lockScope, deepLinkScoped) {
        scopedCandidates.associate { candidate ->
            candidate.rule.id to if (deepLinkScoped) {
                vm.isBulkProtected(lockScope, packageName, candidate.rule.id)
            } else {
                vm.isRuleBulkProtected(channel, openPreset, candidate.rule)
            }
        }
    }
    val appLocked = remember(scopeRules, bulkLockRevision, channel, openPreset, lockScope, deepLinkScoped) {
        if (deepLinkScoped) vm.isBulkAppLocked(lockScope, packageName)
        else vm.isRuleAppLocked(channel, openPreset, packageName, scopeRules)
    }

    val rankingKind = channel ?: IntentKind.SHARE
    val rankedRaw = when {
        openPreset != null -> state.openTypes.priorities[openPreset].orEmpty()
        browserHost != null -> state.browserLinks.priority(browserHost)
            .ifEmpty { state.priorities.apps[IntentKind.DEEP_LINK].orEmpty() }
        else -> state.priorities.apps[rankingKind].orEmpty()
    }
    val rankIndex = rankedRaw.indexOf(packageName)
    val rank = if (rankIndex >= 0) rankIndex + 1 else null

    val visible = scopedCandidates.filter { candidate ->
        when (segment) {
            DetailSegment.ALL -> true
            DetailSegment.CHECKED -> candidate.rule in activeSelected
            DetailSegment.UNCHECKED -> candidate.rule !in activeSelected
        }
    }
    val checkedCount = scopedCandidates.count { it.rule in activeSelected }

    var autoPicked by remember(packageName, channel, openPreset, browserHost) { mutableStateOf(false) }
    LaunchedEffect(scopedCandidates, activeSelected) {
        if (!autoPicked && scopedCandidates.isNotEmpty()) {
            autoPicked = true
            val anyChecked = scopedCandidates.any { it.rule in activeSelected }
            onSegment(if (anyChecked) DetailSegment.CHECKED else DetailSegment.UNCHECKED)
        }
    }

    DetailStats(
        candidates = scopedCandidates.size,
        checked = checkedCount,
        checkedLabel = stringResource(R.string.detail_stat_selected),
        locked = lockedRules.count { it.value }
    )
    DetailSegments(
        entries = listOf(DetailSegment.CHECKED, DetailSegment.UNCHECKED),
        labels = listOf(
            stringResource(R.string.common_selected),
            stringResource(R.string.common_not_selected)
        ),
        selected = segment,
        onSelect = onSegment
    )

    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
        if (showRanking) {
            item(key = "rank") {
                RankRow(
                    rank = rank,
                    canMoveUp = rankIndex > 0,
                    canMoveDown = rankIndex in 0 until rankedRaw.lastIndex,
                    onMoveUp = {
                        when {
                            openPreset != null ->
                                vm.moveOpenTypePriority(openPreset, packageName, -1, rankedRaw)
                            browserHost != null ->
                                vm.moveBrowserHostPriority(browserHost, packageName, -1, rankedRaw)
                            else -> vm.movePriority(rankingKind, packageName, -1, rankedRaw)
                        }
                    },
                    onMoveDown = {
                        when {
                            openPreset != null ->
                                vm.moveOpenTypePriority(openPreset, packageName, 1, rankedRaw)
                            browserHost != null ->
                                vm.moveBrowserHostPriority(browserHost, packageName, 1, rankedRaw)
                            else -> vm.movePriority(rankingKind, packageName, 1, rankedRaw)
                        }
                    }
                )
                HorizontalDivider()
            }
        }
        items(visible, key = { it.rule.id }, contentType = { "rule" }) { candidate ->
            val locked = lockedRules[candidate.rule.id] == true
            val toggleLock: (Boolean) -> Unit = { next ->
                if (deepLinkScoped) {
                    vm.setBulkItemLocked(lockScope, candidate.rule.id, next)
                } else {
                    vm.setRuleItemLocked(channel, openPreset, candidate.rule, next)
                }
            }
            RuleDetailRow(
                item = candidate,
                checked = candidate.rule in activeSelected,
                customTitle = state.priorities.titles[candidate.rule.id],
                locked = locked,
                lockToggleEnabled = !appLocked,
                onToggle = {
                    when {
                        openPreset != null -> vm.toggleOpenType(openPreset, candidate.rule)
                        browserHost != null -> vm.toggleBrowserHost(browserHost, candidate.rule)
                        else -> vm.toggle(candidate.rule)
                    }
                },
                onLockChange = toggleLock
            )
        }
        if (visible.isEmpty()) {
            item(key = "empty") { EmptyHint(stringResource(R.string.detail_empty_list)) }
        }
    }

    DetailActionBar(
        selectAllEnabled = visible.isNotEmpty(),
        invertEnabled = visible.isNotEmpty(),
        lockEnabled = scopeRules.isNotEmpty(),
        appLocked = appLocked,
        onSelectAll = {
            val rules = visible.map { it.rule }
            when {
                openPreset != null -> vm.selectOpenTypeRules(openPreset, rules, lockScope)
                browserHost != null -> vm.selectBrowserHostRules(browserHost, rules, lockScope)
                else -> vm.selectRules(rules, channel, openPreset)
            }
        },
        onInvert = {
            val rules = visible.map { it.rule }
            when {
                openPreset != null -> vm.invertOpenTypeRules(openPreset, rules, lockScope)
                browserHost != null -> vm.invertBrowserHostRules(browserHost, rules, lockScope)
                else -> vm.invertRules(rules, channel, openPreset)
            }
        },
        onToggleAppLock = {
            val target = !appLocked
            if (deepLinkScoped) {
                vm.setBulkAppLocked(lockScope, packageName, scopeRules.map { it.id }, target)
            } else {
                vm.setRuleAppLocked(channel, openPreset, packageName, scopeRules, target)
            }
        }
    )
}

@Composable
private fun RuleDetailRow(
    item: ComponentCandidate,
    checked: Boolean,
    customTitle: String?,
    locked: Boolean,
    lockToggleEnabled: Boolean,
    onToggle: () -> Unit,
    onLockChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .bulkLockSwipe(
                enabled = lockToggleEnabled,
                onLock = { onLockChange(true) },
                onUnlock = { onLockChange(false) }
            )
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 56.dp)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectionMark(
            state = if (checked) androidx.compose.ui.state.ToggleableState.On
            else androidx.compose.ui.state.ToggleableState.Off,
            modifier = Modifier.padding(8.dp)
        )
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(item.activityLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!customTitle.isNullOrBlank()) {
                Text(
                    stringResource(R.string.component_shown_as, customTitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                item.rule.className,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.unavailable) {
                Text(
                    stringResource(R.string.component_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (item.restricted) {
                Text(
                    stringResource(R.string.component_restricted),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LockButton(locked = locked, enabled = lockToggleEnabled, onLockChange = onLockChange)
    }
    HorizontalDivider()
}

@Composable
private fun LockButton(locked: Boolean, enabled: Boolean, onLockChange: (Boolean) -> Unit) {
    IconButton(onClick = { onLockChange(!locked) }, enabled = enabled) {
        Icon(
            if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
            contentDescription = stringResource(
                if (locked) R.string.detail_unlock_item else R.string.detail_lock_item
            ),
            tint = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColumnScope.ComponentsDetailBody(
    packageName: String,
    vm: MainViewModel,
    kind: CleanupKind?,
    componentItems: List<RootComponent>,
    busy: Boolean,
    bulkLockRevision: Long,
    segment: DetailSegment,
    onSegment: (DetailSegment) -> Unit
) {
    val appId = componentItems.firstOrNull()?.let(::componentBulkLockAppId) ?: packageName
    val lockedItems = remember(componentItems, bulkLockRevision, kind) {
        componentItems.associate { it.id to vm.isComponentBulkProtected(kind, it) }
    }
    val appLocked = remember(componentItems, bulkLockRevision, kind) {
        componentItems.firstOrNull()?.let { vm.isComponentAppLockedForItem(kind, it) } ?: false
    }
    val disabledCount = componentItems.count { it.blocked == null && it.enabled == false }

    val visible = componentItems.filter { item ->
        when (segment) {
            DetailSegment.ALL -> true
            DetailSegment.CHECKED -> item.enabled == false && item.blocked == null
            DetailSegment.UNCHECKED -> item.enabled != false
        }
    }

    DetailStats(
        candidates = componentItems.size,
        checked = disabledCount,
        checkedLabel = stringResource(R.string.detail_stat_disabled),
        locked = lockedItems.count { it.value }
    )
    DetailSegments(
        entries = DetailSegment.entries,
        labels = listOf(
            stringResource(R.string.common_all),
            stringResource(R.string.detail_disabled),
            stringResource(R.string.detail_enabled)
        ),
        selected = segment,
        onSelect = onSegment
    )

    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
        items(visible, key = { it.id }, contentType = { "component" }) { item ->
            val editable = !busy && item.blocked == null && item.enabled != null
            val checked = item.enabled == false
            val setLocked: (Boolean) -> Unit = { next -> vm.setComponentItemLocked(kind, item, next) }
            Row(
                Modifier.fillMaxWidth()
                    .bulkLockSwipe(
                        enabled = editable && !appLocked,
                        onLock = { setLocked(true) },
                        onUnlock = { setLocked(false) }
                    )
                    .toggleable(
                        value = checked,
                        enabled = editable,
                        role = Role.Checkbox,
                        onValueChange = { next -> vm.changeComponent(item, !next) }
                    )
                    .heightIn(min = 56.dp)
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionMark(
                    state = if (checked) androidx.compose.ui.state.ToggleableState.On
                    else androidx.compose.ui.state.ToggleableState.Off,
                    enabled = editable,
                    modifier = Modifier.padding(8.dp)
                )
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.component.flattenToShortString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.blocked ?: stringResource(item.kind.titleRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.blocked != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LockButton(
                    locked = lockedItems[item.id] == true,
                    enabled = editable && !appLocked,
                    onLockChange = setLocked
                )
            }
            HorizontalDivider()
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                EmptyHint(
                    stringResource(
                        if (componentItems.isEmpty()) R.string.detail_empty_components else R.string.detail_empty_list
                    )
                )
            }
        }
    }

    DetailActionBar(
        selectAllEnabled = !busy && visible.any { it.blocked == null && it.enabled != null },
        invertEnabled = !busy && visible.any { it.blocked == null && it.enabled != null },
        lockEnabled = componentItems.isNotEmpty(),
        appLocked = appLocked,
        onSelectAll = { vm.changeComponentsBulk(kind, visible, enable = false) },
        onInvert = { vm.invertComponentsBulk(kind, visible) },
        onToggleAppLock = { vm.setComponentAppLocked(kind, appId, componentItems, !appLocked) }
    )
}

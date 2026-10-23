package com.eio258.cleanchooser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.domain.ComponentCandidate
import com.eio258.cleanchooser.domain.IntentKind

/**
 * Text selection menu workspace: the observed system menu on top, the manageable
 * PROCESS_TEXT extensions below. Hidden entries stay in the list so they can be restored.
 */
@Composable
fun TextMenuScreen(state: MainState, vm: MainViewModel) {
    val order by vm.textMenuOrder.collectAsStateWithLifecycle()
    val observed = state.runtime.textMenuObserved
    val systemItems = state.runtime.textMenu.filter { it.fixed }
    val observedExt = state.runtime.textMenu.filter { !it.fixed }
    val candidates = state.candidates.filter { it.rule.kind == IntentKind.PROCESS_TEXT }
    val observedComponents = observedExt.mapNotNull { it.component }.toSet()

    val rows = remember(candidates, order, observedExt, state.selected) {
        buildExtensionRows(candidates, order, observedExt, state.selected, observedComponents)
    }
    val hiddenCount = rows.count { it.hidden }
    val draggableIds = rows.filter { !it.hidden }.map { it.component }

    val listState = rememberLazyListState()
    val dragState = remember(listState) { PriorityDragState(listState) }
    val drag = dragState.session
    val currentVisible by rememberUpdatedState(draggableIds)
    val currentSaved by rememberUpdatedState(rows.map { it.component })
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val speed = with(density) { 640.dp.toPx() }

    LaunchedEffect(order, rows) { dragState.cancel() }
    LaunchedEffect(drag?.packageName) {
        if (dragState.session != null) {
            var previous = withFrameNanos { it }
            while (dragState.session != null) {
                val now = withFrameNanos { it }
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
                previous = now
                val delta = dragState.scrollSpeed(edge, speed) * seconds
                if (delta != 0f) {
                    listState.scrollBy(delta)
                    dragState.retarget()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth()
                .pointerInput(rows, draggableIds) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { position ->
                            if (draggableIds.size >= 2 &&
                                dragState.start(position.y, "text", draggableIds, currentSaved)
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, amount ->
                            if (dragState.session != null) {
                                change.consume()
                                dragState.move(amount.y)
                            }
                        },
                        onDragCancel = { dragState.cancel() },
                        onDragEnd = {
                            dragState.finish()?.let { finished ->
                                val ids = draggableIds.toMutableList()
                                val from = ids.indexOf(finished.packageName)
                                val to = ids.indexOf(finished.target)
                                if (from >= 0 && to >= 0 && from != to) {
                                    val moved = ids.removeAt(from)
                                    ids.add(to, moved)
                                }
                                val byComponent = rows.associateBy { it.component }
                                val hiddenIds = rows.filter { it.hidden }.mapNotNull { it.ruleId }
                                vm.setTextMenuOrder(
                                    ids.mapNotNull { byComponent[it]?.ruleId } + hiddenIds
                                )
                            }
                        }
                    )
                },
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
        ) {
            item(key = "status") {
                StatusBanner(
                    observed = observed,
                    ready = state.runtime.ready,
                    hiddenCount = hiddenCount
                )
            }

            if (systemItems.isNotEmpty()) {
                item(key = "sys-header") {
                    SectionLabel(
                        stringResource(R.string.text_menu_system_section),
                        stringResource(R.string.text_menu_system_hint)
                    )
                }
                items(systemItems.size, key = { "sys|$it" }) { index ->
                    val item = systemItems[index]
                    SystemMenuRow(item.title, item.component)
                }
            }

            item(key = "ext-header") {
                SectionLabel(
                    stringResource(R.string.text_menu_ext_section),
                    stringResource(R.string.text_menu_drag_hint)
                )
            }

            items(rows, key = { "app|text|${it.component}" }) { row ->
                val isDragging = drag?.packageName == row.component
                val marker = MaterialTheme.colorScheme.primary
                ExtensionMenuRow(
                    row = row,
                    position = rows.indexOf(row) + 1,
                    dragging = isDragging,
                    onToggle = { row.rule?.let(vm::toggle) },
                    modifier = Modifier
                        .alpha(if (isDragging) 0.3f else 1f)
                        .drawWithContent {
                            drawContent()
                            if (drag?.target == row.component && drag.packageName != row.component) {
                                val y = if (drag.movingDown) size.height - 2.dp.toPx() else 2.dp.toPx()
                                drawLine(
                                    marker,
                                    Offset(0f, y),
                                    Offset(size.width, y),
                                    3.dp.toPx()
                                )
                            }
                        }
                )
            }

            if (rows.isEmpty()) {
                item(key = "empty") {
                    EmptyHint(stringResource(R.string.text_menu_empty))
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = vm::resetProcessTextOrder,
                    enabled = order.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.text_menu_reset_order), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(
                    onClick = vm::clearProcessTextRules,
                    enabled = hiddenCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.text_menu_clear_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private data class ExtensionRow(
    val component: String,
    val ruleId: String?,
    val title: String,
    val subtitle: String,
    val rule: com.eio258.cleanchooser.domain.ComponentRule?,
    val hidden: Boolean,
)

private fun buildExtensionRows(
    candidates: List<ComponentCandidate>,
    order: List<String>,
    observed: List<com.eio258.cleanchooser.domain.TextMenuItem>,
    selected: Set<com.eio258.cleanchooser.domain.ComponentRule>,
    observedComponents: Set<String>,
): List<ExtensionRow> {
    val byComponent = candidates.associateBy { "${it.rule.packageName}/${it.rule.className}" }
    val rows = LinkedHashMap<String, ExtensionRow>()

    fun add(component: String, candidate: ComponentCandidate?) {
        val rule = candidate?.rule
        rows[component] = ExtensionRow(
            component = component,
            ruleId = rule?.id,
            title = candidate?.activityLabel ?: component.substringAfterLast('/'),
            subtitle = candidate?.rule?.packageName ?: component.substringBefore('/'),
            rule = rule,
            hidden = rule != null && rule in selected
        )
    }

    observed.forEach { item ->
        val component = item.component ?: return@forEach
        add(component, byComponent[component])
    }
    candidates.forEach { candidate ->
        val component = "${candidate.rule.packageName}/${candidate.rule.className}"
        if (component !in rows) add(component, candidate)
    }

    val rank = order.withIndex().associate { it.value to it.index }
    return rows.values.sortedWith(
        compareBy<ExtensionRow> { if (it.hidden) 1 else 0 }
            .thenBy { rank[it.rule?.id] ?: Int.MAX_VALUE }
            .thenBy { it.title.lowercase() }
    )
}

@Composable
private fun StatusBanner(observed: Boolean, ready: Boolean, hiddenCount: Int) {
    val colors = MaterialTheme.colorScheme
    val ok = observed && ready
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
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
                stringResource(
                    when {
                        !observed -> R.string.text_menu_not_observed
                        ready -> R.string.text_menu_observed
                        else -> R.string.text_menu_saved_pending
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                stringResource(R.string.text_menu_hidden_count, hiddenCount),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String, hint: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
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

@Composable
private fun SystemMenuRow(title: String, component: String?) {
    AppEntryRow(
        title = title,
        subtitle = component ?: stringResource(R.string.text_menu_system_subtitle),
        icon = null,
        onOpen = {},
        showChevron = false,
        trailing = {
            Text(
                stringResource(R.string.text_menu_system_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, AppShape.tile)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    )
}

@Composable
private fun ExtensionMenuRow(
    row: ExtensionRow,
    position: Int,
    dragging: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppEntryRow(
        title = row.title,
        subtitle = row.subtitle,
        icon = null,
        onOpen = {},
        modifier = modifier.alpha(if (row.hidden) 0.6f else 1f),
        checkState = if (row.hidden) ToggleableState.On else ToggleableState.Off,
        onCheck = if (row.rule != null) onToggle else null,
        rank = position,
        showChevron = false,
        trailing = {
            if (row.hidden) {
                Text(
                    stringResource(R.string.text_menu_hidden),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (dragging) {
                Text(
                    stringResource(R.string.text_menu_dragging),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

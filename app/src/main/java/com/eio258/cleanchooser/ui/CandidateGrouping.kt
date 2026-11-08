package com.eio258.cleanchooser.ui

import android.graphics.Bitmap
import com.eio258.cleanchooser.domain.AppMeta
import com.eio258.cleanchooser.domain.AppType
import com.eio258.cleanchooser.domain.ComponentCandidate
import com.eio258.cleanchooser.domain.ComponentRule
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.PINYIN_AWARE_SORT
import com.eio258.cleanchooser.domain.displayNameComparator
import com.eio258.cleanchooser.domain.normalizeBrowserHost

data class AppGroup(
    val packageName: String,
    val appLabel: String,
    val appIcon: Bitmap?,
    val appType: AppType,
    val components: List<ComponentCandidate>,
    /** Every entry of this package is configured but absent from the current scan. */
    val missing: Boolean = false,
)

private fun appSelectionRank(group: AppGroup, selected: Set<ComponentRule>): Int {
    val selectedCount = group.components.count { it.rule in selected }
    return when {
        group.components.isNotEmpty() && selectedCount == group.components.size -> 0
        selectedCount > 0 -> 1
        else -> 2
    }
}

internal fun baseAppGroups(candidates: List<ComponentCandidate>): List<AppGroup> =
    candidates.groupBy { it.rule.packageName }.map { (_, all) ->
        val first = all.first()
        AppGroup(
            first.rule.packageName,
            first.appLabel,
            first.appIcon,
            first.appType,
            all.sortedBy { it.rule.kind.ordinal },
            // "Missing" means the package is not installed, not merely unobserved: a launcher-only
            // app has no share/open entry yet is perfectly present.
            missing = all.none { it.installed }
        )
    }

/** Created once per sort pass; orders Han labels by Mandarin pinyin when [PINYIN_AWARE_SORT] is on. */
private val pinyinLabelComparator = displayNameComparator()

internal fun filterAppGroups(
    groups: List<AppGroup>,
    selected: Set<ComponentRule>,
    filter: IntentKind?,
    query: String,
    uiFilter: UiFilter
): List<AppGroup> = groups.mapNotNull { group ->
    val matching = group.components.filter {
        val isSelected = it.rule in selected
        val matchesUiFilter = when (uiFilter) {
            UiFilter.ALL -> true
            UiFilter.HIDE_SELECTED -> !isSelected
            UiFilter.SHOW_SELECTED -> isSelected
            UiFilter.LOCKED -> true
        }
        catalogVisible(it, isSelected, uiFilter) && matchesUiFilter &&
            (filter == null || it.rule.kind == filter) && it.matchesQuery(query)
    }
    if (matching.isEmpty()) null else group.copy(components = matching)
}.sortedWith(
    compareBy<AppGroup> { appSelectionRank(it, selected) }
        .then(
            if (PINYIN_AWARE_SORT) compareBy(pinyinLabelComparator) { it.appLabel }
            else compareBy { it.components.firstOrNull()?.normalizedAppLabel ?: it.appLabel.lowercase() }
        )
        .thenBy { it.packageName }
)

fun groupCandidates(
    candidates: List<ComponentCandidate>,
    selected: Set<ComponentRule>,
    filter: IntentKind?,
    query: String,
    uiFilter: UiFilter
): List<AppGroup> = filterAppGroups(baseAppGroups(candidates), selected, filter, query, uiFilter)

fun retainConfiguredCandidates(
    items: List<ComponentCandidate>,
    selected: Set<ComponentRule>,
    unavailableEvidence: String = "Configured but not observed during this scan; this does not mean the app is uninstalled",
    metaOf: (String) -> AppMeta? = { null },
): List<ComponentCandidate> {
    val kept = items.filter { !it.unavailable || it.rule in selected }
    val ids = kept.map { it.rule.id }.toSet()
    return kept + selected.filter { it.id !in ids }.map { rule ->
        // Resolve the package instead of trusting the id: an app whose only configured surface is
        // a launcher tile / shortcut never shows up in the intent scan, so it would otherwise be
        // rendered as a bare package name with no icon.
        val meta = metaOf(rule.packageName)
        ComponentCandidate(
            rule = rule,
            appLabel = meta?.label ?: rule.packageName,
            activityLabel = rule.className.substringAfterLast('.'),
            appIcon = meta?.icon,
            appType = meta?.type ?: AppType.USER,
            evidence = listOf(unavailableEvidence),
            unavailable = true,
            installed = meta != null,
        )
    }
}

internal fun availableDeepLinkHosts(
    configuredHosts: Set<String>,
    discoveredHosts: Set<String>,
    candidates: List<ComponentCandidate>
): Set<String> {
    val normalizedDiscovered = discoveredHosts.mapNotNull(::normalizeBrowserHost).toSet()
    val matched = candidates.asSequence()
        .filter { it.rule.kind == IntentKind.DEEP_LINK && it.isCatalogCandidate }
        .flatMap { it.browserHosts.asSequence() }
        .mapNotNull(::normalizeBrowserHost)
        .filter { it in normalizedDiscovered }
        .toSet()
    return configuredHosts.mapNotNull(::normalizeBrowserHost).toSet() + matched
}

internal fun catalogVisible(item: ComponentCandidate, selected: Boolean, uiFilter: UiFilter): Boolean =
    item.isCatalogCandidate || (selected && (uiFilter == UiFilter.SHOW_SELECTED || uiFilter == UiFilter.LOCKED))

/**
 * One application-class partition of a flat list. The single partitioning rule used by every
 * surface that lists applications (rules, components, app nodes); callers only supply the row
 * type and how to read its class.
 */
data class AppTypeSection<T>(
    /** Null marks the trailing "no longer installed" bucket, which has no application class. */
    val type: AppType?,
    val items: List<T>,
)

/**
 * Partition order is a product decision, not an accident of enum declaration order: user
 * applications always come first, system applications second. A class added to [AppType] later
 * falls back to first-seen order after the known ones, so this never silently mis-orders.
 */
internal val APP_SECTION_ORDER = listOf(AppType.USER, AppType.SYSTEM)

/**
 * Splits rows into per-class partitions in one pass, preserving input order inside each
 * partition so callers keep their own sort logic. Empty input short-circuits, so render layers
 * never need a separate emptiness check.
 */
internal inline fun <T> sectionsByAppType(
    items: List<T>,
    typeOf: (T) -> AppType,
    isMissing: (T) -> Boolean = { false },
): List<AppTypeSection<T>> {
    if (items.isEmpty()) return emptyList()
    val missing = ArrayList<T>()
    val buckets = LinkedHashMap<AppType, MutableList<T>>(APP_SECTION_ORDER.size)
    for (item in items) {
        if (isMissing(item)) missing.add(item)
        else buckets.getOrPut(typeOf(item)) { ArrayList() }.add(item)
    }
    val ordered = ArrayList<AppTypeSection<T>>(buckets.size + 1)
    for (type in APP_SECTION_ORDER) {
        val bucket = buckets.remove(type) ?: continue
        ordered.add(AppTypeSection(type, bucket))
    }
    // Forward compatibility: classes outside APP_SECTION_ORDER keep their first-seen order.
    for ((type, bucket) in buckets) {
        ordered.add(AppTypeSection(type, bucket))
    }
    // Configured-but-absent packages always trail the list: they are leftovers, not a class.
    if (missing.isNotEmpty()) ordered.add(AppTypeSection(null, missing))
    return ordered
}

/**
 * Whether to render partition headers at all. Only meaningful when more than one class is
 * present: with a single class the class switcher (or the page's own summary line) already
 * states the scope, so a header would just repeat it.
 */
internal fun List<AppTypeSection<*>>.needsTypeHeaders(): Boolean = size > 1

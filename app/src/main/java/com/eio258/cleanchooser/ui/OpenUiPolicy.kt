package com.eio258.cleanchooser.ui

import com.eio258.cleanchooser.domain.ComponentRule
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.OpenTypeConfig
import com.eio258.cleanchooser.domain.PriorityConfig

/** Pure UI projection: raw persisted typed config + generic OPEN inheritance -> effective typed config. */
internal fun effectiveOpenTypes(
    raw: OpenTypeConfig,
    selected: Set<ComponentRule>,
    priorities: PriorityConfig
): OpenTypeConfig {
    val genericIds = selected.asSequence().filter { it.kind == IntentKind.OPEN }.map { it.id }.toSet()
    val genericPriority = priorities.apps[IntentKind.OPEN].orEmpty()
    val rules = raw.configuredPresets().mapNotNull { preset ->
        val ids = genericIds + raw.rules[preset].orEmpty()
        if (ids.isEmpty()) null else preset to ids
    }.toMap()
    val ranked = raw.configuredPresets().mapNotNull { preset ->
        val explicit = raw.priorities[preset].orEmpty()
        val packages = if (explicit.isNotEmpty()) explicit else genericPriority
        if (packages.isEmpty()) null else preset to packages
    }.toMap()
    return raw.copy(rules = rules, priorities = ranked)
}

package com.eio258.cleanchooser.xposed

import android.content.ComponentName
import android.content.pm.ShortcutInfo
import com.eio258.cleanchooser.domain.ComponentRule
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.OBSERVABLE_ENTRY_KINDS
import com.eio258.cleanchooser.domain.SyntheticEntryKeys
import com.eio258.cleanchooser.domain.isPackageScopedEntry
import java.util.concurrent.ConcurrentHashMap

internal data class ObservedRuntimeEntry(
    val kind: IntentKind,
    val packageName: String,
    val syntheticClass: String,
    val label: String,
    val activityClass: String?,
    val observedAt: Long,
    val userId: Int,
)

/** Process-local observations from Android's authoritative runtime managers. */
internal object RuntimeObservedEntryStore {
    private const val MAX_ENTRIES = 1024
    private const val MAX_LABEL_CHARS = 160
    private const val MAX_ID_CHARS = 300
    private val entries = ConcurrentHashMap<String, ObservedRuntimeEntry>()

    fun observeShortcut(shortcut: ShortcutInfo) {
        if (!shortcut.isEnabled || (!shortcut.isDeclaredInManifest && !shortcut.isDynamic)) return
        observeShortcutLike(IntentKind.SHORTCUT_ITEM, shortcut, shortcut.activity)
    }

    fun observeDirectShare(shortcut: ShortcutInfo, target: ComponentName?) {
        observeShortcutLike(IntentKind.DIRECT_SHARE, shortcut, target ?: shortcut.activity)
    }

    fun observeComponent(kind: IntentKind, component: ComponentName, label: String = "", userId: Int) {
        if (kind !in OBSERVABLE_ENTRY_KINDS || kind.isPackageScopedEntry()) return
        val rule = ComponentRule(kind, component.packageName, component.className)
        if (!rule.isValid()) return
        put(
            ObservedRuntimeEntry(
                kind = kind,
                packageName = rule.packageName,
                syntheticClass = rule.className,
                label = label.take(MAX_LABEL_CHARS),
                activityClass = rule.className,
                observedAt = System.currentTimeMillis(),
                userId = userId,
            )
        )
    }

    private fun observeShortcutLike(kind: IntentKind, shortcut: ShortcutInfo, component: ComponentName?) {
        val packageName = shortcut.`package`?.takeIf { it.isNotBlank() } ?: return
        val shortcutId = shortcut.id?.takeIf { it.isNotBlank() && it.length <= MAX_ID_CHARS } ?: return
        val targetClass = component?.className
        val synthetic = when (kind) {
            IntentKind.DIRECT_SHARE -> SyntheticEntryKeys.directShareClass(targetClass, shortcutId)
            else -> SyntheticEntryKeys.shortcutItemClass(targetClass, shortcutId)
        }
        put(
            ObservedRuntimeEntry(
                kind = kind,
                packageName = packageName,
                syntheticClass = synthetic,
                label = shortcut.shortLabel?.toString()?.take(MAX_LABEL_CHARS).orEmpty(),
                activityClass = targetClass,
                observedAt = System.currentTimeMillis(),
                userId = ReflectionAccess.userId(shortcut.userHandle) ?: return,
            )
        )
    }

    private fun put(entry: ObservedRuntimeEntry) {
        val key = "${entry.userId}|${entry.kind.name}|${entry.packageName}|${entry.syntheticClass}"
        entries[key] = entry
        if (entries.size > MAX_ENTRIES) {
            entries.values.sortedBy { it.observedAt }
                .take(entries.size - MAX_ENTRIES)
                .forEach { old ->
                    entries.remove("${old.userId}|${old.kind.name}|${old.packageName}|${old.syntheticClass}", old)
                }
        }
    }

    fun snapshot(userId: Int): List<ObservedRuntimeEntry> =
        entries.values.filter { it.userId == userId && System.currentTimeMillis() - it.observedAt in 0..300_000L }
            .sortedWith(
                compareByDescending<ObservedRuntimeEntry> { it.observedAt }
                    .thenBy { it.kind.ordinal }
                    .thenBy { it.packageName }
                    .thenBy { it.label }
            )
}

package com.eio258.cleanchooser.xposed

import android.content.ComponentName
import android.content.pm.ShortcutInfo
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.SyntheticEntryKeys
import java.util.LinkedHashMap

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
    // Access-order map: least recently touched entries sit at the head and are evicted in O(1).
    private val entries = LinkedHashMap<String, ObservedRuntimeEntry>(16, 0.75f, true)

    fun observeShortcut(shortcut: ShortcutInfo) {
        if (!shortcut.isEnabled || (!shortcut.isDeclaredInManifest && !shortcut.isDynamic)) return
        observeShortcutLike(IntentKind.SHORTCUT_ITEM, shortcut, shortcut.activity)
    }

    fun observeDirectShare(shortcut: ShortcutInfo, target: ComponentName?) {
        observeShortcutLike(IntentKind.DIRECT_SHARE, shortcut, target ?: shortcut.activity)
    }

    fun observeComponent(kind: IntentKind, component: ComponentName, label: String = "", userId: Int) {
        val rule = observableComponentRule(kind, component) ?: return
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
            else -> SyntheticEntryKeys.shortcutItemClass(shortcutId)
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
        synchronized(entries) {
            entries[key] = entry
            val iterator = entries.entries.iterator()
            while (entries.size > MAX_ENTRIES && iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    fun snapshot(userId: Int): List<ObservedRuntimeEntry> =
        synchronized(entries) {
            entries.values.filter { it.userId == userId && System.currentTimeMillis() - it.observedAt in 0..300_000L }
        }.sortedWith(
            compareByDescending<ObservedRuntimeEntry> { it.observedAt }
                .thenBy { it.kind.ordinal }
                .thenBy { it.packageName }
                .thenBy { it.label }
        )
}

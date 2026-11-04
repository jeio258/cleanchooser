package com.eio258.cleanchooser.xposed

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.view.Menu
import android.view.MenuItem
import com.eio258.cleanchooser.domain.TextMenuItem
import com.eio258.cleanchooser.domain.TextMenuSnapshot
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Observes the ROM text-selection menu from system_server.
 *
 * Technique (studied from the GPL txtoi project, reimplemented from scratch — no txtoi code):
 * on Oplus builds the menu policy lives behind android.widget.IEditorExt and the floating
 * toolbar extension, so a probe menu can be assembled in place and the ROM itself decides
 * which entries it pins. The pinned verdict combines "the ROM extension declared the entry"
 * with "the entry gained an action slot during collection".
 *
 * Everything is fail-open: reflection gaps degrade to UNAVAILABLE plus a short backoff and
 * this class never throws at its callers. The ROM policy is built lazily — extension
 * factories are not ready at system-server-start time.
 */
internal class MenuSnapshotProbe {
    private val memo = ProbeOutcomeCache()

    // Single-threaded daemon worker: each observation performs per-candidate PackageManager
    // lookups plus OEM reflection, which must never stall the hooked query thread. evaluate
    // only enqueues; a published snapshot lands asynchronously and the next poll picks it up.
    private val probeExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ListCleaner-MenuProbe").apply { isDaemon = true }
    }

    @Volatile private var inspector: RomMenuInspector? = null
    @Volatile private var classLoader: ClassLoader? = null
    @Volatile private var logger: ((String) -> Unit)? = null
    @Volatile private var systemContext: Context? = null
    @Volatile private var encoded: String = ""

    fun encodedSnapshot(): String = encoded

    fun initialize(classLoader: ClassLoader, record: (String) -> Unit) {
        this.classLoader = classLoader
        logger = record
        // The inspector is built lazily on the first evaluate call: Oplus extension factories
        // answer null while system_server is still starting, so early construction always fails.
        record("MENU_PROBE_DEFERRED")
    }

    private fun inspectorOrDown(): RomMenuInspector? {
        inspector?.let { return it }
        val loader = classLoader ?: return null
        val built = runCatching { RomMenuInspector(loader) }.getOrElse {
            logger?.invoke("MENU_PROBE_DOWN reason=${it.message}")
            null
        } ?: return null
        inspector = built
        logger?.invoke("MENU_PROBE_UP")
        return built
    }

    /** candidates: (packageName, className) pairs of PROCESS_TEXT activities, in query order. */
    fun evaluate(candidates: List<Pair<String, String>>) {
        if (candidates.isEmpty()) return
        probeExecutor.execute { evaluateOnProbeThread(candidates) }
    }

    @Synchronized
    private fun evaluateOnProbeThread(candidates: List<Pair<String, String>>) {
        val inspector = inspectorOrDown() ?: return
        if (candidates.isEmpty()) return
        val now = android.os.SystemClock.elapsedRealtime()
        val fingerprint = ProbeOutcomeCache.fingerprint(candidates)
        val cached = memo.lookup(fingerprint, now)
        if (cached != null) {
            if (!cached.degraded) {
                publish(cached.entries)
            }
            return
        }
        val context = systemContext() ?: run {
            record("MENU_PROBE_STALLED reason=no_system_context")
            memo.markFailure(fingerprint, now)
            return
        }
        val observed = runCatching { inspector.observe(context, candidates) }.getOrElse {
            record("MENU_PROBE_STALLED reason=${it.message}")
            memo.markFailure(fingerprint, now)
            return
        }
        if (observed.isEmpty()) {
            memo.markFailure(fingerprint, now)
            return
        }
        val payload = TextMenuSnapshot.encode(observed)
        if (payload.isEmpty()) {
            memo.markFailure(fingerprint, now)
            return
        }
        memo.store(fingerprint, observed, now)
        publish(observed)
    }

    private fun publish(entries: List<TextMenuItem>) {
        val next = TextMenuSnapshot.encode(entries)
        if (next.isNotEmpty() && next != encoded) {
            encoded = next
            record("MENU_PROBE_PUBLISHED entries=${entries.size} pinned=${entries.count { it.fixed }}")
        }
    }

    private fun systemContext(): Context? {
        systemContext?.let { return it }
        val thread = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("systemMain").invoke(null)
        }.getOrNull() ?: return null
        val created = runCatching {
            thread.javaClass.getMethod("getSystemContext").invoke(thread) as? Context
        }.getOrNull()
        if (created != null) systemContext = created
        return created
    }

    private fun record(message: String) {
        logger?.invoke(message)
    }
}

/**
 * One PROCESS_TEXT candidate carried through the probe. [menuHandle] is the MenuItem the
 * assembled menu ended up with for this candidate; the classification compares action-slot
 * state of that handle before and after the ROM collected the visible entries.
 */
private class ProbeTarget(
    val component: ComponentName,
    val info: ActivityInfo,
    val label: CharSequence,
    val intent: Intent,
) {
    var romDeclared: Boolean = false
    var menuHandle: MenuItem? = null
    var slotBefore: Boolean = false
    var pinned: Boolean = false
}

/**
 * Reflection facade over the Oplus menu policy. Construction fails with a descriptive
 * [IllegalStateException] (message = the missing piece) when the ROM lacks any piece; the
 * probe then reports itself down until the next process restart.
 */
private class RomMenuInspector(classLoader: ClassLoader) {
    private val editor: Any
    private val raisePriority: Method
    private val setSpeechAssist: Method
    private val setSearch: Method
    private val actionSlotAccessor: Method
    private val menuBuilderConstructor: Constructor<*>
    private val visibleCollector: Method
    private val orderingComparator: Method

    init {
        val editorContract = requireClass(classLoader, "android.widget.IEditorExt")
        val toolbarContract = requireClass(classLoader, "com.android.internal.widget.floatingtoolbar.IFloatingToolbarExt")
        editor = newExtension(editorContract)
        val toolbar = newExtension(toolbarContract)
        check(
            classLoader.loadClass("android.widget.EditorExtImpl").isInstance(editor) &&
                classLoader.loadClass("com.android.internal.widget.floatingtoolbar.FloatingToolbarExtImpl").isInstance(toolbar)
        ) { "menu policy: Oplus editor extensions absent" }
        raisePriority = editorContract.getMethod(
            "raiseOplusMenuPriority", Int::class.javaPrimitiveType, CharSequence::class.java,
            Intent::class.java, ResolveInfo::class.java, Menu::class.java,
        )
        setSpeechAssist = editorContract.getMethod(
            "setSpeechAssistMenuItem", Int::class.javaPrimitiveType, Intent::class.java,
            CharSequence::class.java, ResolveInfo::class.java, Menu::class.java,
        )
        setSearch = editorContract.getMethod(
            "setSearchMenuItem", Int::class.javaPrimitiveType, Intent::class.java,
            CharSequence::class.java, ResolveInfo::class.java, Menu::class.java,
        )
        val menuItemImpl = requireClass(classLoader, "com.android.internal.view.menu.MenuItemImpl")
        actionSlotAccessor = menuItemImpl.getMethod("requiresActionButton")
        val menuBuilder = requireClass(classLoader, "com.android.internal.view.menu.MenuBuilder")
        menuBuilderConstructor = menuBuilder.getConstructor(Context::class.java)
        val floatingToolbar = requireClass(classLoader, "com.android.internal.widget.floatingtoolbar.FloatingToolbar")
        visibleCollector = floatingToolbar.getDeclaredMethod("getVisibleAndEnabledMenuItems", Menu::class.java)
        visibleCollector.isAccessible = true
        orderingComparator = locateComparator(floatingToolbar)
        orderingComparator.isAccessible = true
    }

    /**
     * Registers every candidate into a fresh menu (the ROM extension gets first refusal),
     * collects the visible entries through the ROM collector, and classifies each entry.
     * Structural problems abort with an empty list — never with a throw.
     */
    fun observe(context: Context, candidates: List<Pair<String, String>>): List<TextMenuItem> {
        val pm = context.packageManager
        val targets = ArrayList<ProbeTarget>(candidates.size)
        candidates.forEach { (pkg, cls) ->
            val info: ActivityInfo = runCatching {
                pm.getActivityInfo(ComponentName(pkg, cls), 0)
            }.getOrNull() ?: return@forEach
            val label = runCatching { info.loadLabel(pm) }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: pkg
            val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
                .setComponent(ComponentName(pkg, cls))
            targets.add(ProbeTarget(ComponentName(pkg, cls), info, label, intent))
        }
        if (targets.isEmpty()) return emptyList()

        val menu = menuBuilderConstructor.newInstance(context) as Menu
        targets.forEachIndexed { position, target ->
            val asInfo = ResolveInfo().apply { activityInfo = target.info }
            target.romDeclared = claimsEntry(raisePriority, 100 + position, target, asInfo, menu) ||
                claimsEntry(setSpeechAssist, 5, target, asInfo, menu) ||
                claimsEntry(setSearch, 6, target, asInfo, menu)
            if (!target.romDeclared) {
                menu.add(0, 0, 100 + position, target.label).setIntent(target.intent)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
            if (menu.size() != position + 1) {
                // The ROM inserted or dropped an entry; the mapping below would be guesswork.
                return emptyList()
            }
            target.menuHandle = unclaimedHandle(menu, targets) ?: return emptyList()
        }

        targets.forEach { target ->
            target.slotBefore = runCatching {
                actionSlotAccessor.invoke(target.menuHandle) as Boolean
            }.getOrDefault(false)
        }
        val collected = runCatching { visibleCollector.invoke(null, menu) as? List<*> }
            .getOrNull() ?: return emptyList()
        if (collected.size != targets.size) return emptyList()
        val seen = HashSet<MenuItem>()
        collected.forEach { raw ->
            val handle = raw as? MenuItem ?: return emptyList()
            val target = targets.firstOrNull { it.menuHandle === handle } ?: return emptyList()
            if (!seen.add(handle)) return emptyList()
            val slotAfter = runCatching {
                actionSlotAccessor.invoke(handle) as Boolean
            }.getOrDefault(target.slotBefore)
            target.pinned = target.romDeclared || (slotAfter && !target.slotBefore)
        }

        return targets.sortedWith { left, right ->
            orderingComparator.invoke(null, left.menuHandle, right.menuHandle) as Int
        }.map { target ->
            TextMenuItem(
                title = target.label.toString(),
                component = target.component.flattenToString(),
                fixed = target.pinned,
            )
        }
    }

    private fun claimsEntry(method: Method, order: Int, target: ProbeTarget, asInfo: ResolveInfo, menu: Menu): Boolean =
        runCatching {
            method.invoke(editor, order, target.label, target.intent, asInfo, menu) as Boolean
        }.getOrDefault(false)

    /** First menu handle no candidate has claimed yet. */
    private fun unclaimedHandle(menu: Menu, targets: List<ProbeTarget>): MenuItem? {
        val claimed = targets.mapNotNull { it.menuHandle }.toHashSet()
        return (0 until menu.size())
            .map(menu::getItem)
            .firstOrNull { it !in claimed }
    }

    private fun locateComparator(toolbarType: Class<*>): Method {
        val matches = toolbarType.declaredMethods.filter { method ->
            Modifier.isStatic(method.modifiers) &&
                method.returnType == Int::class.javaPrimitiveType &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == MenuItem::class.java &&
                method.parameterTypes[1] == MenuItem::class.java
        }
        check(matches.size == 1) { "menu policy: installed comparator ambiguous (${matches.size})" }
        return matches.single()
    }

    private fun newExtension(type: Class<*>): Any {
        val factory = Class.forName("system.ext.loader.core.ExtLoader", false, type.classLoader)
            .getMethod("type", Class::class.java).invoke(null, type)
        factory.javaClass.getMethod("base", Any::class.java).invoke(factory, null)
        return factory.javaClass.getMethod("create").invoke(factory)
            ?: throw IllegalStateException("menu policy: extension factory returned null")
    }

    private fun requireClass(classLoader: ClassLoader, name: String): Class<*> =
        runCatching { Class.forName(name, false, classLoader) }
            .getOrElse { throw IllegalStateException("menu policy: $name absent") }
}

/**
 * Pure JVM memo of probe outcomes keyed by the candidate fingerprint. Successful
 * observations live 30s, failures back off 5s; entries are LRU-capped. Stores only
 * immutable item lists, never framework objects.
 */
internal class ProbeOutcomeCache {
    data class Cell(
        val entries: List<TextMenuItem>,
        val degraded: Boolean,
        /** TTL runs from the store time; reads never extend it. */
        val storedAt: Long,
        /** LRU recency: reads refresh this, eviction uses it. */
        val touchedAt: Long,
    )

    private val cells = LinkedHashMap<String, Cell>()
    private val lock = Any()

    fun lookup(key: String, now: Long): Cell? = synchronized(lock) {
        val cell = cells[key] ?: return null
        val ttl = if (cell.degraded) FAILURE_TTL_MS else SUCCESS_TTL_MS
        if (now - cell.storedAt >= ttl) {
            // Expiry must not leave a failure entry behind: a fresh probe is allowed.
            cells.remove(key)
            return null
        }
        // Reading an entry refreshes its LRU recency only; the TTL still runs from store time.
        cells[key] = cell.copy(touchedAt = now)
        cell
    }

    fun store(key: String, entries: List<TextMenuItem>, now: Long) = synchronized(lock) {
        cells[key] = Cell(entries, degraded = false, storedAt = now, touchedAt = now)
        trim()
    }

    fun markFailure(key: String, now: Long) = synchronized(lock) {
        cells[key] = Cell(emptyList(), degraded = true, storedAt = now, touchedAt = now)
        trim()
    }

    private fun trim() {
        while (cells.size > CAPACITY) {
            val oldest = cells.minByOrNull { it.value.touchedAt }?.key ?: return
            cells.remove(oldest)
        }
    }

    companion object {
        const val SUCCESS_TTL_MS = 30_000L
        const val FAILURE_TTL_MS = 5_000L
        const val CAPACITY = 8

        /** Order-sensitive fingerprint of the candidate list. */
        fun fingerprint(candidates: List<Pair<String, String>>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            candidates.forEach { (pkg, cls) ->
                digest.update(pkg.toByteArray())
                digest.update(cls.toByteArray())
                digest.update(0)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

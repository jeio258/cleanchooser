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

/**
 * Observes the ROM's text selection menu policy by actively assembling it in system_server.
 *
 * Hooking toolbar setters never fires in system_server, so this probe builds its own
 * [Menu] from PROCESS_TEXT candidates, lets the installed Oplus editor extension decide
 * which entries it pins or promotes, collects the toolbar's real visible set, and
 * compares [MenuItemImpl.requiresActionButton] before/after to classify entries as
 * FIXED (ROM-pinned slot) vs ORDINARY (regular candidate).
 *
 * Everything is fail-open: any reflection gap degrades to UNAVAILABLE plus a short
 * failure backoff; nothing here ever throws to the caller.
 */
internal class MenuSnapshotProbe {
    private val memo = MenuPolicyMemo()

    @Volatile private var policy: RomMenuPolicy? = null
    @Volatile private var classLoader: ClassLoader? = null
    @Volatile private var logger: ((String) -> Unit)? = null
    @Volatile private var systemContext: Context? = null
    @Volatile private var encoded: String = ""

    fun encodedSnapshot(): String = encoded

    fun initialize(classLoader: ClassLoader, record: (String) -> Unit) {
        this.classLoader = classLoader
        logger = record
        // RomMenuPolicy is built lazily on the first evaluate: ColorOS extension factories are
        // not ready at onSystemServerStarting and return null there (txtoi builds lazily too).
        record("MENU_PROBE_DEFERRED")
    }

    private fun policyOrUnavailable(): RomMenuPolicy? {
        policy?.let { return it }
        val loader = classLoader ?: return null
        val built = runCatching { RomMenuPolicy(loader) }.getOrElse {
            logger?.invoke("MENU_PROBE_UNAVAILABLE error=${it.javaClass.simpleName} msg=${it.message}")
            null
        } ?: return null
        policy = built
        logger?.invoke("MENU_PROBE_READY")
        return built
    }

    /** candidates: (packageName, className) of PROCESS_TEXT activities, in probe order. */
    @Synchronized
    fun evaluate(candidates: List<Pair<String, String>>) {
        val rom = policyOrUnavailable() ?: return
        if (candidates.isEmpty()) return
        val now = android.os.SystemClock.elapsedRealtime()
        val key = MenuPolicyMemo.fingerprint(candidates)
        val cached = memo.get(key, now)
        if (cached != null) {
            if (!cached.failure) {
                val next = TextMenuSnapshot.encode(cached.items)
                if (next.isNotEmpty() && next != encoded) encoded = next
            }
            return
        }
        val context = systemContext() ?: run {
            record("MENU_PROBE_FAILED error=no_system_context")
            memo.putFailure(key, now)
            return
        }
        val items = runCatching { rom.probe(context, candidates) }.getOrElse {
            record("MENU_PROBE_FAILED error=${it.javaClass.simpleName}")
            memo.putFailure(key, now)
            return
        }
        if (items.isEmpty()) {
            memo.putFailure(key, now)
            return
        }
        val next = TextMenuSnapshot.encode(items)
        if (next.isEmpty()) {
            memo.putFailure(key, now)
            return
        }
        memo.put(key, items, now)
        if (next != encoded) {
            encoded = next
            record("MENU_PROBE_CAPTURED items=${items.size} fixed=${items.count { it.fixed }}")
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
 * Reflection bundle for the installed ROM menu policy. Resolution is all-or-nothing:
 * a missing Oplus class or method fails construction and the probe reports UNAVAILABLE.
 */
private class RomMenuPolicy(classLoader: ClassLoader) {
    private val editorExt: Any
    private val raisePriority: Method
    private val setSpeech: Method
    private val setSearch: Method
    private val requiresActionButton: Method
    private val newMenuBuilder: Constructor<*>
    private val collectVisible: Method
    private val comparator: Method

    init {
        val editorType = load(classLoader, "android.widget.IEditorExt")
            ?: fail("IEditorExt unavailable")
        val toolbarExtType =
            load(classLoader, "com.android.internal.widget.floatingtoolbar.IFloatingToolbarExt")
                ?: fail("IFloatingToolbarExt unavailable")
        val editor = extInstance(editorType)
        val toolbarExt = extInstance(toolbarExtType)
        val editorImpl = load(classLoader, "android.widget.EditorExtImpl")
        val toolbarExtImpl =
            load(classLoader, "com.android.internal.widget.floatingtoolbar.FloatingToolbarExtImpl")
        if (editorImpl == null || toolbarExtImpl == null ||
            !editorImpl.isInstance(editor) || !toolbarExtImpl.isInstance(toolbarExt)
        ) {
            fail("installed menu policy implementation unavailable")
        }
        // A default extension is not evidence that the ROM has no pinned items.
        editorExt = editor
        raisePriority = editorType.getMethod(
            "raiseOplusMenuPriority", Int::class.java, CharSequence::class.java,
            Intent::class.java, ResolveInfo::class.java, Menu::class.java
        )
        setSpeech = editorType.getMethod(
            "setSpeechAssistMenuItem", Int::class.java, Intent::class.java,
            CharSequence::class.java, ResolveInfo::class.java, Menu::class.java
        )
        setSearch = editorType.getMethod(
            "setSearchMenuItem", Int::class.java, Intent::class.java,
            CharSequence::class.java, ResolveInfo::class.java, Menu::class.java
        )
        val menuItemImplType = load(classLoader, "com.android.internal.view.menu.MenuItemImpl")
            ?: fail("MenuItemImpl unavailable")
        requiresActionButton = menuItemImplType.getMethod("requiresActionButton")
        val menuBuilderType = load(classLoader, "com.android.internal.view.menu.MenuBuilder")
            ?: fail("MenuBuilder unavailable")
        newMenuBuilder = menuBuilderType.getConstructor(Context::class.java)
        val toolbarType =
            load(classLoader, "com.android.internal.widget.floatingtoolbar.FloatingToolbar")
                ?: fail("FloatingToolbar unavailable")
        collectVisible =
            toolbarType.getDeclaredMethod("getVisibleAndEnabledMenuItems", Menu::class.java)
        collectVisible.isAccessible = true
        comparator = findMenuComparator(toolbarType)
        comparator.isAccessible = true
    }

    private class Slot(
        val component: ComponentName,
        val info: ActivityInfo,
        val label: CharSequence,
        val intent: Intent,
        var handled: Boolean,
        var item: MenuItem? = null,
        var requiredBefore: Boolean = false,
        var fixed: Boolean = false,
    )

    fun probe(context: Context, candidates: List<Pair<String, String>>): List<TextMenuItem> {
        val pm = context.packageManager
        val resolved = ArrayList<Slot>(candidates.size)
        candidates.forEach { (pkg, cls) ->
            val component = ComponentName(pkg, cls)
            val info: ActivityInfo = runCatching { pm.getActivityInfo(component, 0) }.getOrNull()
                ?: return@forEach
            val label = runCatching { info.loadLabel(pm) }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: pkg
            val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
                .setComponent(component)
            resolved.add(Slot(component, info, label, intent, handled = false))
        }
        if (resolved.isEmpty()) fail("no candidates resolved")

        val menu = newMenuBuilder.newInstance(context) as Menu
        resolved.forEachIndexed { index, slot ->
            val info = ResolveInfo().apply { activityInfo = slot.info }
            slot.handled = runCatching {
                raisePriority.invoke(editorExt, 100 + index, slot.label, slot.intent, info, menu) as Boolean
            }.getOrDefault(false)
            if (!slot.handled) slot.handled = runCatching {
                setSpeech.invoke(editorExt, 5, slot.intent, slot.label, info, menu) as Boolean
            }.getOrDefault(false)
            if (!slot.handled) slot.handled = runCatching {
                setSearch.invoke(editorExt, 6, slot.intent, slot.label, info, menu) as Boolean
            }.getOrDefault(false)
            if (!slot.handled) {
                menu.add(0, 0, 100 + index, slot.label).setIntent(slot.intent)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
            if (menu.size() != index + 1) fail("unexpected menu size")
            val freeIndex = (0 until menu.size())
                .firstOrNull { index -> resolved.none { other -> other.item === menu.getItem(index) } }
            slot.item = menu.getItem(freeIndex ?: fail("generated menu item unavailable"))
        }

        resolved.forEach { slot ->
            slot.requiredBefore = runCatching {
                requiresActionButton.invoke(slot.item) as Boolean
            }.getOrThrow()
        }

        val collected = runCatching { collectVisible.invoke(null, menu) as? List<*> }
            .getOrNull() ?: fail("menu collection unavailable")
        if (collected.size != resolved.size) fail("incomplete menu collection")
        val mapped = ArrayList<Slot>(resolved.size)
        collected.forEach { raw ->
            val item = raw as? MenuItem ?: fail("invalid toolbar menu item")
            val slot = resolved.firstOrNull { it.item === item } ?: fail("unmapped toolbar menu item")
            if (slot in mapped) fail("duplicate toolbar menu item")
            val requiredAfter = runCatching {
                requiresActionButton.invoke(item) as Boolean
            }.getOrThrow()
            slot.fixed = slot.handled || (!slot.requiredBefore && requiredAfter)
            mapped.add(slot)
        }

        return mapped
            .sortedWith(Comparator { left, right ->
                comparator.invoke(null, left.item, right.item) as Int
            })
            .map { slot ->
                TextMenuItem(
                    title = slot.label.toString(),
                    component = slot.component.flattenToString(),
                    fixed = slot.fixed,
                )
            }
    }

    /** Locate the ROM comparator by its unique static signature, not a lambda name. */
    private fun findMenuComparator(toolbarType: Class<*>): Method {
        var found: Method? = null
        for (method in toolbarType.declaredMethods) {
            val parameters = method.parameterTypes
            if (Modifier.isStatic(method.modifiers) && method.returnType == Int::class.javaPrimitiveType &&
                parameters.size == 2 && parameters[0] == MenuItem::class.java &&
                parameters[1] == MenuItem::class.java
            ) {
                if (found != null) fail("ambiguous installed menu comparator")
                found = method
            }
        }
        return found ?: fail("installed menu comparator unavailable")
    }

    private fun extInstance(type: Class<*>): Any {
        val builder = Class.forName("system.ext.loader.core.ExtLoader", false, type.classLoader)
            .getMethod("type", Class::class.java).invoke(null, type)
        builder.javaClass.getMethod("base", Any::class.java).invoke(builder, null)
        return builder.javaClass.getMethod("create").invoke(builder)
            ?: fail("extension factory returned null")
    }

    private fun load(classLoader: ClassLoader, name: String): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    private fun fail(message: String): Nothing = throw IllegalStateException(message)
}

/**
 * Pure JVM memo of probe outcomes keyed by the candidate fingerprint. Successful
 * classifications live 30s, failures back off 5s; entries are LRU-capped so a long
 * candidate stream cannot grow it. Stores only immutable item lists, never framework objects.
 */
internal class MenuPolicyMemo {
    internal class Entry(
        val items: List<TextMenuItem>,
        val failure: Boolean,
        val issuedAt: Long,
        val lifetime: Long,
    )

    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    @Synchronized
    fun get(key: String, now: Long): Entry? {
        val entry = entries[key] ?: return null
        if (now < entry.issuedAt || now - entry.issuedAt >= entry.lifetime) {
            entries.remove(key)
            return null
        }
        return entry
    }

    @Synchronized
    fun put(key: String, items: List<TextMenuItem>, now: Long) {
        entries[key] = Entry(
            items = items.toList(),
            failure = false,
            issuedAt = now,
            lifetime = SUCCESS_TTL_MS,
        )
        evict()
    }

    @Synchronized
    fun putFailure(key: String, now: Long) {
        entries[key] = Entry(
            items = emptyList(),
            failure = true,
            issuedAt = now,
            lifetime = FAILURE_TTL_MS,
        )
        evict()
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun evict() {
        while (entries.size > CAPACITY) {
            entries.remove(entries.keys.iterator().next())
        }
    }

    companion object {
        const val CAPACITY = 8
        const val SUCCESS_TTL_MS = 30_000L
        const val FAILURE_TTL_MS = 5_000L

        /** Order-sensitive: menu slots and display order depend on candidate order. */
        internal fun fingerprint(candidates: List<Pair<String, String>>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            candidates.forEach { (pkg, cls) ->
                digest.update(pkg.toByteArray(Charsets.UTF_8))
                digest.update(NUL)
                digest.update(cls.toByteArray(Charsets.UTF_8))
                digest.update(NUL)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private val NUL = byteArrayOf(0)
    }
}

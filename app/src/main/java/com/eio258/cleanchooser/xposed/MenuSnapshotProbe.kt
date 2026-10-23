package com.eio258.cleanchooser.xposed

import android.view.Menu
import android.view.MenuItem
import com.eio258.cleanchooser.domain.TextMenuItem
import com.eio258.cleanchooser.domain.TextMenuSnapshot
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * Captures the text selection menu exactly as the system assembled it.
 *
 * Several ROMs build that menu in different places, so every candidate entry point is
 * probed and hooked independently; whichever fires first wins. Nothing here is required
 * for filtering or ordering to work: when no candidate resolves the manager simply shows
 * "not observed" and keeps the rule-only view.
 */
internal class MenuSnapshotProbe(
    private val register: (Method, XposedInterface.Hooker) -> Unit,
    private val diagnostic: (String) -> Unit,
) {
    @Volatile
    private var encoded: String = ""

    @Volatile
    private var lastCaptureAt: Long = 0L

    @Volatile
    private var installedAny = false

    fun encodedSnapshot(): String = encoded

    fun install(classLoader: ClassLoader) {
        val toolbar = load(classLoader, "com.android.internal.widget.floatingtoolbar.FloatingToolbar")
        val popup = load(classLoader, "com.android.internal.widget.floatingtoolbar.FloatingToolbarPopup")

        var hooked = 0
        hooked += hookSetter(toolbar, "setItems", List::class.java, argIndex = 0)
        hooked += hookSetter(toolbar, "setMenu", Menu::class.java, argIndex = 0)
        hooked += hookSetter(popup, "setMenuItems", List::class.java, argIndex = 0)
        hooked += hookGetter(toolbar, "getVisibleAndEnabledMenuItems", Menu::class.java)
        hooked += hookGetter(popup, "getVisibleAndEnabledMenuItems", Menu::class.java)

        installedAny = hooked > 0
        diagnostic(
            if (installedAny) "MENU_PROBE_INSTALLED hooks=$hooked"
            else "MENU_PROBE_UNAVAILABLE"
        )
    }

    private fun load(classLoader: ClassLoader, name: String): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    private fun hookSetter(
        type: Class<*>?,
        name: String,
        parameter: Class<*>,
        argIndex: Int,
    ): Int {
        val target = type ?: return 0
        val method = runCatching { target.getDeclaredMethod(name, parameter) }.getOrNull() ?: return 0
        return runCatching {
            register(method, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                runCatching { capture(chain.args.getOrNull(argIndex)) }
                result
            })
            1
        }.getOrElse {
            diagnostic("MENU_PROBE_HOOK_FAILED method=$name error=${it.javaClass.simpleName}")
            0
        }
    }

    private fun hookGetter(type: Class<*>?, name: String, parameter: Class<*>): Int {
        val target = type ?: return 0
        val method = runCatching { target.getDeclaredMethod(name, parameter) }.getOrNull() ?: return 0
        return runCatching {
            register(method, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                runCatching { capture(result) }
                result
            })
            1
        }.getOrElse {
            diagnostic("MENU_PROBE_HOOK_FAILED method=$name error=${it.javaClass.simpleName}")
            0
        }
    }

    private fun capture(source: Any?) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastCaptureAt < 250L) return
        val menuItems: List<Any?> = when (source) {
            is Menu -> (0 until source.size()).map { source.getItem(it) }
            is List<*> -> source
            else -> return
        }
        if (menuItems.isEmpty()) return

        val items = ArrayList<TextMenuItem>(menuItems.size)
        for (raw in menuItems) {
            val item = raw as? MenuItem ?: continue
            val title = item.title?.toString()?.trim().orEmpty()
            if (title.isEmpty()) continue
            val component = item.intent?.component?.flattenToString()
            items += TextMenuItem(title = title, component = component, fixed = component == null)
        }
        if (items.isEmpty()) return

        val next = TextMenuSnapshot.encode(items)
        if (next.isEmpty()) return
        lastCaptureAt = now
        if (next != encoded) {
            encoded = next
            diagnostic("MENU_PROBE_CAPTURED items=${items.size} fixed=${items.count { it.fixed }}")
        }
    }
}

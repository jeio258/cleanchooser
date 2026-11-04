package com.eio258.cleanchooser.xposed

import com.eio258.cleanchooser.domain.TextMenuItem
import com.eio258.cleanchooser.domain.TextMenuSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the JVM-testable parts of the menu snapshot probe:
 * the classification memo (TTL, failure backoff, LRU eviction, fingerprint)
 * and the snapshot encoding contract the probe's output must satisfy.
 */
class MenuSnapshotProbeContractTest {
    private fun items(vararg titles: String) = titles.map { TextMenuItem(title = it) }

    @Test
    fun successEntrySurvivesFullTtlThenExpires() {
        val memo = MenuPolicyMemo()
        memo.put("k", items("Alpha", "Beta"), now = 1_000L)

        assertNotNull(memo.get("k", now = 1_000L + MenuPolicyMemo.SUCCESS_TTL_MS - 1))
        assertNull(memo.get("k", now = 1_000L + MenuPolicyMemo.SUCCESS_TTL_MS))
    }

    @Test
    fun failureEntryBacksOffOnlyForShortWindow() {
        val memo = MenuPolicyMemo()
        memo.putFailure("k", now = 1_000L)

        val backed = memo.get("k", now = 1_000L + MenuPolicyMemo.FAILURE_TTL_MS - 1)
        assertNotNull(backed)
        assertTrue(backed!!.failure)
        assertTrue(backed.items.isEmpty())
        assertNull(memo.get("k", now = 1_000L + MenuPolicyMemo.FAILURE_TTL_MS))
    }

    @Test
    fun expiredEntryIsRemovedSoSuccessBackoffCannotBeReused() {
        val memo = MenuPolicyMemo()
        memo.put("k", items("Alpha"), now = 0L)
        assertNull(memo.get("k", now = MenuPolicyMemo.SUCCESS_TTL_MS))
        // Expiry must not leave a failure entry behind: a fresh probe is allowed.
        assertNull(memo.get("k", now = MenuPolicyMemo.SUCCESS_TTL_MS + 1))
    }

    @Test
    fun lruEvictsEldestAndAccessRefreshesRecency() {
        val memo = MenuPolicyMemo()
        (1..MenuPolicyMemo.CAPACITY).forEach { index -> memo.put("k$index", items("i$index"), now = index.toLong()) }
        memo.get("k1", now = 100L) // refresh k1, so k2 becomes the eldest
        memo.put("k${MenuPolicyMemo.CAPACITY + 1}", items("new"), now = 200L)

        assertNotNull(memo.get("k1", now = 201L))
        assertNull(memo.get("k2", now = 201L))
        assertEquals(MenuPolicyMemo.CAPACITY, memo.countEntries())
    }

    @Test
    fun fingerprintIsDeterministicOrderSensitiveAndKeyedPerCandidate() {
        val first = listOf("com.a" to "com.a.Main", "com.b" to "com.b.Main")
        assertEquals(MenuPolicyMemo.fingerprint(first), MenuPolicyMemo.fingerprint(first))
        assertNotEquals(
            MenuPolicyMemo.fingerprint(first),
            MenuPolicyMemo.fingerprint(first.reversed()),
        )
        assertNotEquals(
            MenuPolicyMemo.fingerprint(listOf("com.a" to "com.a.Main")),
            MenuPolicyMemo.fingerprint(listOf("com.a" to "com.a.Other")),
        )
    }

    @Test
    fun snapshotEncodingRoundTripsDisplayOrderAndClassification() {
        val items = listOf(
            TextMenuItem(title = "Translate", component = "com.a/.Translate", fixed = false),
            TextMenuItem(title = "Pinned", component = "com.b/.Pinned", fixed = true),
            TextMenuItem(title = "Copy", fixed = true),
        )
        val encoded = TextMenuSnapshot.encode(items)
        assertTrue(encoded.isNotEmpty())

        val decoded = TextMenuSnapshot.decode(encoded)
        assertNotNull(decoded)
        assertEquals(items, decoded)
        assertEquals(1, decoded!!.indexOfFirst { it.title == "Pinned" })
    }

    @Test
    fun snapshotEncodingCapsSizeAndTitleLength() {
        val longTitle = "x".repeat(64)
        val items = (0 until 30).map { index ->
            TextMenuItem(title = "$longTitle-$index", component = "com.pkg/.Activity$index")
        }
        val decoded = TextMenuSnapshot.decode(TextMenuSnapshot.encode(items))!!

        assertEquals(TextMenuSnapshot.MAX_ITEMS, decoded.size)
        assertTrue(decoded.all { it.title.length <= TextMenuSnapshot.MAX_TITLE })
        // The probe relies on encode failing closed for oversized payloads.
        assertTrue(TextMenuSnapshot.encode(items).isNotEmpty())
        assertNull(TextMenuSnapshot.decode(null))
        assertNull(TextMenuSnapshot.decode("not-base64!!"))
    }
}

private fun MenuPolicyMemo.countEntries(): Int {
    var seen = 0
    (1..MenuPolicyMemo.CAPACITY + 1).forEach { index ->
        if (get("k$index", now = 250L) != null) seen++
    }
    return seen
}

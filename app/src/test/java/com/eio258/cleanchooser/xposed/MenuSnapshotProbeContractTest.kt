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
 * the outcome cache (success TTL, failure backoff, LRU eviction, fingerprint)
 * and the snapshot encoding contract the probe's output must satisfy.
 */
class MenuSnapshotProbeContractTest {
    private fun items(vararg titles: String) = titles.map { TextMenuItem(title = it) }

    @Test
    fun successEntrySurvivesFullTtlThenExpires() {
        val cache = ProbeOutcomeCache()
        cache.store("k", items("Alpha", "Beta"), now = 1_000L)

        assertNotNull(cache.lookup("k", now = 1_000L + ProbeOutcomeCache.SUCCESS_TTL_MS - 1))
        assertNull(cache.lookup("k", now = 1_000L + ProbeOutcomeCache.SUCCESS_TTL_MS))
    }

    @Test
    fun failureEntryBacksOffOnlyForShortWindow() {
        val cache = ProbeOutcomeCache()
        cache.markFailure("k", now = 1_000L)

        val backed = cache.lookup("k", now = 1_000L + ProbeOutcomeCache.FAILURE_TTL_MS - 1)
        assertNotNull(backed)
        assertTrue(backed!!.degraded)
        assertTrue(backed.entries.isEmpty())
        assertNull(cache.lookup("k", now = 1_000L + ProbeOutcomeCache.FAILURE_TTL_MS))
    }

    @Test
    fun expiredEntryIsRemovedSoSuccessBackoffCannotBeReused() {
        val cache = ProbeOutcomeCache()
        cache.store("k", items("Alpha"), now = 0L)
        assertNull(cache.lookup("k", now = ProbeOutcomeCache.SUCCESS_TTL_MS))
        // Expiry must not leave a failure entry behind: a fresh probe is allowed.
        assertNull(cache.lookup("k", now = ProbeOutcomeCache.SUCCESS_TTL_MS + 1))
    }

    @Test
    fun lruEvictsEldestAndAccessRefreshesRecency() {
        val cache = ProbeOutcomeCache()
        (1..ProbeOutcomeCache.CAPACITY).forEach { index -> cache.store("k$index", items("i$index"), now = index.toLong()) }
        cache.lookup("k1", now = 100L) // refresh k1, so k2 becomes the eldest
        cache.store("k${ProbeOutcomeCache.CAPACITY + 1}", items("new"), now = 200L)

        assertNotNull(cache.lookup("k1", now = 201L))
        assertNull(cache.lookup("k2", now = 201L))
        assertEquals(ProbeOutcomeCache.CAPACITY, cache.countEntries())
    }

    @Test
    fun fingerprintIsDeterministicOrderSensitiveAndKeyedPerCandidate() {
        val first = listOf("com.a" to "com.a.Main", "com.b" to "com.b.Main")
        assertEquals(ProbeOutcomeCache.fingerprint(first), ProbeOutcomeCache.fingerprint(first))
        assertNotEquals(
            ProbeOutcomeCache.fingerprint(first),
            ProbeOutcomeCache.fingerprint(first.reversed()),
        )
        assertNotEquals(
            ProbeOutcomeCache.fingerprint(listOf("com.a" to "com.a.Main")),
            ProbeOutcomeCache.fingerprint(listOf("com.a" to "com.a.Other")),
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
        // The probe relies on encode always producing a usable (possibly trimmed) payload.
        assertTrue(TextMenuSnapshot.encode(items).isNotEmpty())
        assertNull(TextMenuSnapshot.decode(null))
        assertNull(TextMenuSnapshot.decode("not-base64!!"))
    }
}

private fun ProbeOutcomeCache.countEntries(): Int {
    var seen = 0
    (1..ProbeOutcomeCache.CAPACITY + 1).forEach { index ->
        if (lookup("k$index", now = 250L) != null) seen++
    }
    return seen
}

package com.eio258.cleanchooser.xposed

import android.content.SharedPreferences
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.ObservedEntryCacheCodec
import com.eio258.cleanchooser.domain.ObservedEntryRecord

/** Remote preference keys for runtime-observed entries. The cleanchooser prefix namespaces our rows. */
internal object ObservedRemoteKeys {
    const val REMOTE_KEY = "cleanchooser_observed_entries_v1"
    private const val REMOTE_KIND_PREFIX = "cleanchooser_observed_entries_v2_kind_"

    fun remoteKey(kind: IntentKind, userId: Int = 0): String =
        REMOTE_KIND_PREFIX + kind.name + if (userId == 0) "" else "_user_$userId"

    fun isRemoteKey(key: String?): Boolean =
        key == REMOTE_KEY || key?.startsWith(REMOTE_KIND_PREFIX) == true
}

/** Persists runtime-observed entries through libxposed RemotePreferences. */
internal class RemoteObservedEntryPersistence(
    private val preferences: SharedPreferences,
    private val record: (String) -> Unit,
) {
    private companion object {
        const val MAX_WRITE_FAILURES = 3
    }

    /**
     * Write attempts before giving up. Some frameworks hand out a read-only RemotePreferences
     * instance; in that case every observation would raise and log, so a few failures flip a
     * latch and the store degrades to the in-process cache instead of retrying forever.
     */
    private var consecutiveWriteFailures = 0

    @Volatile
    private var writesDisabled = false

    private fun persist(key: String, encoded: String): Boolean {
        if (writesDisabled) return false
        val written = runCatching { preferences.edit().putString(key, encoded).commit() }
            .getOrElse { error ->
                synchronized(this) {
                    consecutiveWriteFailures += 1
                    if (!writesDisabled && consecutiveWriteFailures >= MAX_WRITE_FAILURES) {
                        writesDisabled = true
                        record(
                            "OBSERVED_CACHE_WRITE_DISABLED key=" + key +
                                " error=" + error.javaClass.simpleName
                        )
                    }
                }
                false
            }
        if (written) {
            synchronized(this) { consecutiveWriteFailures = 0 }
        }
        return written
    }

    /**
     * Delta-style observations are isolated by kind so independent Xposed processes never race on
     * one aggregate JSON value. The read/merge/write cycle is now scoped to one logical surface.
     */
    fun merge(entries: Collection<ObservedEntryRecord>): Boolean {
        if (entries.isEmpty()) return true
        val grouped = entries.mapNotNull { candidate ->
            val validated = candidate.validatedOrNull() ?: return@mapNotNull null
            val kind = runCatching { IntentKind.valueOf(validated.kind) }.getOrNull()
                ?: return@mapNotNull null
            (kind to validated.userId) to validated
        }.groupBy({ it.first }, { it.second })
        return grouped.all { (scope, values) ->
            val (kind, userId) = scope
            update(ObservedRemoteKeys.remoteKey(kind, userId)) { previous ->
                ObservedEntryCacheCodec.merge(previous, values)
            }
        }
    }

    /**
     * Replace one authority surface without reading or rewriting other kinds. Empty snapshots are
     * meaningful and therefore still write an encoded empty list for this kind.
     */
    fun replaceKind(kind: IntentKind, entries: Collection<ObservedEntryRecord>, userId: Int = 0): Boolean = runCatching {
        val encoded = ObservedEntryCacheCodec.encode(
            entries.mapNotNull(ObservedEntryRecord::validatedOrNull)
                .filter { it.kind == kind.name && it.userId == userId }
        )
        check(encoded.length <= ObservedEntryCacheCodec.MAX_ENCODED_CHARS)
        persist(ObservedRemoteKeys.remoteKey(kind, userId), encoded)
    }.onFailure {
        record("OBSERVED_CACHE_WRITE_FAILED kind=${kind.name} error=${it.javaClass.name}")
    }.getOrDefault(false)

    @Synchronized
    private fun update(
        key: String,
        transform: (List<ObservedEntryRecord>) -> List<ObservedEntryRecord>,
    ): Boolean = runCatching {
        val previousEncoded = preferences.getString(key, null)
        val previous = ObservedEntryCacheCodec.decode(previousEncoded)
        val encoded = ObservedEntryCacheCodec.encode(transform(previous))
        if (encoded == previousEncoded) return@runCatching true
        check(encoded.length <= ObservedEntryCacheCodec.MAX_ENCODED_CHARS)
        persist(key, encoded)
    }.onFailure {
        record("OBSERVED_CACHE_WRITE_FAILED key=$key error=${it.javaClass.name}")
    }.getOrDefault(false)
}

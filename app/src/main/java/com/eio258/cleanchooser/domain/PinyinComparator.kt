package com.eio258.cleanchooser.domain

import java.text.Collator
import java.util.Locale

/** Switch for pinyin-aware app-label ordering (upstream eb2e8383); intentionally no settings page. */
internal const val PINYIN_AWARE_SORT = true

/**
 * Android's Chinese collator orders Han characters by Mandarin pinyin while
 * retaining alphabetical comparisons for Latin names. Create one per sort,
 * rather than creating it in every comparator invocation.
 */
internal fun displayNameComparator(): Comparator<String> {
    val collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE).apply {
        strength = Collator.PRIMARY
    }
    return Comparator { first, second ->
        collator.compare(first, second)
    }
}

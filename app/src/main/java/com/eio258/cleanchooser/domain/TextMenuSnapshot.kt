package com.eio258.cleanchooser.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * One entry of the text selection menu as the system actually assembled it.
 *
 * [fixed] marks system-provided actions (cut/copy/paste/select all/share): they carry no
 * component intent, can be hidden, but keep their slot when other entries are reordered.
 * This class only describes what was observed; it never fabricates items.
 */
@Serializable
data class TextMenuItem(
    val title: String,
    val component: String? = null,
    val fixed: Boolean = false,
)

/** Immutable, size-capped view of the last text menu captured in system_server. */
data class TextMenuSnapshot(
    val items: List<TextMenuItem>,
    val capturedAtMillis: Long,
    val supported: Boolean,
) {
    companion object {
        const val MAX_ITEMS = 24
        const val MAX_TITLE = 32
        const val MAX_ENCODED = 1400
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(TextMenuItem.serializer())

        fun encode(items: List<TextMenuItem>): String {
            val trimmed = items.take(MAX_ITEMS).map { item ->
                item.copy(title = item.title.take(MAX_TITLE), component = item.component?.take(64))
            }
            val raw = json.encodeToString(serializer, trimmed)
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
            return if (encoded.length <= MAX_ENCODED) encoded else ""
        }

        fun decode(value: String?): List<TextMenuItem>? {
            if (value.isNullOrEmpty() || value.length > MAX_ENCODED) return null
            return runCatching {
                val raw = String(Base64.getUrlDecoder().decode(value))
                json.decodeFromString(serializer, raw)
            }.getOrNull()
        }
    }
}

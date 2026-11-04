package com.eio258.cleanchooser.domain

/**
 * Manager-only synthetic PackageManager query used to expose entries observed in system_server.
 * Chooser-side Direct Share observations are also mirrored through the persistent observed cache.
 */
object ObservedEntryProtocol {
    const val ACTION = "com.eio258.cleanchooser.action.OBSERVED_SHORTCUT_ENTRIES"
    const val PACKAGE = "android"
    const val META_KIND = "com.eio258.cleanchooser.meta.ENTRY_KIND"
    const val META_ACTIVITY = "com.eio258.cleanchooser.meta.ENTRY_ACTIVITY"
    const val META_USER = "com.eio258.cleanchooser.meta.ENTRY_USER"
    const val META_TIME = "com.eio258.cleanchooser.meta.ENTRY_TIME"
    const val META_ID = "com.eio258.cleanchooser.meta.ENTRY_ID"
}

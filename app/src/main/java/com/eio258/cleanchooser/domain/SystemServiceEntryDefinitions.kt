package com.eio258.cleanchooser.domain

/**
 * System-managed service surfaces that Android discovers through PackageManager service queries.
 *
 * These definitions are shared by manager discovery and the system_server filter so both sides use
 * exactly the same action/permission contract. Filtering is non-destructive: components remain
 * enabled and are only omitted from matching discovery results while the rule is active.
 *
 * Ported from upstream SystemServiceEntryDefinitions.kt. The upstream EntrySurfaceDefinitions.kt
 * surface table is deliberately NOT ported (local SyntheticEntryKeys.isPackageScopedEntry already
 * owns package identity scope); only the EntryAuthority mapping consumed by the system-server
 * privileged-UI check is mirrored here.
 */
data class SystemServiceEntryDefinition(
    val kind: IntentKind,
    val action: String,
    val requiredPermission: String? = null,
    val alternativePermissions: Set<String> = emptySet(),
) {
    fun acceptsPermission(permission: String?): Boolean =
        requiredPermission == null || permission == requiredPermission || permission in alternativePermissions
}

val SYSTEM_SERVICE_ENTRY_DEFINITIONS: List<SystemServiceEntryDefinition> = listOf(
    SystemServiceEntryDefinition(
        IntentKind.INPUT_METHOD,
        "android.view.InputMethod",
        "android.permission.BIND_INPUT_METHOD",
    ),
    SystemServiceEntryDefinition(
        IntentKind.AUTOFILL,
        "android.service.autofill.AutofillService",
        "android.permission.BIND_AUTOFILL_SERVICE",
        alternativePermissions = setOf("android.permission.BIND_AUTOFILL"),
    ),
    SystemServiceEntryDefinition(
        IntentKind.NOTIFICATION_LISTENER,
        "android.service.notification.NotificationListenerService",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.ACCESSIBILITY,
        "android.accessibilityservice.AccessibilityService",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.VPN,
        "android.net.VpnService",
        "android.permission.BIND_VPN_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.PRINT,
        "android.printservice.PrintService",
        "android.permission.BIND_PRINT_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.WALLPAPER,
        "android.service.wallpaper.WallpaperService",
        "android.permission.BIND_WALLPAPER",
    ),
    SystemServiceEntryDefinition(
        IntentKind.DREAM,
        "android.service.dreams.DreamService",
        "android.permission.BIND_DREAM_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.NFC_HCE,
        "android.nfc.cardemulation.action.HOST_APDU_SERVICE",
        "android.permission.BIND_NFC_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.NFC_HCE,
        "android.nfc.cardemulation.action.OFF_HOST_APDU_SERVICE",
        "android.permission.BIND_NFC_SERVICE",
    ),
    SystemServiceEntryDefinition(
        IntentKind.CALL_SCREENING,
        "android.telecom.CallScreeningService",
        "android.permission.BIND_SCREENING_SERVICE",
    ),
)

fun systemServiceEntryKind(action: String?): IntentKind? =
    SYSTEM_SERVICE_ENTRY_DEFINITIONS.firstOrNull { it.action == action }?.kind

fun IntentKind.isSystemServiceEntry(): Boolean =
    SYSTEM_SERVICE_ENTRY_DEFINITIONS.any { it.kind == this }

/** Android subsystem whose final list is the authority for one List Cleaner category. */
enum class EntryAuthority {
    PACKAGE_MANAGER,
    ROLE_CONTROLLER,
    ACCESSIBILITY_MANAGER,
    INPUT_METHOD_MANAGER,
    PRINT_MANAGER,
    SETTINGS_VPN,
    CREDENTIAL_MANAGER,
    COMBINED_PROVIDER_SETTINGS,
    NFC_CARD_EMULATION,
    SHORTCUT_SERVICE,
    DOCUMENTS_UI,
}

/** Minimal mirror of the upstream EntrySurfaceDefinitions authority column. */
fun IntentKind.entryAuthority(): EntryAuthority? = when (this) {
    IntentKind.BROWSER,
    IntentKind.HOME,
    IntentKind.ASSISTANT,
    IntentKind.CALL_SCREENING -> EntryAuthority.ROLE_CONTROLLER

    IntentKind.DIRECT_SHARE,
    IntentKind.SHORTCUT_ITEM -> EntryAuthority.SHORTCUT_SERVICE

    IntentKind.DOCUMENT_ROOT -> EntryAuthority.DOCUMENTS_UI
    IntentKind.INPUT_METHOD -> EntryAuthority.INPUT_METHOD_MANAGER
    IntentKind.ACCESSIBILITY -> EntryAuthority.ACCESSIBILITY_MANAGER
    IntentKind.PRINT -> EntryAuthority.PRINT_MANAGER
    IntentKind.VPN -> EntryAuthority.SETTINGS_VPN
    IntentKind.AUTOFILL -> EntryAuthority.COMBINED_PROVIDER_SETTINGS
    IntentKind.NFC_HCE -> EntryAuthority.NFC_CARD_EMULATION

    IntentKind.SHARE,
    IntentKind.SHARE_MULTIPLE,
    IntentKind.SEND_TO,
    IntentKind.OPEN,
    IntentKind.DIAL,
    IntentKind.GET_CONTENT,
    IntentKind.OPEN_DOCUMENT,
    IntentKind.CREATE_DOCUMENT,
    IntentKind.CAPTURE_IMAGE,
    IntentKind.CAPTURE_VIDEO,
    IntentKind.RECORD_AUDIO,
    IntentKind.PROCESS_TEXT,
    IntentKind.EDIT,
    IntentKind.PICK -> EntryAuthority.PACKAGE_MANAGER

    IntentKind.DEEP_LINK,
    IntentKind.DOCUMENT_PROVIDER,
    IntentKind.PHOTO_SOURCE,
    IntentKind.NOTIFICATION_LISTENER,
    IntentKind.WALLPAPER,
    IntentKind.DREAM -> EntryAuthority.PACKAGE_MANAGER

    // Not part of the upstream surface table: retired kind and the not-yet-active credential kind.
    IntentKind.LAUNCHER_SHORTCUT,
    IntentKind.CREDENTIAL_PROVIDER -> null
}

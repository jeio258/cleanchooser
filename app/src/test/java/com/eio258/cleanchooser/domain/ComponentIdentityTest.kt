package com.eio258.cleanchooser.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentIdentityTest {
    @Test fun aliasIsAnIndependentComponent() {
        // Upstream e28d0b75 semantics (user-confirmed): an alias is its own component; the
        // target activity is metadata, never a replacement for the resolved identity.
        assertEquals(
            "com.UCMobile.main.UCMobile.DefaultBrowserEntry",
            ComponentIdentity.canonicalClassName(
                "com.UCMobile",
                "com.UCMobile.main.UCMobile.DefaultBrowserEntry",
                "com.UCMobile.main.UCMobile"
            )
        )
    }

    @Test fun normalActivityKeepsItsOwnName() {
        assertEquals(
            "com.example.ShareActivity",
            ComponentIdentity.canonicalClassName(
                "com.example", "com.example.ShareActivity", null
            )
        )
    }

    @Test fun relativeAndShortNamesAreExpandedDefensively() {
        assertEquals(
            "com.example.Alias",
            ComponentIdentity.canonicalClassName("com.example", "com.example.Alias", ".AliasTarget")
        )
        assertEquals(
            "com.example.OpenActivity",
            ComponentIdentity.canonicalClassName("com.example", "OpenActivity", null)
        )
    }
}

package com.eio258.cleanchooser.domain

import android.graphics.Bitmap

/**
 * Package metadata resolved straight from PackageManager, used for rules whose entry could not
 * be observed by the current scan. Distinguishes "installed but this surface was not observed"
 * from "the package is not on this device any more" - the two cases read very differently to
 * the user and must never be collapsed into one label.
 */
data class AppMeta(
    val label: String,
    val icon: Bitmap?,
    val type: AppType,
)

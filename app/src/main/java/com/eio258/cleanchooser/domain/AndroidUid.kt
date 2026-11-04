package com.eio258.cleanchooser.domain

/** Android multi-user UID arithmetic (framework UserId rules). */
object AndroidUid {
    const val PER_USER_RANGE = 100000

    @JvmStatic
    fun userId(uid: Int): Int = uid / PER_USER_RANGE

    @JvmStatic
    fun isApplicationUid(appId: Int): Boolean = appId >= 10000
}

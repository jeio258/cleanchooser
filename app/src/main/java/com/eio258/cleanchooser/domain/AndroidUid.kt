package com.eio258.cleanchooser.domain

/** Android multi-user UID arithmetic (framework UserId rules). */
object AndroidUid {
    const val PER_USER_RANGE = 100000

    /** Inclusive upper bound for Android user/profile ids (framework USER_HANDLE_MAX - 1). */
    const val MAX_USER_ID = 99999

    @JvmStatic
    fun userId(uid: Int): Int = uid / PER_USER_RANGE

    /** Strips the per-user prefix so isolated uids (99000-99999) and system uids are
     * classified by their appId regardless of which user/profile the caller runs in. */
    @JvmStatic
    fun appId(uid: Int): Int = if (uid >= PER_USER_RANGE) uid % PER_USER_RANGE else uid

    @JvmStatic
    fun isApplicationUid(appId: Int): Boolean = appId >= 10000
}

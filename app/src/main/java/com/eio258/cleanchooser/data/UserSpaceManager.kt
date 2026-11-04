package com.eio258.cleanchooser.data

import android.content.Context
import android.os.Process
import android.os.UserManager
import android.util.Log
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.domain.AndroidUid
import com.eio258.cleanchooser.domain.PackageIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Android user/profile identity, never an application-clone package name. */
data class UserSpace(val userId: Int, val title: String)

/** Distinguish an empty remote profile from a failed or unfinished Root query. */
enum class SpacePackageScanState { LOCAL, LOADING, READY, FAILED }

/**
 * One authoritative selected space for every CleanChooser editor. Root is used only for
 * enumerating other Android users; without it we expose the local user rather than
 * guessing whether a vendor app clone is a separate Android profile.
 *
 * Dual-source discovery (fail-open): UserManager.userProfiles (reflective getIdentifier)
 * primary, root `cmd user list` for naming; if both fail only the current user is exposed,
 * never fabricated spaces.
 */
class UserSpaceManager(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("space_selection", Context.MODE_PRIVATE)
    val currentUserId: Int = AndroidUid.userId(Process.myUid())
    private val mutableSpaces = MutableStateFlow(
        listOf(UserSpace(currentUserId, app.getString(R.string.space_user_title, currentUserId)))
    )
    val spaces: StateFlow<List<UserSpace>> = mutableSpaces.asStateFlow()
    private val mutableSelected = MutableStateFlow(currentUserId)
    val selected: StateFlow<Int> = mutableSelected.asStateFlow()
    private val mutableInstalledPackages = MutableStateFlow<Set<String>?>(null)
    /** null only for the local user; empty means a remote scan failed or has no apps. */
    val installedPackages: StateFlow<Set<String>?> = mutableInstalledPackages.asStateFlow()
    private val mutableScanState = MutableStateFlow(SpacePackageScanState.LOCAL)
    val scanState: StateFlow<SpacePackageScanState> = mutableScanState.asStateFlow()

    /**
     * Remote space package query via root `pm list packages --user N`. A failed or truncated
     * query is surfaced as [SpacePackageScanState.FAILED] with an empty set — it must never
     * masquerade as a confirmed-empty profile.
     */
    suspend fun refreshInstalledPackages(userId: Int) {
        if (userId == currentUserId) {
            mutableInstalledPackages.value = null
            mutableScanState.value = SpacePackageScanState.LOCAL
            return
        }
        if (selected.value == userId) mutableScanState.value = SpacePackageScanState.LOADING
        val packages = withContext(Dispatchers.IO) {
            runCatching {
                val response = ComponentRootCommand.runReadOnly("pm list packages --user $userId")
                if (response.exitCode != 0 || response.timedOut ||
                    response.output.contains("[output truncated]")) {
                    error("Remote user package scan failed or was truncated")
                }
                response.output.lineSequence().map { it.trim() }
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:") }
                    .filter(PackageIdentity::valid)
                    .toSet()
            }.getOrNull()
        }
        if (selected.value == userId) {
            mutableInstalledPackages.value = packages ?: emptySet()
            mutableScanState.value = if (packages == null) SpacePackageScanState.FAILED
                else SpacePackageScanState.READY
        }
    }

    suspend fun refresh() {
        val found = withContext(Dispatchers.IO) {
            val result = linkedMapOf(
                currentUserId to UserSpace(
                    currentUserId,
                    app.getString(R.string.space_user_title, currentUserId)
                )
            )
            var reflectiveFailed = false
            var rootFailed = false
            runCatching {
                app.getSystemService(UserManager::class.java)?.userProfiles.orEmpty().forEach { handle ->
                    // SDK stubs may hide getIdentifier even when the device provides
                    // it. Prefer the runtime value; as a compatibility fallback decode
                    // the legacy UserHandle representation (hashCode() carries the full
                    // uid, so userId = uid / 100000 per Android's uid encoding). A raw
                    // hashCode() is never used as a userId — it fabricates a fake space
                    // (e.g. 1010xxx) that later switchSpace() validation would reject.
                    val id = runCatching {
                        (handle.javaClass.getMethod("getIdentifier").invoke(handle) as Number).toInt()
                    }.getOrElse {
                        Log.w(TAG, "getIdentifier unavailable; decoding UserHandle uid fallback")
                        val uid = handle.hashCode()
                        if (uid > 0) uid / 100_000 else -1
                    }
                    if (id in 0..99999) {
                        result[id] = UserSpace(
                            id, app.getString(R.string.space_user_title, id)
                        )
                    } else {
                        Log.w(TAG, "Discarding profile with out-of-range userId $id (no fake space created)")
                    }
                }
            }.onFailure {
                Log.w(TAG, "UserManager profile discovery failed entirely", it)
                reflectiveFailed = true
            }
            runCatching {
                val output = ComponentRootCommand.runReadOnly("cmd user list")
                if (output.exitCode == 0 && !output.timedOut) {
                    // UserInfo{10:Work profile:...}; no shell substitution or user-controlled input.
                    Regex("UserInfo\\{([0-9]{1,5}):([^:}\\r\\n]{1,100}):").findAll(output.output)
                        .forEach { hit ->
                            val id = hit.groupValues[1].toInt()
                            result[id] = UserSpace(
                                id,
                                hit.groupValues[2].trim()
                                    .ifEmpty { app.getString(R.string.space_user_title, id) }
                            )
                        }
                } else rootFailed = true
            }.onFailure { rootFailed = true }
            if (reflectiveFailed && rootFailed && result.size <= 1) {
                // Degraded, not silent: both discovery sources failed; only the local user
                // is exposed so callers know enumeration is unavailable rather than empty.
                Log.w(TAG, "User space discovery degraded: both UserManager and root sources failed")
            }
            result.values.sortedBy { it.userId }
        }
        mutableSpaces.value = found
        val restored = prefs.getInt("selected_user", currentUserId)
        if (mutableSelected.value !in found.map { it.userId }) {
            mutableSelected.value = if (restored in found.map { it.userId }) restored else currentUserId
        } else if (mutableSelected.value == currentUserId && restored in found.map { it.userId }) {
            mutableSelected.value = restored
        }
    }

    fun select(userId: Int): Boolean {
        if (userId !in mutableSpaces.value.map { it.userId }) return false
        mutableSelected.value = userId
        mutableInstalledPackages.value = if (userId == currentUserId) null else emptySet()
        mutableScanState.value = if (userId == currentUserId)
            SpacePackageScanState.LOCAL else SpacePackageScanState.LOADING
        prefs.edit().putInt("selected_user", userId).apply()
        return true
    }

    private companion object {
        const val TAG = "UserSpaceManager"
    }
}

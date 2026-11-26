package com.branchphotovault.navigation

import android.content.Context

/**
 * Restores the last explicitly opened branch if Android kills the app while
 * the user is doing field work. No image/media data is stored here.
 */
class BranchWorkSession(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "image_keeper_work_session_v1", Context.MODE_PRIVATE
    )

    fun rememberBranch(account: String, branchCode: String) {
        if (account.isBlank() || branchCode.isBlank()) return
        preferences.edit()
            .putString("account", account)
            .putString("branch_code", branchCode)
            .apply()
    }

    fun lastBranch(): Pair<String, String>? {
        val account = preferences.getString("account", null)?.takeIf { it.isNotBlank() } ?: return null
        val branchCode = preferences.getString("branch_code", null)?.takeIf { it.isNotBlank() } ?: return null
        return account to branchCode
    }

    /** Explicitly returning to the branch list ends the active work session. */
    fun clearBranch() {
        preferences.edit().remove("account").remove("branch_code").apply()
    }
}

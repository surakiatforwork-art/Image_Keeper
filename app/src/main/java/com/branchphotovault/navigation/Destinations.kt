package com.branchphotovault.navigation

import android.net.Uri

sealed class Destination(val route: String) {
    data object BranchList : Destination("branch_list")
    data object AddBranch : Destination("add_branch")
    data object Settings : Destination("settings")
    data object BranchPreview : Destination("branch_preview/{account}/{branchCode}") {
        fun createRoute(account: String, branchCode: String): String {
            return "branch_preview/${Uri.encode(account)}/${Uri.encode(branchCode)}"
        }
    }

    data object PhotoViewer : Destination("photo_viewer/{account}/{branchCode}/{photoId}") {
        fun createRoute(account: String, branchCode: String, photoId: String): String {
            return "photo_viewer/${Uri.encode(account)}/${Uri.encode(branchCode)}/${Uri.encode(photoId)}"
        }
    }

    data object CapturePhoto : Destination("capture_photo/{account}/{branchCode}") {
        fun createRoute(account: String, branchCode: String): String {
            return "capture_photo/${Uri.encode(account)}/${Uri.encode(branchCode)}"
        }
    }
}


package com.branchphotovault.util

object AppConfig {
    const val DEFAULT_APPS_SCRIPT_URL =
        "https://script.google.com/macros/s/AKfycbzIqft7RnWxAPly_XjAESJ_IxzaPR3YXBvB5s4E8beKejv-_BlBDt97cAXoLA0rQGe1/exec"
    const val RETROFIT_PLACEHOLDER_BASE_URL = "https://script.google.com/"

    const val ACTION_HEALTH = "health"
    const val ACTION_BRANCHES = "branches"
    const val ACTION_ADD_BRANCH = "addBranch"
    const val ACTION_UPDATE_ROUTE = "updateRoute"

    const val FOLDER_PHOTOS = "photos"
    const val FOLDER_THUMBS = "thumbs"
    const val FOLDER_EXPORTS = "Branch Photo Vault"
    const val FOLDER_CAPTURE_TEMP = "capture_temp"

    const val MAIN_LONG_EDGE = 2560
    const val THUMB_LONG_EDGE = 512
    const val MAIN_QUALITY = 92
    const val THUMB_QUALITY = 80
    const val ASPECT_RATIO_ORIGINAL = "ORIGINAL"

    const val DEFAULT_RETENTION_MONTHS = 3
    const val DEFAULT_EXPORT_FOLDER_PATTERN = "{account}_{branchCode}"

    const val DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
    const val MONTH_KEY_PATTERN = "yyyy-MM"
}

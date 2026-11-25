package com.branchphotovault.data.repository

import android.content.Context

/** Gallery presentation choices belong to each branch, not to the photo records. */
enum class GalleryLayout { GRID, LIST }
enum class GalleryPhotoSort { NEWEST_FIRST, OLDEST_FIRST }

data class GalleryViewConfig(
    val layout: GalleryLayout = GalleryLayout.GRID,
    val columns: Int = 3,
    val sort: GalleryPhotoSort = GalleryPhotoSort.NEWEST_FIRST
)

/**
 * Store a branch's display choices in app-private preferences.
 * This never changes Room photo ordering, selection order or transfer/export behavior.
 */
class GalleryViewPreferences(context: Context) {
    private val storage = context.applicationContext.getSharedPreferences(
        "keeper_branch_gallery_view_v1", Context.MODE_PRIVATE
    )

    private fun prefix(account: String, branchCode: String): String =
        "branch_${account.length}:${account}_${branchCode.length}:${branchCode}_"

    fun load(account: String, branchCode: String): GalleryViewConfig {
        val p = prefix(account, branchCode)
        val layout = enumValueOrDefault(
            storage.getString(p + "layout", null),
            GalleryLayout.GRID
        )
        val sort = enumValueOrDefault(
            storage.getString(p + "sort", null),
            GalleryPhotoSort.NEWEST_FIRST
        )
        return GalleryViewConfig(
            layout = layout,
            columns = storage.getInt(p + "columns", 3).coerceIn(3, 6),
            sort = sort
        )
    }

    fun save(account: String, branchCode: String, config: GalleryViewConfig) {
        val p = prefix(account, branchCode)
        storage.edit()
            .putString(p + "layout", config.layout.name)
            .putInt(p + "columns", config.columns.coerceIn(3, 6))
            .putString(p + "sort", config.sort.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}

package com.branchphotovault.util

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.branchphotovault.data.model.BranchSortOption

object BranchQueryBuilder {

    fun build(
        searchText: String,
        accountFilter: String?,
        sortOption: BranchSortOption,
        onlyWithPhotos: Boolean = false
    ): SupportSQLiteQuery {
        val sql = StringBuilder(
            """
            SELECT 
                b.account AS account,
                b.branchCode AS branchCode,
                b.shopName AS shopName,
                b.currentRoute AS currentRoute,
                b.rowNumber AS rowNumber,
                b.updatedAt AS updatedAt,
                COUNT(p.id) AS photoCount
            FROM branches b
            LEFT JOIN photos p
                ON p.account = b.account
                AND p.branchCode = b.branchCode
            WHERE 1 = 1
            """.trimIndent()
        )
        val args = mutableListOf<Any>()

        val trimmedSearch = searchText.trim()
        if (trimmedSearch.isNotEmpty()) {
            sql.append(
                """
                
                AND (
                    b.account LIKE ?
                    OR b.branchCode LIKE ?
                    OR b.shopName LIKE ?
                )
                """.trimIndent()
            )
            val like = "%$trimmedSearch%"
            args += like
            args += like
            args += like
        }

        if (accountFilter != null) {
            sql.append("\nAND b.account = ?")
            args += accountFilter
        }

        sql.append(
            """
            
            GROUP BY b.account, b.branchCode, b.shopName, b.currentRoute, b.rowNumber, b.updatedAt
            """.trimIndent()
        )
        if (onlyWithPhotos) sql.append("\nHAVING COUNT(p.id) > 0")
        sql.append("\nORDER BY ")

        when (sortOption) {
            BranchSortOption.ACCOUNT -> sql.append("b.account ASC, b.branchCode ASC")
            BranchSortOption.BRANCH_CODE -> sql.append("b.branchCode ASC, b.account ASC")
            BranchSortOption.SHOP_NAME -> sql.append("b.shopName COLLATE NOCASE ASC, b.account ASC")
            BranchSortOption.OLDEST_FIRST -> sql.append("MAX(p.createdAt) IS NULL, MAX(p.createdAt) ASC, b.account ASC, b.branchCode ASC")
            BranchSortOption.NEWEST_FIRST -> sql.append("MAX(p.createdAt) IS NULL, MAX(p.createdAt) DESC, b.account ASC, b.branchCode ASC")
        }

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }
}

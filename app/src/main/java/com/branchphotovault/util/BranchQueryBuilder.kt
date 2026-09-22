package com.branchphotovault.util

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.branchphotovault.data.model.BranchSortOption

object BranchQueryBuilder {

    fun build(
        searchText: String,
        accountFilter: String?,
        sortOption: BranchSortOption
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
            ORDER BY 
            """.trimIndent()
        )

        when (sortOption) {
            BranchSortOption.ROUTE -> sql.append(
                "CASE WHEN b.currentRoute IS NULL THEN 1 ELSE 0 END, b.currentRoute ASC, b.account ASC, b.branchCode ASC"
            )

            BranchSortOption.ACCOUNT -> sql.append("b.account ASC, b.branchCode ASC")
            BranchSortOption.BRANCH_CODE -> sql.append("b.branchCode ASC, b.account ASC")
            BranchSortOption.SHOP_NAME -> sql.append("b.shopName COLLATE NOCASE ASC, b.account ASC")
        }

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }
}

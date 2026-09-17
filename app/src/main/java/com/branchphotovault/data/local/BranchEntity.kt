package com.branchphotovault.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "branches",
    primaryKeys = ["account", "branchCode"],
    indices = [
        Index("account"),
        Index("branchCode"),
        Index("shopName"),
        Index("currentRoute")
    ]
)
data class BranchEntity(
    val account: String,
    val branchCode: String,
    val shopName: String,
    val currentRoute: Int?,
    val rowNumber: Int?,
    val updatedAt: String?
)


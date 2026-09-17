package com.branchphotovault.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    indices = [
        Index("account", "branchCode"),
        Index("createdAt"),
        Index("monthKey")
    ]
)
data class PhotoEntity(
    @PrimaryKey
    val id: String,
    val account: String,
    val branchCode: String,
    val shopName: String,
    val routeAtCapture: Int?,
    val localMainPath: String,
    val localThumbPath: String,
    val createdAt: Long,
    val monthKey: String,
    val note: String? = null,
    val tag: String? = null
)

package com.branchphotovault.data.model

data class BranchListItem(
    val account: String,
    val branchCode: String,
    val shopName: String,
    val currentRoute: Int?,
    val rowNumber: Int?,
    val updatedAt: String?,
    val photoCount: Int
)


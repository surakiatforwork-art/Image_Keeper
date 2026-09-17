package com.branchphotovault.data.remote

import com.branchphotovault.data.local.BranchEntity
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val ok: Boolean,
    val action: String?,
    val message: String? = null,
    val timestamp: String? = null
)

@JsonClass(generateAdapter = true)
data class BranchesResponse(
    val ok: Boolean,
    val action: String?,
    val spreadsheetName: String? = null,
    val sheet: String? = null,
    val updatedAt: String? = null,
    val count: Int = 0,
    val items: List<BranchDto> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class MutationResponse(
    val ok: Boolean,
    val action: String? = null,
    val message: String? = null,
    val error: String? = null,
    val item: BranchDto? = null,
    val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class BranchDto(
    val account: String,
    val branchCode: String,
    val shopName: String,
    @Json(name = "route")
    val route: Int? = null,
    val rowNumber: Int? = null
)

fun BranchDto.toEntity(updatedAt: String?): BranchEntity {
    return BranchEntity(
        account = account.trim(),
        branchCode = branchCode.trim(),
        shopName = shopName.trim(),
        currentRoute = route,
        rowNumber = rowNumber,
        updatedAt = updatedAt
    )
}

fun MutationResponse.requireOkMessage(): String {
    return error ?: message ?: "The server rejected the request."
}

fun BranchesResponse.requireOkMessage(): String {
    return error ?: message ?: "Failed to load branches from the remote sheet."
}


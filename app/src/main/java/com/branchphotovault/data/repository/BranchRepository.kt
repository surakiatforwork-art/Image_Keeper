package com.branchphotovault.data.repository

import com.branchphotovault.data.local.BranchDao
import com.branchphotovault.data.local.BranchEntity
import com.branchphotovault.data.local.PhotoDao
import com.branchphotovault.data.model.BranchListItem
import com.branchphotovault.data.model.BranchSortOption
import com.branchphotovault.data.remote.AppsScriptService
import com.branchphotovault.data.remote.requireOkMessage
import com.branchphotovault.data.remote.toEntity
import com.branchphotovault.util.BranchQueryBuilder
import com.branchphotovault.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow

class BranchRepository(
    private val branchDao: BranchDao,
    private val photoDao: PhotoDao,
    private val service: AppsScriptService,
    private val settingsRepository: SettingsRepository
) {

    fun observeBranches(
        searchText: String,
        accountFilter: String?,
        sortOption: BranchSortOption
    ): Flow<List<BranchListItem>> {
        return branchDao.observeBranchListItems(
            BranchQueryBuilder.build(
                searchText = searchText,
                accountFilter = accountFilter,
                sortOption = sortOption
            )
        )
    }

    fun observeBranch(account: String, branchCode: String): Flow<BranchEntity?> {
        return branchDao.observeBranch(account = account, branchCode = branchCode)
    }

    fun observeAvailableRoutes(): Flow<List<Int>> = branchDao.observeAvailableRoutes()

    suspend fun syncBranches(): Int {
        val baseUrl = requireConfiguredBaseUrl()
        val response = service.branches(url = baseUrl)
        if (!response.ok) {
            throw IllegalStateException(response.requireOkMessage())
        }

        val entities = response.items.map { dto -> dto.toEntity(response.updatedAt ?: DateTimeUtils.nowFormatted()) }
        branchDao.replaceAll(entities)
        settingsRepository.setLastSyncAt(System.currentTimeMillis())
        return entities.size
    }

    suspend fun addBranch(
        account: String,
        branchCode: String,
        shopName: String,
        currentRoute: Int?
    ): BranchEntity {
        val normalizedAccount = account.trim()
        val normalizedBranchCode = branchCode.trim()
        val normalizedShopName = shopName.trim()

        val existing = branchDao.getBranch(
            account = normalizedAccount,
            branchCode = normalizedBranchCode
        )
        if (existing != null) {
            throw IllegalStateException("Branch already exists locally.")
        }

        val baseUrl = requireConfiguredBaseUrl()
        val response = service.addBranch(
            url = baseUrl,
            account = normalizedAccount,
            branchCode = normalizedBranchCode,
            shopName = normalizedShopName,
            route = currentRoute
        )
        if (!response.ok) {
            throw IllegalStateException(response.requireOkMessage())
        }

        val updatedAt = response.updatedAt ?: DateTimeUtils.nowFormatted()
        val entity = response.item?.toEntity(updatedAt) ?: BranchEntity(
            account = normalizedAccount,
            branchCode = normalizedBranchCode,
            shopName = normalizedShopName,
            currentRoute = currentRoute,
            rowNumber = null,
            updatedAt = updatedAt
        )
        branchDao.upsert(entity)
        return entity
    }

    suspend fun updateRoute(branch: BranchEntity, newRoute: Int?): BranchEntity {
        val baseUrl = requireConfiguredBaseUrl()
        val response = service.updateRoute(
            url = baseUrl,
            account = branch.account,
            branchCode = branch.branchCode,
            route = newRoute,
            rowNumber = branch.rowNumber
        )
        if (!response.ok) {
            throw IllegalStateException(response.requireOkMessage())
        }

        val updated = branch.copy(
            currentRoute = newRoute,
            updatedAt = response.updatedAt ?: DateTimeUtils.nowFormatted()
        )
        branchDao.upsert(updated)
        return updated
    }

    suspend fun healthCheck() {
        val baseUrl = requireConfiguredBaseUrl()
        val response = service.health(url = baseUrl)
        if (!response.ok) {
            throw IllegalStateException(response.message ?: "Remote health check failed.")
        }
    }

    private suspend fun requireConfiguredBaseUrl(): String {
        val baseUrl = settingsRepository.getBaseUrl().trim()
        if (baseUrl.isBlank() || baseUrl == "YOUR_APPS_SCRIPT_WEB_APP_URL_HERE") {
            throw IllegalStateException("Configure the Apps Script base URL in Settings before syncing.")
        }
        return baseUrl
    }
}

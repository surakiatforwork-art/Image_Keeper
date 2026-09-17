package com.branchphotovault.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.branchphotovault.data.model.BranchListItem
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BranchDao {

    @RawQuery(observedEntities = [BranchEntity::class, PhotoEntity::class])
    abstract fun observeBranchListItems(query: SupportSQLiteQuery): Flow<List<BranchListItem>>

    @Query(
        """
        SELECT * FROM branches
        WHERE account = :account AND branchCode = :branchCode
        LIMIT 1
        """
    )
    abstract fun observeBranch(account: String, branchCode: String): Flow<BranchEntity?>

    @Query(
        """
        SELECT * FROM branches
        WHERE account = :account AND branchCode = :branchCode
        LIMIT 1
        """
    )
    abstract suspend fun getBranch(account: String, branchCode: String): BranchEntity?

    @Query("SELECT DISTINCT currentRoute FROM branches WHERE currentRoute IS NOT NULL ORDER BY currentRoute")
    abstract fun observeAvailableRoutes(): Flow<List<Int>>

    @Upsert
    abstract suspend fun upsert(branch: BranchEntity)

    @Upsert
    abstract suspend fun upsertAll(branches: List<BranchEntity>)

    @Query("DELETE FROM branches")
    abstract suspend fun deleteAll()

    @Transaction
    open suspend fun replaceAll(branches: List<BranchEntity>) {
        deleteAll()
        upsertAll(branches)
    }
}


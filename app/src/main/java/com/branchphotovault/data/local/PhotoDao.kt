package com.branchphotovault.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY createdAt, id")
    suspend fun getAllForTransfer(): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertForTransfer(photo: PhotoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<PhotoEntity>)

    @Query(
        """
        SELECT * FROM photos
        WHERE account = :account AND branchCode = :branchCode
        ORDER BY createdAt DESC
        """
    )
    fun observePhotosForBranch(account: String, branchCode: String): Flow<List<PhotoEntity>>

    @Query(
        """
        SELECT * FROM photos
        WHERE account = :account AND branchCode = :branchCode
        ORDER BY createdAt DESC
        """
    )
    suspend fun getPhotosForBranch(account: String, branchCode: String): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :photoId LIMIT 1")
    suspend fun getPhotoById(photoId: String): PhotoEntity?

    @Query("SELECT * FROM photos WHERE id IN (:photoIds) ORDER BY createdAt DESC")
    suspend fun getPhotosByIds(photoIds: List<String>): List<PhotoEntity>

    @Query("DELETE FROM photos WHERE id IN (:photoIds)")
    suspend fun deleteByIds(photoIds: List<String>)

    @Query("SELECT * FROM photos WHERE createdAt < :cutoffEpochMillis")
    suspend fun getPhotosOlderThan(cutoffEpochMillis: Long): List<PhotoEntity>

    @Query("DELETE FROM photos WHERE createdAt < :cutoffEpochMillis")
    suspend fun deleteOlderThan(cutoffEpochMillis: Long): Int
}


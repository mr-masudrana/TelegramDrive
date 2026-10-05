package com.telegramdrive.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveFileDao {

    @Query("SELECT * FROM drive_files WHERE folderPath = :folderPath ORDER BY uploadedAtEpochSec DESC")
    fun observeFolder(folderPath: String): Flow<List<DriveFileEntity>>

    @Query("DELETE FROM drive_files")
    suspend fun clearAll()

    @Query("DELETE FROM drive_files WHERE folderPath = :folderPath")
    suspend fun clearFolder(folderPath: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: DriveFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(files: List<DriveFileEntity>)

    @androidx.room.Transaction
    suspend fun replaceFolder(folderPath: String, files: List<DriveFileEntity>) {
        clearFolder(folderPath)
        upsertAll(files)
    }

    @Query("DELETE FROM drive_files WHERE messageId = :messageId")
    suspend fun delete(messageId: Long)
}

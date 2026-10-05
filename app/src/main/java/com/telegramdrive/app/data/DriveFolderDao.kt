package com.telegramdrive.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveFolderDao {

    @Query("SELECT * FROM drive_folders WHERE parentPath = :parentPath ORDER BY name ASC")
    fun observeChildren(parentPath: String): Flow<List<DriveFolderEntity>>

    @Query("DELETE FROM drive_folders")
    suspend fun clearAll()

    @Query("DELETE FROM drive_folders WHERE parentPath = :parentPath")
    suspend fun clearChildren(parentPath: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(folders: List<DriveFolderEntity>)

    @Transaction
    suspend fun replaceChildren(parentPath: String, folders: List<DriveFolderEntity>) {
        clearChildren(parentPath)
        upsertAll(folders)
    }
}

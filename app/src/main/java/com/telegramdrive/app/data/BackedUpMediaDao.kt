package com.telegramdrive.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BackedUpMediaDao {

    @Query("SELECT mediaStoreId FROM backed_up_media WHERE mediaStoreId IN (:ids)")
    suspend fun filterAlreadyBackedUp(ids: List<Long>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markBackedUp(entity: BackedUpMediaEntity)
}

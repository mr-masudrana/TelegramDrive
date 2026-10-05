package com.telegramdrive.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Marks a MediaStore item as already uploaded, so the backup worker never re-sends it. */
@Entity(tableName = "backed_up_media")
data class BackedUpMediaEntity(
    @PrimaryKey val mediaStoreId: Long,
    val uploadedAtEpochSec: Long
)

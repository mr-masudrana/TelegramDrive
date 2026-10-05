package com.telegramdrive.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DriveFileEntity::class, DriveFolderEntity::class, BackedUpMediaEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun driveFileDao(): DriveFileDao
    abstract fun driveFolderDao(): DriveFolderDao
    abstract fun backedUpMediaDao(): BackedUpMediaDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "telegramdrive.db"
                )
                    .fallbackToDestructiveMigration() // ডেভেলপমেন্ট পর্যায়ে schema বদলালে পুরোনো DB রিসেট হবে
                    .build().also { instance = it }
            }
    }
}

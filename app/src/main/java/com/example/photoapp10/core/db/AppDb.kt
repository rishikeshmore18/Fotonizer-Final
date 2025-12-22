package com.example.photoapp10.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.photoapp10.core.db.convert.Converters
import com.example.photoapp10.feature.album.data.AlbumDao
import com.example.photoapp10.feature.album.data.AlbumEntity
import com.example.photoapp10.feature.photo.data.PhotoDao
import com.example.photoapp10.feature.photo.data.PhotoEntity

@Database(
    entities = [AlbumEntity::class, PhotoEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDb : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null

        // Migration from version 1 to 2: Add missing columns
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add any missing columns from version 1 to 2
                database.execSQL("ALTER TABLE photos ADD COLUMN favorite INTEGER")
                database.execSQL("ALTER TABLE photos ADD COLUMN caption TEXT")
                database.execSQL("ALTER TABLE photos ADD COLUMN tags TEXT")
                
                // Set default values for existing records
                database.execSQL("UPDATE photos SET favorite = 0 WHERE favorite IS NULL")
            }
        }

        // Migration from version 2 to 3: Add emoji column to albums table
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE albums ADD COLUMN emoji TEXT")
            }
        }

        // Migration from version 3 to 4: Add backup status fields
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add columns as nullable first
                database.execSQL("ALTER TABLE photos ADD COLUMN backedUpAt INTEGER")
                database.execSQL("ALTER TABLE albums ADD COLUMN backedUpAt INTEGER")
                
                // Set default values for existing records
                database.execSQL("UPDATE photos SET backedUpAt = 0 WHERE backedUpAt IS NULL")
                database.execSQL("UPDATE albums SET backedUpAt = 0 WHERE backedUpAt IS NULL")
            }
        }

        fun get(context: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    "photoapp10.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addTypeConverter(Converters())
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

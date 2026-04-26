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
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDb : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile private var instances = mutableMapOf<String, AppDb>()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE photos ADD COLUMN favorite INTEGER")
                database.execSQL("ALTER TABLE photos ADD COLUMN caption TEXT")
                database.execSQL("ALTER TABLE photos ADD COLUMN tags TEXT")
                database.execSQL("UPDATE photos SET favorite = 0 WHERE favorite IS NULL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE albums ADD COLUMN emoji TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE photos ADD COLUMN backedUpAt INTEGER")
                database.execSQL("ALTER TABLE albums ADD COLUMN backedUpAt INTEGER")
                database.execSQL("UPDATE photos SET backedUpAt = 0 WHERE backedUpAt IS NULL")
                database.execSQL("UPDATE albums SET backedUpAt = 0 WHERE backedUpAt IS NULL")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE albums ADD COLUMN parentId INTEGER")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_albums_parentId ON albums(parentId)")
            }
        }

        fun get(context: Context): AppDb = get(context, "photoapp10.db")

        fun get(context: Context, dbName: String): AppDb =
            instances[dbName] ?: synchronized(this) {
                instances[dbName] ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java,
                    dbName
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addTypeConverter(Converters())
                    .build()
                    .also { instances[dbName] = it }
            }

        fun closeAndRemove(dbName: String) {
            synchronized(this) {
                instances.remove(dbName)?.close()
            }
        }

        fun closeAll() {
            synchronized(this) {
                instances.values.forEach { it.close() }
                instances.clear()
            }
        }
    }
}

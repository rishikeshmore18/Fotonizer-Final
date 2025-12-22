package com.example.photoapp10.core.di

import android.app.Application
import android.content.Context
import com.example.photoapp10.core.db.AppDb
import com.example.photoapp10.core.file.AppStorage
import com.example.photoapp10.core.prefs.UserPrefs
import com.example.photoapp10.core.thumb.Thumbnailer
import com.example.photoapp10.feature.album.domain.AlbumRepository
import com.example.photoapp10.feature.auth.AuthManager
import com.example.photoapp10.feature.backup.DriveSyncManager
import com.example.photoapp10.feature.backup.domain.RealTimeArchiveManager
import com.example.photoapp10.feature.photo.domain.PhotoRepository

object Modules {

    // Lazily initialized singletons
    private var appDb: AppDb? = null
    private var appStorage: AppStorage? = null
    private var thumbnailer: Thumbnailer? = null
    private var syncManager: DriveSyncManager? = null
    private var archiveManager: RealTimeArchiveManager? = null

    private var albumRepo: AlbumRepository? = null
    private var photoRepo: PhotoRepository? = null

    fun provideDb(context: Context): AppDb =
        appDb ?: AppDb.get(context.applicationContext).also { appDb = it }

    fun provideStorage(context: Context): AppStorage =
        appStorage ?: AppStorage(context.applicationContext).also { appStorage = it }

    fun provideThumbnailer(): Thumbnailer =
        thumbnailer ?: Thumbnailer().also { thumbnailer = it }

    fun provideUserPrefs(context: Context): UserPrefs = UserPrefs

    fun provideAuthManager(): AuthManager = AuthManager

    fun provideDriveSyncManager(context: Context): DriveSyncManager =
        syncManager ?: DriveSyncManager(context.applicationContext as Application).also { syncManager = it }

    fun provideRealTimeArchiveManager(context: Context): RealTimeArchiveManager =
        archiveManager ?: RealTimeArchiveManager(
            context = context.applicationContext,
            db = provideDb(context),
            storage = provideStorage(context),
            thumbnailer = provideThumbnailer()
        ).also { archiveManager = it }

    fun provideAlbumRepository(context: Context): AlbumRepository {
        if (albumRepo == null) {
            val db = provideDb(context)
            val sync = provideDriveSyncManager(context)
            val archive = provideRealTimeArchiveManager(context)
            albumRepo = AlbumRepository(db.albumDao(), sync, archive)
        }
        return albumRepo ?: throw IllegalStateException("AlbumRepository not initialized")
    }

    fun providePhotoRepository(context: Context): PhotoRepository {
        if (photoRepo == null) {
            val db = provideDb(context)
            val sync = provideDriveSyncManager(context)
            val archive = provideRealTimeArchiveManager(context)
            photoRepo = PhotoRepository(
                photoDao = db.photoDao(),
                albumDao = db.albumDao(),
                storage = provideStorage(context),
                thumbnailer = provideThumbnailer(),
                syncManager = sync,
                archiveManager = archive
            )
        }
        return photoRepo ?: throw IllegalStateException("PhotoRepository not initialized")
    }
}




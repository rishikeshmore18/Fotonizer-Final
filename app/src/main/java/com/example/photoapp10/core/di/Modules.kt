package com.example.photoapp10.core.di

import android.app.Application
import android.content.Context
import com.example.photoapp10.core.account.AccountScopeManager
import com.example.photoapp10.core.account.TempModeManager
import com.example.photoapp10.core.db.AppDb
import com.example.photoapp10.core.file.AppStorage
import com.example.photoapp10.core.prefs.UserPrefs
import com.example.photoapp10.core.thumb.Thumbnailer
import com.example.photoapp10.feature.album.domain.AlbumRepository
import com.example.photoapp10.feature.auth.AuthManager
import com.example.photoapp10.feature.backup.CloudArchiveQueueManager
import com.example.photoapp10.feature.backup.DriveSyncManager
import com.example.photoapp10.feature.backup.domain.RealTimeArchiveManager
import com.example.photoapp10.feature.photo.domain.PhotoRepository
import timber.log.Timber

object Modules {

    // Lazily initialized singletons — rebuilt on account change
    private var appDb: AppDb? = null
    private var currentDbName: String? = null
    private var appStorage: AppStorage? = null
    private var thumbnailer: Thumbnailer? = null
    private var syncManager: DriveSyncManager? = null
    private var archiveQueueManager: CloudArchiveQueueManager? = null
    private var archiveManager: RealTimeArchiveManager? = null

    private var albumRepo: AlbumRepository? = null
    private var photoRepo: PhotoRepository? = null

    /**
     * Close the current DB and clear all cached singletons so the next
     * access rebuilds them for the newly-active account.
     */
    fun resetForAccountChange() {
        Timber.i("Modules: Resetting singletons for account change")
        currentDbName?.let { AppDb.closeAndRemove(it) }
        appDb = null
        currentDbName = null
        appStorage = null
        albumRepo = null
        photoRepo = null
        syncManager = null
        archiveQueueManager = null
        archiveManager = null
    }

    fun provideDb(context: Context): AppDb {
        val accountId = AccountScopeManager.getActiveAccountId(context)
        val dbName = AccountScopeManager.getDbName(accountId)
        if (currentDbName != null && currentDbName != dbName) {
            resetForAccountChange()
        }
        return appDb ?: AppDb.get(context.applicationContext, dbName).also {
            appDb = it
            currentDbName = dbName
        }
    }

    fun provideStorage(context: Context): AppStorage {
        val accountId = AccountScopeManager.getActiveAccountId(context)
        return appStorage ?: AppStorage(context.applicationContext, accountId).also { appStorage = it }
    }

    fun provideThumbnailer(): Thumbnailer =
        thumbnailer ?: Thumbnailer().also { thumbnailer = it }

    fun provideUserPrefs(context: Context): UserPrefs = UserPrefs

    fun provideAuthManager(): AuthManager = AuthManager

    fun provideDriveSyncManager(context: Context): DriveSyncManager =
        syncManager ?: DriveSyncManager(context.applicationContext as Application).also { syncManager = it }

    fun provideCloudArchiveQueueManager(context: Context): CloudArchiveQueueManager =
        archiveQueueManager ?: CloudArchiveQueueManager(context.applicationContext as Application).also {
            archiveQueueManager = it
        }

    fun provideRealTimeArchiveManager(context: Context): RealTimeArchiveManager =
        archiveManager ?: RealTimeArchiveManager(
            context = context.applicationContext,
            db = provideDb(context),
            storage = provideStorage(context),
            thumbnailer = provideThumbnailer(),
            queueManager = provideCloudArchiveQueueManager(context)
        ).also { archiveManager = it }

    fun provideAlbumRepository(context: Context): AlbumRepository {
        if (albumRepo == null) {
            val db = provideDb(context)
            val tempMode = TempModeManager.isTempMode(context)
            val sync = if (tempMode) null else provideDriveSyncManager(context)
            val archive = if (tempMode) null else provideRealTimeArchiveManager(context)
            albumRepo = AlbumRepository(db.albumDao(), sync, archive)
        }
        return albumRepo ?: throw IllegalStateException("AlbumRepository not initialized")
    }

    fun providePhotoRepository(context: Context): PhotoRepository {
        if (photoRepo == null) {
            val db = provideDb(context)
            val tempMode = TempModeManager.isTempMode(context)
            val sync = if (tempMode) null else provideDriveSyncManager(context)
            val archive = if (tempMode) null else provideRealTimeArchiveManager(context)
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



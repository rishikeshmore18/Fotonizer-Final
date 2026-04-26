package com.example.photoapp10.feature.backup.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.photoapp10.core.account.AccountScopeManager
import com.example.photoapp10.core.account.TempModeManager
import com.example.photoapp10.core.db.AppDb
import com.example.photoapp10.core.file.AppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class TempModeCleanupWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Timber.i("TempModeCleanupWorker: Starting expired content cleanup")

            val tempNamespaces = findTempNamespaces()
            if (tempNamespaces.isEmpty()) {
                Timber.d("TempModeCleanupWorker: No temp namespaces found, nothing to clean")
                return@withContext Result.success()
            }

            val cutoff = System.currentTimeMillis() - TempModeManager.EXPIRY_MS
            var totalPurged = 0

            tempNamespaces.forEach { namespace ->
                totalPurged += purgeExpiredContent(namespace, cutoff)
            }

            Timber.i("TempModeCleanupWorker: Cleanup complete, purged $totalPurged items")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "TempModeCleanupWorker: Cleanup failed")
            Result.retry()
        }
    }

    private suspend fun purgeExpiredContent(namespace: String, cutoff: Long): Int {
        val dbName = AccountScopeManager.getDbName(namespace)
        val dbFile = applicationContext.getDatabasePath(dbName)
        if (!dbFile.exists()) return 0

        val db = AppDb.get(applicationContext, dbName)
        val storage = AppStorage(applicationContext, namespace)
        val expired = db.photoDao().getAllPhotos().filter { it.createdAt < cutoff }

        if (expired.isEmpty()) {
            Timber.d("TempModeCleanupWorker: No expired content for namespace=$namespace")
            return 0
        }

        Timber.i("TempModeCleanupWorker: Purging ${expired.size} expired items for namespace=$namespace")

        expired.forEach { photo ->
            try {
                val ext = photo.filename.substringAfterLast('.', "jpg")
                val photoFile = storage.photoFile(photo.albumId, photo.id, ext)
                val thumbFile = storage.thumbFile(photo.albumId, photo.id)
                if (photoFile.exists()) photoFile.delete()
                if (thumbFile.exists()) thumbFile.delete()
                db.photoDao().deleteById(photo.id)
            } catch (e: Exception) {
                Timber.e(e, "TempModeCleanupWorker: Failed to delete photo ${photo.id} in namespace=$namespace")
            }
        }

        val affectedAlbumIds = expired.map { it.albumId }.distinct()
        for (albumId in affectedAlbumIds) {
            val remaining = db.photoDao().countInAlbum(albumId)
            if (remaining == 0) {
                db.albumDao().getById(albumId)?.let { album ->
                    db.albumDao().delete(album)
                    storage.deleteAlbumFiles(albumId)
                    Timber.d("TempModeCleanupWorker: Removed empty album ${album.name} in namespace=$namespace")
                }
            } else {
                db.albumDao().updateCounts(albumId, remaining, System.currentTimeMillis())
            }
        }

        return expired.size
    }

    private fun findTempNamespaces(): List<String> {
        val namespaces = linkedSetOf<String>()

        val databaseDir = applicationContext.getDatabasePath("placeholder").parentFile
        databaseDir?.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(".db") }
            ?.mapNotNull { dbFileToNamespace(it) }
            ?.forEach { namespaces += it }

        val accountsDir = File(applicationContext.filesDir, "accounts")
        accountsDir.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && TempModeManager.isTempNamespace(it.name) }
            ?.map { it.name }
            ?.forEach { namespaces += it }

        return namespaces.toList()
    }

    private fun dbFileToNamespace(file: File): String? {
        val prefix = "photoapp10_"
        val suffix = ".db"
        val name = file.name
        if (!name.startsWith(prefix) || !name.endsWith(suffix)) return null

        val namespace = name.removePrefix(prefix).removeSuffix(suffix)
        return namespace.takeIf { TempModeManager.isTempNamespace(it) }
    }

    companion object {
        private const val WORK_NAME = "temp_mode_cleanup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TempModeCleanupWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(
                    Constraints.Builder().setRequiresBatteryNotLow(true).build()
                )
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )

            Timber.i("TempModeCleanupWorker: Scheduled daily cleanup")
        }
    }
}

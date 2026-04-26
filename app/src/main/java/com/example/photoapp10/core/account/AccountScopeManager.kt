package com.example.photoapp10.core.account

import android.content.Context
import timber.log.Timber
import java.io.File

object AccountScopeManager {

    private const val PREFS_NAME = "account_scope"
    private const val KEY_ACTIVE_ACCOUNT = "active_account_id"
    private const val KEY_MIGRATION_DONE = "default_data_migrated"

    private const val DEFAULT_DB_NAME = "photoapp10.db"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getActiveAccountId(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE_ACCOUNT, null)

    fun setActiveAccount(context: Context, accountId: String) {
        val prev = getActiveAccountId(context)
        prefs(context).edit().putString(KEY_ACTIVE_ACCOUNT, accountId).apply()
        if (prev != accountId) {
            Timber.i("AccountScopeManager: Active account changed from $prev to $accountId")
        }
    }

    fun clearActiveAccount(context: Context) {
        Timber.i("AccountScopeManager: Clearing active account (sign-out)")
        prefs(context).edit().remove(KEY_ACTIVE_ACCOUNT).apply()
    }

    fun getDbName(accountId: String?): String =
        if (accountId.isNullOrBlank()) DEFAULT_DB_NAME
        else "photoapp10_$accountId.db"

    fun getAccountStorageDir(context: Context, accountId: String?): File =
        if (accountId.isNullOrBlank()) context.applicationContext.filesDir
        else File(context.applicationContext.filesDir, "accounts/$accountId")

    /**
     * Migrate existing default DB and photo/thumb directories to the first
     * account that signs in after the multi-account update is installed.
     * Safe to call multiple times — runs only once.
     */
    fun migrateDefaultDataIfNeeded(context: Context, accountId: String) {
        val p = prefs(context)
        if (p.getBoolean(KEY_MIGRATION_DONE, false)) return

        val ctx = context.applicationContext
        val oldDb = ctx.getDatabasePath(DEFAULT_DB_NAME)

        if (!oldDb.exists()) {
            p.edit().putBoolean(KEY_MIGRATION_DONE, true).apply()
            Timber.d("AccountScopeManager: No default DB found — nothing to migrate")
            return
        }

        Timber.i("AccountScopeManager: Migrating default data to account $accountId")

        try {
            val newDbName = getDbName(accountId)
            val newDb = ctx.getDatabasePath(newDbName)

            oldDb.renameTo(newDb)
            File(oldDb.path + "-wal").let { if (it.exists()) it.renameTo(File(newDb.path + "-wal")) }
            File(oldDb.path + "-shm").let { if (it.exists()) it.renameTo(File(newDb.path + "-shm")) }
            File(oldDb.path + "-journal").let { if (it.exists()) it.renameTo(File(newDb.path + "-journal")) }
            Timber.d("AccountScopeManager: DB renamed to $newDbName")
        } catch (e: Exception) {
            Timber.e(e, "AccountScopeManager: Failed to rename DB — account starts fresh")
        }

        try {
            val accountDir = getAccountStorageDir(ctx, accountId)
            accountDir.mkdirs()

            val oldPhotos = File(ctx.filesDir, "photos")
            val oldThumbs = File(ctx.filesDir, "thumbs")

            if (oldPhotos.exists()) oldPhotos.renameTo(File(accountDir, "photos"))
            if (oldThumbs.exists()) oldThumbs.renameTo(File(accountDir, "thumbs"))
            Timber.d("AccountScopeManager: Photo/thumb dirs moved to ${accountDir.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "AccountScopeManager: Failed to move storage dirs — account starts fresh")
        }

        p.edit().putBoolean(KEY_MIGRATION_DONE, true).apply()
        Timber.i("AccountScopeManager: Migration complete")
    }

    fun hasLocalData(context: Context, accountId: String): Boolean {
        val dbFile = context.applicationContext.getDatabasePath(getDbName(accountId))
        return dbFile.exists()
    }
}

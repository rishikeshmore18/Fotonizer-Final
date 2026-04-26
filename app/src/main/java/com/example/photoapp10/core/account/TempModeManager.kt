package com.example.photoapp10.core.account

import android.content.Context
import com.example.photoapp10.core.di.Modules
import timber.log.Timber
import java.io.File

object TempModeManager {

    private const val PREFS = "temp_mode_prefs"
    private const val KEY_TEMP_MODE = "is_temp_mode"
    private const val KEY_PRIMARY_ACCOUNT = "primary_account_id"

    const val TEMP_NAMESPACE = "__temp_mode__"
    const val EXPIRY_DAYS = 7
    const val EXPIRY_MS = EXPIRY_DAYS.toLong() * 24 * 60 * 60 * 1000

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isTempMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TEMP_MODE, false)

    fun getPrimaryAccountId(context: Context): String? =
        prefs(context).getString(KEY_PRIMARY_ACCOUNT, null)

    fun getTempNamespaceForAccount(accountId: String?): String =
        if (accountId.isNullOrBlank()) TEMP_NAMESPACE else "${TEMP_NAMESPACE}_$accountId"

    fun isTempNamespace(accountId: String?): Boolean =
        !accountId.isNullOrBlank() && accountId.startsWith(TEMP_NAMESPACE)

    fun enterTempMode(context: Context) {
        val currentAccount = AccountScopeManager.getActiveAccountId(context)
        val tempNamespace = getTempNamespaceForAccount(currentAccount)
        migrateLegacyTempNamespaceIfNeeded(context, tempNamespace)
        prefs(context).edit()
            .putBoolean(KEY_TEMP_MODE, true)
            .putString(KEY_PRIMARY_ACCOUNT, currentAccount)
            .apply()
        Modules.resetForAccountChange()
        AccountScopeManager.setActiveAccount(context, tempNamespace)
        Timber.i("TempModeManager: Entered temp mode (primary=$currentAccount, namespace=$tempNamespace)")
    }

    fun exitTempMode(context: Context) {
        val primaryAccount = getPrimaryAccountId(context)
        prefs(context).edit()
            .putBoolean(KEY_TEMP_MODE, false)
            .remove(KEY_PRIMARY_ACCOUNT)
            .apply()
        Modules.resetForAccountChange()
        if (primaryAccount != null) {
            AccountScopeManager.setActiveAccount(context, primaryAccount)
        }
        Timber.i("TempModeManager: Exited temp mode (restored=$primaryAccount)")
    }

    fun ensureTempModeScope(context: Context) {
        if (!isTempMode(context)) return

        val primaryAccount = getPrimaryAccountId(context)
        val expectedNamespace = getTempNamespaceForAccount(primaryAccount)
        val activeAccount = AccountScopeManager.getActiveAccountId(context)

        if (activeAccount == expectedNamespace) return

        migrateLegacyTempNamespaceIfNeeded(context, expectedNamespace)
        Modules.resetForAccountChange()
        AccountScopeManager.setActiveAccount(context, expectedNamespace)
        Timber.i(
            "TempModeManager: Reconciled temp mode namespace (primary=$primaryAccount, " +
                "from=$activeAccount, to=$expectedNamespace)"
        )
    }

    private fun migrateLegacyTempNamespaceIfNeeded(context: Context, targetNamespace: String) {
        if (targetNamespace == TEMP_NAMESPACE) return

        val appContext = context.applicationContext
        val legacyDb = appContext.getDatabasePath(AccountScopeManager.getDbName(TEMP_NAMESPACE))
        val targetDb = appContext.getDatabasePath(AccountScopeManager.getDbName(targetNamespace))
        val legacyStorage = AccountScopeManager.getAccountStorageDir(appContext, TEMP_NAMESPACE)
        val targetStorage = AccountScopeManager.getAccountStorageDir(appContext, targetNamespace)

        val hasLegacyData = legacyDb.exists() || legacyStorage.exists()
        val hasTargetData = targetDb.exists() || targetStorage.exists()
        if (!hasLegacyData || hasTargetData) return

        Timber.i("TempModeManager: Migrating legacy temp namespace to $targetNamespace")

        moveFileIfExists(legacyDb, targetDb)
        moveFileIfExists(File(legacyDb.path + "-wal"), File(targetDb.path + "-wal"))
        moveFileIfExists(File(legacyDb.path + "-shm"), File(targetDb.path + "-shm"))
        moveFileIfExists(File(legacyDb.path + "-journal"), File(targetDb.path + "-journal"))
        moveDirectoryIfExists(legacyStorage, targetStorage)
    }

    private fun moveFileIfExists(source: File, target: File) {
        if (!source.exists() || target.exists()) return
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            Timber.w("TempModeManager: Failed to move ${source.absolutePath} to ${target.absolutePath}")
        }
    }

    private fun moveDirectoryIfExists(source: File, target: File) {
        if (!source.exists() || target.exists()) return
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            Timber.w("TempModeManager: Failed to move ${source.absolutePath} to ${target.absolutePath}")
        }
    }
}

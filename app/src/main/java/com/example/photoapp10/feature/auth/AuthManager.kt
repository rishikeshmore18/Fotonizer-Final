package com.example.photoapp10.feature.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AuthManager {
    private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    private fun gso() = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DRIVE_SCOPE))
        .build()

    fun getSignInIntent(ctx: Context) =
        GoogleSignIn.getClient(ctx, gso()).signInIntent

    fun isSignedIn(ctx: Context) =
        GoogleSignIn.getLastSignedInAccount(ctx) != null

    fun getLastAccount(ctx: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(ctx)

    /**
     * Get the user's display name for greeting purposes.
     * Fallback order: displayName -> givenName -> email prefix -> "User"
     */
    fun getUserDisplayName(ctx: Context): String {
        val account = getLastAccount(ctx) ?: return "User"
        
        return when {
            !account.displayName.isNullOrBlank() -> account.displayName ?: "User"
            !account.givenName.isNullOrBlank() -> account.givenName ?: "User"
            !account.email.isNullOrBlank() -> {
                val emailPrefix = account.email?.substringBefore("@")
                if (emailPrefix?.isNotBlank() == true) emailPrefix else "User"
            }
            else -> "User"
        }
    }

    fun signOut(context: Context, onComplete: () -> Unit = {}) {
        GoogleSignIn.getClient(context, gso()).signOut().addOnCompleteListener { onComplete() }
    }

    /** Build Drive service using GoogleAuthUtil (Android-only approach) */
    suspend fun buildDriveService(ctx: Context): Drive? = withContext(Dispatchers.IO) {
        return@withContext try {
            val account = getLastAccount(ctx) ?: run {
                Log.e("AuthManager", "No Google account; user not signed in")
                return@withContext null
            }
            
            Log.d("AuthManager", "Building Drive service for ${account.email}")
            
            // Get access token using GoogleAuthUtil
            val accountObj = account.account ?: return@withContext null
            val token = GoogleAuthUtil.getToken(
                ctx,
                accountObj,
                "oauth2:$DRIVE_SCOPE"
            )
            
            // Create Drive service with token
            val credential = GoogleCredential().setAccessToken(token)
            
            val drive = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("PhotoApp10")
                .build()
            
            Log.d("AuthManager", "Successfully built Drive service")
            drive
            
        } catch (e: Exception) {
            Log.e("AuthManager", "Failed to build Drive service", e)
            null
        }
    }

    /** Refresh Drive service with new token - call this when getting 401 errors */
    suspend fun refreshDriveService(ctx: Context, oldDrive: Drive?): Drive? = withContext(Dispatchers.IO) {
        return@withContext try {
            val account = getLastAccount(ctx) ?: run {
                Log.e("AuthManager", "No Google account; user not signed in")
                return@withContext null
            }
            
            Log.d("AuthManager", "Refreshing Drive service for ${account.email}")
            
            // Force refresh the token
            val accountObj = account.account ?: return@withContext null
            val token = GoogleAuthUtil.getToken(
                ctx,
                accountObj,
                "oauth2:$DRIVE_SCOPE"
            )
            
            // Create new Drive service with refreshed token
            val credential = GoogleCredential().setAccessToken(token)
            
            val drive = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("PhotoApp10")
                .build()
            
            Log.d("AuthManager", "Successfully refreshed Drive service")
            drive
            
        } catch (e: Exception) {
            Log.e("AuthManager", "Failed to refresh Drive service", e)
            null
        }
    }

    /** Get access token for Drive REST API calls (legacy method - now returns null) */
    suspend fun getAccessToken(ctx: Context): String? {
        Log.w("AuthManager", "getAccessToken() is deprecated - use buildDriveService() instead")
        return null
    }
}
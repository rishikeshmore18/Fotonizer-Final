package com.example.photoapp10

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.feature.album.ui.AlbumsScreen
import com.example.photoapp10.feature.album.ui.AlbumDetailScreen
import com.example.photoapp10.feature.auth.AuthManager
import com.example.photoapp10.feature.auth.ui.SignInScreen
import com.example.photoapp10.feature.auth.ui.RestoreGateScreen
import com.example.photoapp10.feature.auth.ui.RestoreSyncScreen
import com.example.photoapp10.feature.backup.ui.SimpleBackupScreen
import com.example.photoapp10.feature.photo.ui.CameraScreen
import com.example.photoapp10.feature.photo.ui.PhotoDetailScreen
import com.example.photoapp10.feature.search.ui.SearchScreen
import com.example.photoapp10.feature.settings.ui.SettingsScreen
import timber.log.Timber

@Composable
fun AppNav(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val userPrefs = remember { Modules.provideUserPrefs(context) }
    
    // Collect preferences state without blocking UI
    val rememberDevice by userPrefs.rememberDeviceFlow(context).collectAsState(initial = true)
    val restoreGateShown by userPrefs.restoreGateShownFlow(context).collectAsState(initial = false)
    
    // Check authentication status once and determine start destination
    val startDestination by remember {
        derivedStateOf {
            val isAuthenticated = AuthManager.isSignedIn(context)
            
            when {
                !isAuthenticated -> "signin"
                rememberDevice && restoreGateShown -> "albums" // Skip RestoreGate if already shown
                rememberDevice && !restoreGateShown -> "restore_gate" // First login, show RestoreGate
                else -> "signin"
            }
        }
    }
    
    Timber.i("AppNav: Starting with destination: $startDestination")
    
    NavHost(navController = navController, startDestination = startDestination) {
        composable("signin") {
            Timber.i("AppNav: Navigating to SignInScreen")
            SignInScreen(navController)
        }
        composable("restore_gate") {
            Timber.i("AppNav: Navigating to RestoreGateScreen")
            RestoreGateScreen(navController)
        }
        composable("albums") {
            Timber.i("AppNav: Navigating to AlbumsScreen")
            AlbumsScreen(navController)
        }
        composable(
            route = "album/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
            AlbumDetailScreen(albumId = albumId, nav = navController)
        }
        composable(
            route = "camera/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
            CameraScreen(navController = navController, albumId = albumId)
        }
        // Camera route removed - using native camera intents instead
        composable(
            route = "photo/{photoId}/{albumId}",
            arguments = listOf(
                navArgument("photoId"){ type = NavType.LongType },
                navArgument("albumId"){ type = NavType.LongType }
            )
        ) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
            PhotoDetailScreen(photoId = photoId, albumId = albumId, nav = navController)
        }
        composable(
            route = "photo/favorites/{photoId}",
            arguments = listOf(navArgument("photoId"){ type = NavType.LongType })
        ) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L
            PhotoDetailScreen(photoId = photoId, albumId = -1L, nav = navController, context = "favorites")
        }
        composable(
            route = "photo/recents/{photoId}",
            arguments = listOf(navArgument("photoId"){ type = NavType.LongType })
        ) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getLong("photoId") ?: 0L
            PhotoDetailScreen(photoId = photoId, albumId = -1L, nav = navController, context = "recents")
        }
        composable(
            route = "photo_detail/{photoPath}",
            arguments = listOf(navArgument("photoPath") { type = NavType.StringType })
        ) { backStackEntry ->
            val photoPath = backStackEntry.arguments?.getString("photoPath") ?: ""
            val decodedPath = java.net.URLDecoder.decode(photoPath, "UTF-8")
            // For now, we'll need to find the photo by path or create a temporary PhotoDetailScreen
            // This is a workaround until we can get the proper photoId
            PhotoDetailScreen(photoId = 0L, albumId = 0L, nav = navController, photoPath = decodedPath)
        }
        composable("search") {
            SearchScreen(navController, albumId = null)
        }
        composable(
            route = "search/query/{query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType })
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query") ?: ""
            SearchScreen(navController, albumId = null, initialQuery = query)
        }
        composable(
            route = "search/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
            SearchScreen(navController, albumId = albumId)
        }
        composable("backup") {
            Timber.i("AppNav: Navigating to SimpleBackupScreen")
            SimpleBackupScreen()
        }
        composable("restore_sync") {
            Timber.i("AppNav: Navigating to RestoreSyncScreen")
            RestoreSyncScreen(navController)
        }
        composable("settings") {
            SettingsScreen(navController)
        }
    }
}


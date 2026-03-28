package com.example.photoapp10.feature.settings.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.photoapp10.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("About Us") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // App Icon
            Icon(
                imageVector = Icons.Default.Info, // Using Info icon as placeholder if app icon resource isn't easily available, or R.mipmap.ic_launcher if possible
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // App Name
            Text(
                text = "Foto-Nizer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // Version
            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Description
            Text(
                text = "Foto-Nizer is your smart photo organization assistant. " +
                        "Capture, store, and backup your memories with ease. " +
                        "Enjoy features like smart albums, cloud sync, and secure storage for all your photos and videos.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(48.dp))

            Divider()

            // Privacy Policy
            ListItem(
                headlineContent = { Text("Privacy Policy") },
                modifier = Modifier.clickable {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.PRIVACY_POLICY_URL))
                    context.startActivity(browserIntent)
                },
                trailingContent = {
                    Icon(painter = painterResource(android.R.drawable.ic_menu_view), contentDescription = null)
                }
            )

            Divider()

            // Terms & Conditions
            ListItem(
                headlineContent = { Text("Terms & Conditions") },
                modifier = Modifier.clickable {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.TERMS_URL))
                    context.startActivity(browserIntent)
                },
                trailingContent = {
                    Icon(painter = painterResource(android.R.drawable.ic_menu_view), contentDescription = null)
                }
            )
            
            Divider()

            ListItem(
                headlineContent = { Text("Delete Account") },
                supportingContent = { Text("Learn how to remove app access and associated data") },
                modifier = Modifier.clickable {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(LegalLinks.DELETE_ACCOUNT_URL))
                    context.startActivity(browserIntent)
                },
                trailingContent = {
                    Icon(painter = painterResource(android.R.drawable.ic_menu_view), contentDescription = null)
                }
            )

            Divider()
        }
    }
}

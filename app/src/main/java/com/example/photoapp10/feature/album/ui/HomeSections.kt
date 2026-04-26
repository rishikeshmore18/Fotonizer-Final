package com.example.photoapp10.feature.album.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.photoapp10.core.util.MediaPreviewResolver
import com.example.photoapp10.feature.photo.data.PhotoEntity

@Composable
fun HomeSections(
    nav: NavController,
    vm: HomeSectionsViewModel = viewModel()
) {
    val favorites by vm.favorites.collectAsState()
    val recents by vm.recents.collectAsState()
    val albums  by vm.albums.collectAsState()
    val lastSearch by vm.lastQ.collectAsState()

    Column(Modifier.fillMaxSize()) {
        AlbumSearchBar(nav = nav)

        // Recent search section - redesigned as "Recent Search" = "result" in one row
        if (lastSearch.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { 
                            navigateToSearch(nav, lastSearch, albumId = null)
                        },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Search",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "=",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "\"$lastSearch\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_revert),
                        contentDescription = "Tap to search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Existing Albums grid screen should be shown below/alongside sections.
        SectionTitle("Favorites")
        if (favorites.isEmpty()) {
            SectionEmpty("No favorites yet")
        } else {
            PhotosRow(photos = favorites) { p -> nav.navigate("photo/favorites/${p.id}") }
        }

        Spacer(Modifier.height(12.dp))

        SectionTitle("Recents")
        if (recents.isEmpty()) {
            SectionEmpty("No recent photos")
        } else {
            PhotosRow(photos = recents) { p -> nav.navigate("photo/recents/${p.id}") }
        }

        Spacer(Modifier.height(16.dp))

        // You can place Albums grid under this, or call your AlbumsScreen here.
        // Example:
        // AlbumsGrid(items = albums, onOpen = { nav.navigate("album/${it.id}") }, ...)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

@Composable
private fun PhotosRow(
    photos: List<PhotoEntity>,
    onClick: (PhotoEntity) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(photos, key = { it.id }) { p ->
            val previewPath = remember(p.thumbPath, p.path) {
                MediaPreviewResolver.resolvePreviewPath(p.thumbPath, p.path)
            }
            AsyncImage(
                model = previewPath,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onClick(p) }
            )
        }
    }
}

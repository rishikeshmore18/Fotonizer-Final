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
import com.example.photoapp10.feature.photo.data.PhotoEntity
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun HomeSections(
    nav: NavController,
    vm: HomeSectionsViewModel = viewModel()
) {
    val favorites by vm.favorites.collectAsState()
    val recents by vm.recents.collectAsState()
    val albums  by vm.albums.collectAsState()
    val lastSearch by vm.lastQ.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        // Search bar at the top
        Card(
            modifier = Modifier
                .fillMaxWidth() // Full width end-to-end
                .padding(horizontal = 12.dp, vertical = 2.dp), // Reduced vertical padding for thin appearance
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp), // Reduced vertical padding for thin appearance
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_search),
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp) // Smaller search icon for thin bar
                )
                Spacer(modifier = Modifier.width(6.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search albums and photos") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                val encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.toString())
                                nav.navigate("search/query/$encodedQuery")
                            }
                        }
                    )
                )
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = { 
                            val encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8.toString())
                            nav.navigate("search/query/$encodedQuery")
                        },
                        modifier = Modifier.size(28.dp) // Smaller send button for thin bar
                    ) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_send),
                            contentDescription = "Search",
                            modifier = Modifier.size(14.dp) // Smaller send icon for thin bar
                        )
                    }
                }
            }
        }

        // Recent search section - redesigned as "Recent Search" = "result" in one row
        if (lastSearch.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable { 
                        val encodedQuery = URLEncoder.encode(lastSearch, StandardCharsets.UTF_8.toString())
                        nav.navigate("search/query/$encodedQuery") 
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
            AsyncImage(
                model = p.thumbPath.ifBlank { p.path },
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onClick(p) }
            )
        }
    }
}

package com.example.photoapp10.feature.album.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun AlbumSearchBar(
    nav: NavController,
    albumId: Long? = null,
    placeholder: String = if (albumId == null) "Search albums and photos" else "Search this album"
) {
    val currentBackStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route.orEmpty()
    var searchFieldValue by remember(albumId) {
        mutableStateOf(TextFieldValue("", selection = TextRange.Zero))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_search),
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            TextField(
                value = searchFieldValue,
                onValueChange = { newValue ->
                    val previousQuery = searchFieldValue.text.trim()
                    searchFieldValue = newValue
                    val nextQuery = newValue.text.trim()
                    if (previousQuery.isBlank() && nextQuery.isNotBlank() && !currentRoute.startsWith("search")) {
                        navigateToSearch(nav, newValue.text, albumId)
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder) },
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
                        if (searchFieldValue.text.isNotBlank()) {
                            navigateToSearch(nav, searchFieldValue.text, albumId)
                        }
                    }
                )
            )
            if (searchFieldValue.text.isNotBlank()) {
                IconButton(
                    onClick = { navigateToSearch(nav, searchFieldValue.text, albumId) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_send),
                        contentDescription = "Search",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

fun navigateToSearch(nav: NavController, query: String, albumId: Long? = null) {
    val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
    val route = if (albumId == null) {
        "search/query/$encodedQuery"
    } else {
        "search/$albumId/query/$encodedQuery"
    }
    nav.navigate(route)
}

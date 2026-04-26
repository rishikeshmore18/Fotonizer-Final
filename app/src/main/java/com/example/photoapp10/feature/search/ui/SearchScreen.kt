package com.example.photoapp10.feature.search.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.photoapp10.core.di.Modules
import com.example.photoapp10.core.util.MediaPreviewResolver
import com.example.photoapp10.feature.album.data.AlbumEntity
import com.example.photoapp10.feature.photo.data.PhotoEntity
import com.example.photoapp10.feature.photo.domain.SortMode
import com.example.photoapp10.feature.search.domain.SearchUseCase
import com.example.photoapp10.feature.settings.data.UserPrefs
import java.net.URLDecoder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    nav: NavController,
    albumId: Long? = null,
    initialQuery: String = ""
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: SearchViewModel = viewModel(
        factory = SearchViewModel.factory(app, albumId, initialQuery)
    )
    val photoResults by vm.photoResults.collectAsState()
    val albumResults by vm.albumResults.collectAsState()
    val allAlbums by vm.allAlbums.collectAsState()
    val sort by vm.sort.collectAsState()
    val query by vm.query.collectAsState()
    var queryFieldValue by remember(query) {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }
    val albumsById = remember(allAlbums) { allAlbums.associateBy { it.id } }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(query) {
        if (query != queryFieldValue.text) {
            queryFieldValue = TextFieldValue(query, selection = TextRange(query.length))
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (albumId == null) "Search" else "Search in album") },
                navigationIcon = {
                    IconButton(onClick = { nav.navigateUp() }) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_revert),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (albumId == null) {
                        DropdownMenuWithSort(current = sort, onChange = vm::setSort)
                    }
                }
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            TextField(
                value = queryFieldValue,
                onValueChange = { newValue ->
                    queryFieldValue = newValue
                    vm.setQuery(newValue.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                placeholder = {
                    Text(
                        if (albumId == null) "Search albums and photos"
                        else "Search photos in album"
                    )
                }
            )

            val hasResults = photoResults.isNotEmpty() || (albumId == null && albumResults.isNotEmpty())
            if (!hasResults && query.isNotBlank()) {
                EmptyHelp()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (albumId == null && albumResults.isNotEmpty()) {
                        item {
                            Text(
                                "Albums",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(albumResults, key = { "album_${it.id}" }) { album ->
                            AlbumRow(
                                album = album,
                                path = buildAlbumPath(album.id, albumsById),
                                onClick = { nav.navigate("album/${album.id}") }
                            )
                        }
                        if (photoResults.isNotEmpty()) {
                            item {
                                Text(
                                    "Photos",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    items(photoResults, key = { "photo_${it.id}" }) { photo ->
                        SearchRow(
                            photo = photo,
                            albumPath = buildAlbumPath(photo.albumId, albumsById),
                            onClick = { nav.navigate("photo/${photo.id}/${photo.albumId}") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(album: AlbumEntity, path: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val photoRepo = Modules.providePhotoRepository(app)
    var coverImagePath by remember(album.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(album.id, album.coverPhotoId) {
        try {
            if (album.id > 0) {
                val coverPhotoId = album.coverPhotoId
                if (coverPhotoId != null && coverPhotoId > 0) {
                    val photo = photoRepo.getPhoto(coverPhotoId)
                    if (photo != null) {
                        coverImagePath = MediaPreviewResolver.resolvePreviewPath(photo.thumbPath, photo.path)
                    }
                }
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
        } catch (_: Exception) {
            coverImagePath = null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val currentCoverPath = coverImagePath
        if (currentCoverPath != null && currentCoverPath.isNotBlank()) {
            AsyncImage(
                model = currentCoverPath,
                contentDescription = "Album cover",
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_gallery),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
        }

        Column(Modifier.weight(1f)) {
            Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${album.photoCount} photos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchRow(photo: PhotoEntity, albumPath: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val previewPath = remember(photo.thumbPath, photo.path) {
            MediaPreviewResolver.resolvePreviewPath(photo.thumbPath, photo.path)
        }
        AsyncImage(
            model = previewPath,
            contentDescription = null,
            modifier = Modifier.size(64.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(photo.filename, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                albumPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val captionText = if (photo.caption.isBlank()) "-" else photo.caption
            Text(
                captionText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (photo.favorite) {
            Icon(
                painter = painterResource(android.R.drawable.btn_star_big_on),
                contentDescription = null
            )
        }
    }
}

@Composable
private fun EmptyHelp() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No results", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tips: try a different word, check spelling, or search by a tag/emoji.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DropdownMenuWithSort(current: SortMode, onChange: (SortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            painter = painterResource(android.R.drawable.arrow_down_float),
            contentDescription = "Sort"
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(text = { Text("Name A-Z") }, onClick = { expanded = false; onChange(SortMode.NAME_ASC) })
        DropdownMenuItem(text = { Text("Name Z-A") }, onClick = { expanded = false; onChange(SortMode.NAME_DESC) })
        DropdownMenuItem(text = { Text("Date newest") }, onClick = { expanded = false; onChange(SortMode.DATE_NEW) })
        DropdownMenuItem(text = { Text("Date oldest") }, onClick = { expanded = false; onChange(SortMode.DATE_OLD) })
        DropdownMenuItem(text = { Text("Favorites first") }, onClick = { expanded = false; onChange(SortMode.FAV_FIRST) })
    }
}

class SearchViewModel(
    app: Application,
    private val albumId: Long?,
    initialQuery: String = ""
) : AndroidViewModel(app) {

    private val photoRepo = Modules.providePhotoRepository(app)
    private val albumRepo = Modules.provideAlbumRepository(app)
    private val useCase = SearchUseCase(photoRepo)
    private val userPrefs = UserPrefs(app)

    private val _query = MutableStateFlow(initialQuery)
    val query: StateFlow<String> = _query

    private val _sort = userPrefs.sortFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SortMode.DATE_NEW
    )
    val sort: StateFlow<SortMode> = _sort

    val photoResults: StateFlow<List<PhotoEntity>> =
        useCase.execute(_query, _sort, albumId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAlbums: StateFlow<List<AlbumEntity>> =
        albumRepo.observeAllAlbums()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val albumResults: StateFlow<List<AlbumEntity>> =
        if (albumId == null) {
            _query
                .map { it.trim() }
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { queryText ->
                    if (queryText.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        albumRepo.searchAlbums("%${queryText}%")
                    }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        } else {
            MutableStateFlow(emptyList<AlbumEntity>()).asStateFlow()
        }

    fun setQuery(s: String) {
        _query.value = s
        if (s.isNotBlank()) {
            viewModelScope.launch { userPrefs.setLastSearch(s) }
        }
    }

    fun setSort(mode: SortMode) {
        viewModelScope.launch { userPrefs.setSort(mode) }
    }

    companion object {
        fun factory(
            app: Application,
            albumId: Long?,
            initialQuery: String = ""
        ) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(
                    app = app,
                    albumId = albumId,
                    initialQuery = runCatching { URLDecoder.decode(initialQuery, "UTF-8") }
                        .getOrDefault(initialQuery)
                ) as T
            }
        }
    }
}

private fun buildAlbumPath(albumId: Long, albumsById: Map<Long, AlbumEntity>): String {
    val names = mutableListOf<String>()
    val visited = mutableSetOf<Long>()
    var currentId: Long? = albumId

    while (currentId != null && visited.add(currentId)) {
        val album = albumsById[currentId] ?: break
        names.add(album.name)
        currentId = album.parentId
    }

    return names.asReversed().joinToString(" / ").ifBlank { "Unknown album" }
}

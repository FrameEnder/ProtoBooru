package com.frameender.protobooru.ui.search

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.data.ReverseSearchResult
import com.frameender.protobooru.data.StaticPostSource
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.RemoteImage
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ImageSearchViewModel(handle: SavedStateHandle) : ViewModel() {
    private val api = Graph.api
    private val fromPost: Int? = handle.get<String>("post")?.toIntOrNull()

    /** Anything Coil can display: a content Uri or a resolved URL. */
    var preview by mutableStateOf<Any?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var result by mutableStateOf<ReverseSearchResult?>(null)
        private set
    private var handledPost = false

    fun startFromPostIfNeeded() {
        val id = fromPost ?: return
        if (handledPost) return
        handledPost = true
        run(previewModel = null) {
            val p = api.post(id)
            preview = api.resolve(p.thumbnailUrl)
            val url = api.resolve(p.contentUrl) ?: throw IllegalStateException("Post has no file")
            api.fetchBytes(url) to (p.mimeType ?: "image/jpeg")
        }
    }

    fun searchUri(context: Context, uri: Uri) {
        run(previewModel = uri) {
            withContext(Dispatchers.IO) {
                val cr = context.contentResolver
                val mime = cr.getType(uri) ?: "image/jpeg"
                val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Could not read image")
                bytes to mime
            }
        }
    }

    private fun run(previewModel: Any?, load: suspend () -> Pair<ByteArray, String>) {
        if (previewModel != null) preview = previewModel
        busy = true
        error = null
        result = null
        viewModelScope.launch {
            try {
                val (bytes, mime) = load()
                result = api.reverseSearch(bytes, mime)
            } catch (e: Exception) {
                error = e.message ?: "Search failed"
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun ImageSearchScreen(onBack: () -> Unit, onOpenPost: (Int) -> Unit, vm: ImageSearchViewModel = viewModel()) {
    val context = LocalContext.current
    val pending by Graph.pendingSharedImage.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.searchUri(context, uri)
    }

    LaunchedEffect(Unit) { vm.startFromPostIfNeeded() }
    LaunchedEffect(pending) {
        pending?.let {
            Graph.pendingSharedImage.value = null
            vm.searchUri(context, it)
        }
    }

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = { TopAppBar(title = { Text("Image search") }, navigationIcon = { BackButton(onBack) }) },
    ) { pad ->
        val res = vm.result
        val posts: List<Pair<Post, Double?>> = buildList {
            res?.exactPost?.let { add(it to null) }
            res?.similarPosts?.forEach { s -> if (s.post.id != res?.exactPost?.id) add(s.post to s.distance) }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Surface(shape = RoundedCornerShape(12.dp), color = Ink.Surface, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(96.dp).clip(RoundedCornerShape(8.dp))) {
                                if (vm.preview != null) {
                                    AsyncImage(vm.preview, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Icon(Icons.Default.AddPhotoAlternate, null, tint = Ink.TextDim, modifier = Modifier.align(Alignment.Center).size(40.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Find this image on your booru", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Uses Szurubooru's duplicate detection. You can also share an image to ProtoBooru from any app.",
                                    style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    enabled = !vm.busy,
                                ) { Text("Pick image") }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    when {
                        vm.busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Searching…", color = Ink.TextDim)
                        }
                        vm.error != null -> Text(vm.error!!, color = Ink.Red)
                        res != null && posts.isEmpty() -> Text("No matching or similar posts.", color = Ink.TextDim)
                        res != null -> Text(
                            if (res.exactPost != null) "Exact match found · ${posts.size - 1} similar" else "${posts.size} similar posts",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            items(posts, key = { it.first.id }) { (p, dist) ->
                Box(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                ) {
                    Surface(onClick = {
                        Graph.viewerSource = StaticPostSource(posts.map { it.first.id })
                        onOpenPost(p.id)
                    }, modifier = Modifier.fillMaxSize()) {
                        RemoteImage(p.thumbnailUrl, Modifier.fillMaxSize())
                    }
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(4.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dist == null) {
                            Icon(Icons.Default.CheckCircle, "Exact", tint = Ink.Green, modifier = Modifier.size(16.dp))
                            Text(" exact", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        } else {
                            Text(
                                String.format(Locale.US, "%.0f%%", (1.0 - dist) * 100),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                
                            )
                        }
                    }
                }
            }
        }
    }
}

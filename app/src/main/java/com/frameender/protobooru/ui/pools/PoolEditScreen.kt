package com.frameender.protobooru.ui.pools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.MicroPost
import com.frameender.protobooru.data.Pool
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.ErrorBox
import com.frameender.protobooru.ui.common.LoadingBox
import com.frameender.protobooru.ui.common.PostIdDialog
import com.frameender.protobooru.ui.common.RemoteImage
import com.frameender.protobooru.ui.common.SectionHeader
import com.frameender.protobooru.ui.common.TagEditor
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.categoryColor
import kotlinx.coroutines.launch

class PoolEditViewModel(handle: SavedStateHandle) : ViewModel() {
    private val api = Graph.api
    val id: Int? = handle.get<String>("id")?.toIntOrNull()
    val isNew: Boolean = id == null

    var pool by mutableStateOf<Pool?>(null)
        private set
    var loading by mutableStateOf(!isNew)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    var names by mutableStateOf<List<String>>(emptyList())
    var category by mutableStateOf(Graph.poolCategories.value.values.firstOrNull { it.default }?.name ?: "default")
    var description by mutableStateOf("")
    var posts by mutableStateOf<List<MicroPost>>(emptyList())

    init { if (!isNew) load() }

    fun load() {
        val pid = id ?: return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val p = api.pool(pid)
                pool = p
                names = p.names
                category = p.category
                description = p.description.orEmpty()
                posts = p.posts
            } catch (e: Exception) {
                error = e.message ?: "Could not load pool"
            } finally {
                loading = false
            }
        }
    }

    fun move(index: Int, delta: Int) {
        val to = index + delta
        if (to !in posts.indices) return
        val l = posts.toMutableList()
        val item = l.removeAt(index)
        l.add(to, item)
        posts = l
    }

    fun save(onSaved: (Int) -> Unit) {
        if (names.isEmpty()) { Graph.toast("A pool needs a name"); return }
        saving = true
        viewModelScope.launch {
            try {
                val p = pool
                val ids = posts.map { it.id }
                val saved = if (p == null) {
                    api.createPool(names, category, description, ids)
                } else {
                    api.updatePool(p, names = names, category = category, description = description, posts = ids)
                }
                Graph.poolChanged.tryEmit(saved.id)
                Graph.toast(if (p == null) "Created pool #${saved.id}" else "Saved pool #${saved.id}")
                onSaved(saved.id)
            } catch (e: Exception) {
                Graph.toast(e.message ?: "Save failed")
            } finally {
                saving = false
            }
        }
    }
}

@Composable
fun PoolEditScreen(onBack: () -> Unit, onSaved: (Int) -> Unit, vm: PoolEditViewModel = viewModel()) {
    val cats by Graph.poolCategories.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var bulkIds by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New pool" else "Edit pool #${vm.id}") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (vm.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    TextButton(onClick = { vm.save(onSaved) }, enabled = !vm.saving && !vm.loading && vm.names.isNotEmpty()) {
                        Text(if (vm.isNew) "Create" else "Save")
                    }
                },
            )
        },
    ) { pad ->
        when {
            vm.loading -> LoadingBox(Modifier.padding(pad))
            vm.error != null -> ErrorBox(vm.error!!, Modifier.padding(pad), onRetry = vm::load)
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp)) {
                item {
                    SectionHeader("Names")
                    TagEditor(vm.names, { vm.names = it }, label = "Add name")
                }
                item {
                    SectionHeader("Category")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cats.values.forEach { c ->
                            FilterChip(
                                selected = vm.category == c.name,
                                onClick = { vm.category = c.name },
                                label = { Text(c.name) },
                                leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColor(c.color))) },
                            )
                        }
                    }
                }
                item {
                    SectionHeader("Description")
                    OutlinedTextField(vm.description, { vm.description = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
                }
                item {
                    SectionHeader("Posts · ${vm.posts.size}", trailing = {
                        IconButton(onClick = { adding = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, "Add post", tint = Ink.Amber) }
                    })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            bulkIds, { bulkIds = it }, label = { Text("Add IDs (e.g. 12 15 20-24)") }, singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(enabled = bulkIds.isNotBlank(), onClick = {
                            val ids = parseIds(bulkIds).filter { id -> vm.posts.none { it.id == id } }
                            vm.posts = vm.posts + ids.map { MicroPost(it, null) }
                            bulkIds = ""
                        }) { Text("Add") }
                    }
                    Text("Order here is the reading order. Use the arrows to rearrange.", style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
                    Spacer(Modifier.height(8.dp))
                }
                itemsIndexed(vm.posts, key = { _, p -> p.id }) { i, p ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim, modifier = Modifier.width(32.dp))
                        if (p.thumbnailUrl != null) RemoteImage(p.thumbnailUrl, Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)))
                        else Box(Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(Ink.Surface3))
                        Spacer(Modifier.width(10.dp))
                        Text("#${p.id}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.move(i, -1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { vm.move(i, 1) }, enabled = i < vm.posts.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                        IconButton(onClick = { vm.posts = vm.posts.filterNot { it.id == p.id } }) { Icon(Icons.Default.Close, "Remove", tint = Ink.TextDim) }
                    }
                    HorizontalDivider(color = Ink.Line.copy(alpha = 0.5f))
                }
            }
        }
    }

    if (adding) {
        PostIdDialog(title = "Add post to pool", confirmLabel = "Add", onDismiss = { adding = false }) { p ->
            if (vm.posts.none { it.id == p.id }) vm.posts = vm.posts + MicroPost(p.id, p.thumbnailUrl)
        }
    }
}

/** "12 15, 20-24" -> [12, 15, 20, 21, 22, 23, 24] */
fun parseIds(text: String): List<Int> =
    text.split(Regex("[\\s,]+")).filter { it.isNotBlank() }.flatMap { part ->
        val range = part.split('-')
        if (range.size == 2) {
            val a = range[0].removePrefix("#").toIntOrNull()
            val b = range[1].removePrefix("#").toIntOrNull()
            if (a != null && b != null && b >= a && b - a < 1000) (a..b).toList() else emptyList()
        } else {
            listOfNotNull(part.removePrefix("#").toIntOrNull())
        }
    }.distinct()

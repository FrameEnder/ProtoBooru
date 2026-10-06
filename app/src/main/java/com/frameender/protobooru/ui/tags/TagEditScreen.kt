package com.frameender.protobooru.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Tag
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.ErrorBox
import com.frameender.protobooru.ui.common.LoadingBox
import com.frameender.protobooru.ui.common.SectionHeader
import com.frameender.protobooru.ui.common.TagCategoryCache
import com.frameender.protobooru.ui.common.TagEditor
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.categoryColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TagEditViewModel(handle: SavedStateHandle) : ViewModel() {
    private val api = Graph.api
    val originalName: String = handle.get<String>("name").orEmpty()
    val isNew: Boolean = originalName.isBlank()

    var tag by mutableStateOf<Tag?>(null)
        private set
    var loading by mutableStateOf(!isNew)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    var names by mutableStateOf<List<String>>(emptyList())
    var category by mutableStateOf(
        Graph.tagCategories.value.values.firstOrNull { it.default }?.name ?: "default",
    )
    var description by mutableStateOf("")
    var implications by mutableStateOf<List<String>>(emptyList())
    var suggestions by mutableStateOf<List<String>>(emptyList())

    init { if (!isNew) load() }

    fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val t = api.tag(originalName)
                tag = t
                names = t.names
                category = t.category
                description = t.description.orEmpty()
                implications = t.implications.map { it.name }
                suggestions = t.suggestions.map { it.name }
                (t.implications + t.suggestions).forEach { TagCategoryCache.learn(it.name, it.category) }
            } catch (e: Exception) {
                error = e.message ?: "Could not load tag"
            } finally {
                loading = false
            }
        }
    }

    fun save(onSaved: (String) -> Unit) {
        if (names.isEmpty()) { Graph.toast("A tag needs at least one name"); return }
        saving = true
        viewModelScope.launch {
            try {
                val t = tag
                val saved = if (t == null) {
                    api.createTag(names, category, description, implications, suggestions)
                } else {
                    api.updateTag(t, names, category, description, implications, suggestions)
                }
                TagCategoryCache.learn(saved.name, saved.category)
                if (t != null) Graph.tagChanged.tryEmit(t.name to saved.name)
                Graph.toast(if (t == null) "Created ${saved.name}" else "Saved ${saved.name}")
                onSaved(saved.name)
            } catch (e: Exception) {
                Graph.toast(e.message ?: "Save failed")
            } finally {
                saving = false
            }
        }
    }
}

@Composable
fun TagEditScreen(onBack: () -> Unit, onSaved: (String) -> Unit, vm: TagEditViewModel = viewModel()) {
    val cats by Graph.tagCategories.collectAsState()
    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New tag" else "Edit ${vm.originalName}") },
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
                    Text("The first name is the main one; the rest are aliases.", style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
                    Spacer(Modifier.height(6.dp))
                    TagEditor(vm.names, { vm.names = it }, label = "Add name")
                }
                item {
                    SectionHeader("Category")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        cats.values.sortedBy { it.order }.forEach { c ->
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
                    OutlinedTextField(vm.description, { vm.description = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 8)
                }
                item {
                    SectionHeader("Implications")
                    Text("Tagging a post with this tag also adds these.", style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
                    Spacer(Modifier.height(6.dp))
                    TagEditor(vm.implications, { vm.implications = it }, label = "Implied tag")
                }
                item {
                    SectionHeader("Suggestions")
                    Text("Offered (not added) when this tag is used.", style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
                    Spacer(Modifier.height(6.dp))
                    TagEditor(vm.suggestions, { vm.suggestions = it }, label = "Suggested tag")
                }
            }
        }
    }
}

/** Pick a target tag by name (with autocomplete) to merge into. */
@Composable
fun TagMergeDialog(source: Tag, onDismiss: () -> Unit, onMerged: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var target by remember { mutableStateOf<Tag?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(query) {
        if (query.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(220)
        results = runCatching { Graph.api.suggestTags(query.trim(), 8) }.getOrDefault(emptyList()).filter { it.name != source.name }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge ${source.name} into…") },
        text = {
            Column {
                OutlinedTextField(query, { query = it; target = null }, label = { Text("Target tag") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                results.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { target = t; query = t.name; results = emptyList() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.name, modifier = Modifier.weight(1f))
                        Text(Format.count(t.usages), style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${source.name} is removed; its posts, aliases and relations move to the target.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = target != null && !busy, onClick = {
                val t = target ?: return@TextButton
                busy = true
                scope.launch {
                    try {
                        // Fetch fresh versions; the merge endpoint checks both.
                        val src = Graph.api.tag(source.name)
                        val dst = Graph.api.tag(t.name)
                        val merged = Graph.api.mergeTags(src, dst)
                        Graph.tagChanged.tryEmit(source.name to merged.name)
                        Graph.toast("Merged into ${merged.name}")
                        onDismiss()
                        onMerged(merged.name)
                    } catch (e: Exception) {
                        Graph.toast(e.message ?: "Merge failed")
                    } finally {
                        busy = false
                    }
                }
            }) { Text("Merge", color = Ink.Red) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Ink.Surface2,
    )
}

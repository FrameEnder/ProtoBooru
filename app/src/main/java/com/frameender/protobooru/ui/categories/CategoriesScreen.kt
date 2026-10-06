package com.frameender.protobooru.ui.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.ColorField
import com.frameender.protobooru.ui.common.ConfirmDialog
import com.frameender.protobooru.ui.common.EmptyBox
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.categoryColor
import kotlinx.coroutines.launch

/** Common view of a tag or pool category. */
private data class Cat(val name: String, val color: String, val usages: Int, val order: Int?, val isDefault: Boolean)

/**
 * Manage tag categories ([kind] = "tag") or pool categories ([kind] = "pool"):
 * create, rename, recolor, reorder (tags), set default, delete.
 */
@Composable
fun CategoriesScreen(kind: String, onBack: () -> Unit) {
    val isTag = kind == "tag"
    val tagCats by Graph.tagCategories.collectAsState()
    val poolCats by Graph.poolCategories.collectAsState()
    val settings by Graph.settings.collectAsState()
    val scope = rememberCoroutineScope()

    val cats: List<Cat> = if (isTag) {
        tagCats.values.sortedBy { it.order }.map { Cat(it.name, it.color, it.usages, it.order, it.default) }
    } else {
        poolCats.values.sortedBy { it.name }.map { Cat(it.name, it.color, it.usages, null, it.default) }
    }
    val prefix = if (isTag) "tag_categories" else "pool_categories"
    val canCreate = settings.loggedIn && Graph.can("$prefix:create")
    val canEdit = settings.loggedIn && (Graph.can("$prefix:edit:name") || Graph.can("$prefix:edit:color"))
    val canDelete = settings.loggedIn && Graph.can("$prefix:delete")
    val canDefault = settings.loggedIn && Graph.can("$prefix:set_default")

    var editing by remember { mutableStateOf<Cat?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Cat?>(null) }

    fun act(label: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                Graph.refreshCategories()
                Graph.toast(label)
            } catch (e: Exception) {
                Graph.toast(e.message ?: "Failed")
            }
        }
    }

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = { TopAppBar(title = { Text(if (isTag) "Tag categories" else "Pool categories") }, navigationIcon = { BackButton(onBack) }) },
        floatingActionButton = {
            if (canCreate) FloatingActionButton(onClick = { creating = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, "New category")
            }
        },
    ) { pad ->
        if (cats.isEmpty()) {
            EmptyBox("No categories loaded.", Modifier.padding(pad))
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cats, key = { it.name }) { c ->
                    var menu by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { if (canEdit) editing = c },
                        shape = RoundedCornerShape(10.dp),
                        color = Ink.Surface,
                        border = BorderStroke(1.dp, Ink.Line),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(categoryColor(c.color)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.name, style = MaterialTheme.typography.titleMedium, color = categoryColor(c.color))
                                    if (c.isDefault) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Default.Star, "Default", tint = Ink.Amber, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(
                                    listOfNotNull("${c.usages} uses", c.order?.let { "order $it" }, c.color).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall, color = Ink.TextDim,
                                )
                            }
                            if (canDefault || canDelete) {
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More", tint = Ink.TextDim) }
                                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                        if (canDefault && !c.isDefault) DropdownMenuItem(text = { Text("Make default") }, onClick = {
                                            menu = false
                                            act("${c.name} is now the default") {
                                                if (isTag) Graph.api.setDefaultTagCategory(Graph.tagCategories.value.getValue(c.name))
                                                else Graph.api.setDefaultPoolCategory(Graph.poolCategories.value.getValue(c.name))
                                            }
                                        })
                                        if (canDelete) DropdownMenuItem(
                                            text = { Text("Delete", color = Ink.Red) },
                                            enabled = c.usages == 0 && !c.isDefault,
                                            onClick = { menu = false; deleting = c },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Categories in use (or the default) can't be deleted. Move their tags first.",
                        style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        val c = editing
        var name by remember(c) { mutableStateOf(c?.name ?: "") }
        var color by remember(c) { mutableStateOf(c?.color ?: "#7db8b5") }
        var order by remember(c) { mutableStateOf((c?.order ?: (cats.mapNotNull { it.order }.maxOrNull() ?: 0) + 1).toString()) }
        AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(if (c == null) "New category" else "Edit ${c.name}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(name, { name = it.trim() }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    ColorField(color) { color = it.trim() }
                    if (isTag) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            order, { order = it.filter(Char::isDigit) }, label = { Text("Order") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && color.isNotBlank(), onClick = {
                    val o = order.toIntOrNull() ?: 0
                    act(if (c == null) "Created $name" else "Saved $name") {
                        if (isTag) {
                            if (c == null) Graph.api.createTagCategory(name, color, o)
                            else Graph.api.updateTagCategory(Graph.tagCategories.value.getValue(c.name), name, color, o)
                        } else {
                            if (c == null) Graph.api.createPoolCategory(name, color)
                            else Graph.api.updatePoolCategory(Graph.poolCategories.value.getValue(c.name), name, color)
                        }
                    }
                    creating = false
                    editing = null
                }) { Text(if (c == null) "Create" else "Save") }
            },
            dismissButton = { TextButton(onClick = { creating = false; editing = null }) { Text("Cancel") } },
            containerColor = Ink.Surface2,
        )
    }

    deleting?.let { c ->
        ConfirmDialog(
            title = "Delete category ${c.name}?",
            text = "This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleting = null },
            onConfirm = {
                act("Deleted ${c.name}") {
                    if (isTag) Graph.api.deleteTagCategory(Graph.tagCategories.value.getValue(c.name))
                    else Graph.api.deletePoolCategory(Graph.poolCategories.value.getValue(c.name))
                }
            },
        )
    }
}

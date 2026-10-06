package com.frameender.protobooru.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.UserToken
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.ConfirmDialog
import com.frameender.protobooru.ui.common.EmptyBox
import com.frameender.protobooru.ui.common.ErrorBox
import com.frameender.protobooru.ui.common.LoadingBox
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.post.copyText
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.Mono
import kotlinx.coroutines.launch

class TokensViewModel : ViewModel() {
    private val api = Graph.api
    private val user get() = Graph.settings.value.username

    var tokens by mutableStateOf<List<UserToken>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var created by mutableStateOf<UserToken?>(null)

    init { load() }

    fun load() {
        error = null
        viewModelScope.launch {
            try {
                tokens = api.tokens(user).results.sortedByDescending { it.creationTime ?: "" }
            } catch (e: Exception) {
                error = e.message ?: "Could not load tokens"
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); load() } catch (e: Exception) { Graph.toast(e.message ?: "Action failed") }
        }
    }

    fun create(note: String, days: Long?) = act {
        created = api.createToken(user, note, days?.let { Format.isoInDays(it) })
    }

    fun setEnabled(t: UserToken, enabled: Boolean) = act { api.updateToken(user, t, enabled, t.note.orEmpty()) }

    fun rename(t: UserToken, note: String) = act { api.updateToken(user, t, t.enabled, note) }

    fun delete(t: UserToken) = act {
        api.deleteToken(user, t)
        Graph.toast("Token deleted")
    }
}

@Composable
fun TokensScreen(onBack: () -> Unit, vm: TokensViewModel = viewModel()) {
    var creating by remember { mutableStateOf(false) }
    val current = Graph.settings.value.token

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = { TopAppBar(title = { Text("Login tokens") }, navigationIcon = { BackButton(onBack) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New token") },
                containerColor = MaterialTheme.colorScheme.primary,
            )
        },
    ) { pad ->
        val list = vm.tokens
        when {
            list == null && vm.error != null -> ErrorBox(vm.error!!, Modifier.padding(pad), onRetry = vm::load)
            list == null -> LoadingBox(Modifier.padding(pad))
            list.isEmpty() -> EmptyBox("No tokens.", Modifier.padding(pad))
            else -> LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.token }) { t -> TokenCard(t, isCurrent = t.token == current, vm = vm) }
            }
        }
    }

    if (creating) CreateTokenDialog(onDismiss = { creating = false }) { note, days -> vm.create(note, days) }

    vm.created?.let { t ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { vm.created = null },
            title = { Text("Token created") },
            text = {
                Column {
                    Text("Use it with your username in other apps or scripts:", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer { Text(t.token, fontFamily = Mono, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = { TextButton(onClick = { copyText(context, t.token); vm.created = null }) { Text("Copy & close") } },
            dismissButton = { TextButton(onClick = { vm.created = null }) { Text("Close") } },
            containerColor = Ink.Surface2,
        )
    }
}

@Composable
private fun TokenCard(t: UserToken, isCurrent: Boolean, vm: TokensViewModel) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Ink.Surface,
        border = BorderStroke(1.dp, if (isCurrent) MaterialTheme.colorScheme.primary else Ink.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.note?.ifBlank { null } ?: "(no note)", style = MaterialTheme.typography.titleMedium)
                    if (isCurrent) Text("THIS DEVICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Switch(checked = t.enabled, onCheckedChange = { vm.setEnabled(t, it) }, enabled = !isCurrent)
            }
            Spacer(Modifier.height(6.dp))
            Text(t.token.take(8) + "…" + t.token.takeLast(4), fontFamily = Mono, style = MaterialTheme.typography.labelMedium, color = Ink.TextDim)
            Spacer(Modifier.height(6.dp))
            Text(
                "Created ${Format.date(t.creationTime)} · last used ${Format.ago(t.lastUsageTime)}" +
                    (t.expirationTime?.let { " · expires ${Format.date(it)}" } ?: " · never expires"),
                style = MaterialTheme.typography.labelSmall, color = Ink.TextDim,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { copyText(context, t.token) }) { Icon(Icons.Default.ContentCopy, "Copy token", tint = Ink.TextDim) }
                IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Edit note", tint = Ink.TextDim) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = Ink.Red) }
            }
        }
    }
    if (renaming) {
        var note by remember { mutableStateOf(t.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Token note") },
            text = { OutlinedTextField(note, { note = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { renaming = false; vm.rename(t, note) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
            containerColor = Ink.Surface2,
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete token?",
            text = if (isCurrent) "This is the token this app is signed in with. Deleting it will sign you out." else "Anything using this token will stop working.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                vm.delete(t)
                if (isCurrent) Graph.logout(revokeToken = false)
            },
        )
    }
}

@Composable
private fun CreateTokenDialog(onDismiss: () -> Unit, onCreate: (String, Long?) -> Unit) {
    var note by remember { mutableStateOf("") }
    var days by remember { mutableStateOf<Long?>(null) }
    val options = listOf<Pair<String, Long?>>("Never" to null, "1 week" to 7L, "1 month" to 30L, "1 year" to 365L)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New login token") },
        text = {
            Column {
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("Expires", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { (label, d) ->
                        FilterChip(selected = days == d, onClick = { days = d }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(note.ifBlank { "ProtoBooru" }, days); onDismiss() }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Ink.Surface2,
    )
}

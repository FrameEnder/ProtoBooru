package com.frameender.protobooru.ui.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.UriRequestBody
import com.frameender.protobooru.data.User
import com.frameender.protobooru.data.uriInfo
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.users.UserProfileBody
import kotlinx.coroutines.launch

class AccountNav(
    val searchPosts: (String) -> Unit,
    val comments: (String) -> Unit,
    val history: (String) -> Unit,
    val users: () -> Unit,
    val tokens: () -> Unit,
    val similar: () -> Unit,
    val settings: () -> Unit,
    val upload: () -> Unit,
)

@Composable
fun AccountScreen(nav: AccountNav) {
    val settings by Graph.settings.collectAsState()
    val me by Graph.me.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = {
            TopAppBar(
                title = { Text(if (settings.loggedIn) settings.username else "Account") },
                actions = {
                    IconButton(onClick = nav.settings) { Icon(Icons.Default.Settings, "Settings") }
                    if (settings.loggedIn) {
                        IconButton(onClick = { confirmLogout = true }) { Icon(Icons.AutoMirrored.Filled.Logout, "Log out") }
                    }
                },
            )
        },
    ) { pad ->
        when {
            !settings.loggedIn -> LoginForm(Modifier.padding(pad), nav)
            me == null -> Column(Modifier.padding(pad).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Loading profile…", color = Ink.TextDim)
                TextButton(onClick = { Graph.refreshServerState() }) { Text("Retry") }
            }
            else -> UserProfileBody(
                u = me!!,
                modifier = Modifier.padding(pad),
                onSearchPosts = nav.searchPosts,
                onComments = nav.comments,
                onHistory = nav.history,
                extra = { AccountActions(nav, onEdit = { editing = true }) },
            )
        }
    }

    if (confirmLogout) {
        var revoke by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(revoke, { revoke = it })
                    Text("Also delete this device's login token on the server")
                }
            },
            confirmButton = { TextButton(onClick = { confirmLogout = false; Graph.logout(revoke) }) { Text("Log out", color = Ink.Red) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
            containerColor = Ink.Surface2,
        )
    }
    if (editing && me != null) EditProfileDialog(me!!) { editing = false }
}

@Composable
private fun AccountActions(nav: AccountNav, onEdit: () -> Unit) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (Graph.can("posts:create:identified")) {
            ActionTile("Upload posts", Icons.Default.CloudUpload, Modifier.fillMaxWidth(), nav.upload)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("Edit profile", Icons.Default.Edit, Modifier.weight(1f), onEdit)
            ActionTile("Login tokens", Icons.Default.Key, Modifier.weight(1f), nav.tokens)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("All comments", Icons.Default.Comment, Modifier.weight(1f)) { nav.comments("") }
            ActionTile("Users", Icons.Default.Group, Modifier.weight(1f), nav.users)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("Image search", Icons.Default.ImageSearch, Modifier.weight(1f), nav.similar)
            if (Graph.can("snapshots:list")) {
                ActionTile("Site history", Icons.Default.History, Modifier.weight(1f)) { nav.history("") }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ActionTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Ink.Surface,
        border = BorderStroke(1.dp, Ink.Line),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

// ===================== Login =====================

@Composable
private fun LoginForm(modifier: Modifier, nav: AccountNav) {
    val settings by Graph.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var mode by remember { mutableIntStateOf(0) } // 0 = password, 1 = existing token
    var user by remember { mutableStateOf(settings.username) }
    var secret by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Sign in", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (settings.configured) settings.root else "No server set — open Settings first.",
            style = MaterialTheme.typography.labelMedium,
            color = if (settings.configured) Ink.TextDim else Ink.Red,
        )
        Spacer(Modifier.height(16.dp))
        TabRow(selectedTabIndex = mode, containerColor = Ink.Surface) {
            Tab(selected = mode == 0, onClick = { mode = 0; secret = "" }, text = { Text("Password") })
            Tab(selected = mode == 1, onClick = { mode = 1; secret = "" }, text = { Text("Existing token") })
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = user, onValueChange = { user = it.trim() },
            label = { Text("Username") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = secret, onValueChange = { secret = it.trim() },
            label = { Text(if (mode == 0) "Password" else "Login token") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (mode == 0) "Your password is only used once to create a dedicated login token for this device; it's never stored."
            else "Paste a token created under Account → Login tokens in the web UI.",
            style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = Ink.Red, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val base = Graph.settings.value
                        val (u, token) = if (mode == 0) {
                            val (u, t) = Graph.api.login(base, user, secret)
                            u to t.token
                        } else {
                            val cand = base.copy(username = user, token = secret)
                            Graph.api.verifyToken(cand) to secret
                        }
                        Graph.updateSettings { it.copy(username = u.name, token = token) }
                        Graph.me.value = u
                        Graph.refreshServerState()
                        Graph.toast("Signed in as ${u.name}")
                    } catch (e: Exception) {
                        error = e.message ?: "Sign-in failed"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && settings.configured && user.isNotBlank() && secret.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else {
                Icon(Icons.Default.Login, null)
                Spacer(Modifier.width(8.dp))
                Text("Sign in")
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("You can browse anonymously if your server allows it.", style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = nav.settings) { Text("Server settings", maxLines = 1) }
            OutlinedButton(onClick = nav.similar) { Text("Image search", maxLines = 1) }
        }
    }
}

// ===================== Edit profile =====================

@Composable
private fun EditProfileDialog(u: User, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(u.emailText.orEmpty()) }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun avatarAction(label: String, block: suspend (User) -> User) {
        busy = true
        scope.launch {
            try {
                // Re-read the user so the version matches what the server expects.
                val fresh = Graph.api.user(u.name)
                Graph.me.value = block(fresh)
                Graph.toast(label)
            } catch (e: Exception) {
                error = e.message ?: "Avatar update failed"
            } finally {
                busy = false
            }
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) avatarAction("Avatar updated") { fresh ->
            val info = context.uriInfo(uri)
            Graph.api.uploadAvatar(fresh, UriRequestBody(context, uri, info.mime, info.size), info.name)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column {
                if (Graph.can("users:edit:self:avatar")) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), onClick = {
                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text("Upload avatar", maxLines = 1) }
                        if (u.avatarStyle == "manual") {
                            OutlinedButton(enabled = !busy, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), onClick = {
                                avatarAction("Using Gravatar") { fresh -> Graph.api.updateUser(fresh, avatarStyle = "gravatar") }
                            }) { Text("Use Gravatar", maxLines = 1) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Text("Used for Gravatar avatars and password resets.", style = MaterialTheme.typography.bodySmall, color = Ink.TextDim)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(pass, { pass = it }, label = { Text("New password (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(pass2, { pass2 = it }, label = { Text("Repeat new password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation())
                if (error != null) Text(error!!, color = Ink.Red, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (pass.isNotEmpty() && pass != pass2) { error = "Passwords don't match"; return@TextButton }
                    busy = true
                    scope.launch {
                        try {
                            val updated = Graph.api.updateUser(
                                u,
                                email = email.takeIf { it != u.emailText.orEmpty() },
                                password = pass.ifEmpty { null },
                            )
                            Graph.me.value = updated
                            Graph.toast("Profile updated")
                            onDismiss()
                        } catch (e: Exception) {
                            error = e.message ?: "Update failed"
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Ink.Surface2,
    )
}

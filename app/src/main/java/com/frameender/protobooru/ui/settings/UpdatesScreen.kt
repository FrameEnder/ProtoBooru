package com.frameender.protobooru.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.frameender.protobooru.BuildConfigInfo
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.UpdateCheck
import com.frameender.protobooru.data.UpdateScheduler
import com.frameender.protobooru.ui.common.BackButton
import com.frameender.protobooru.ui.common.SectionHeader
import com.frameender.protobooru.ui.common.screenInsets
import com.frameender.protobooru.ui.post.openUrl
import com.frameender.protobooru.ui.theme.Ink
import kotlinx.coroutines.launch

@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Graph.settings.collectAsState()
    val checking by Graph.updater.checking.collectAsState()
    val result by Graph.updater.lastResult.collectAsState()
    val progress by Graph.updater.downloadProgress.collectAsState()
    val downloaded by Graph.updater.downloaded.collectAsState()

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Graph.updateSettings { it.copy(updateNotify = granted) }
        if (!granted) Graph.toast("Notifications are off for ProtoBooru")
    }

    fun setAndReschedule(transform: (com.frameender.protobooru.data.AppSettings) -> com.frameender.protobooru.data.AppSettings) {
        Graph.updateSettings(transform)
        UpdateScheduler.apply(context, Graph.settings.value)
    }

    Scaffold(
        contentWindowInsets = screenInsets(),
        topBar = { TopAppBar(title = { Text("Updates") }, navigationIcon = { BackButton(onBack) }) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // ---------- Installed ----------
            Surface(shape = RoundedCornerShape(12.dp), color = Ink.Surface, border = BorderStroke(1.dp, Ink.Line), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Installed", style = MaterialTheme.typography.labelMedium, color = Ink.TextDim)
                    Text("ProtoBooru ${BuildConfigInfo.versionName(context)}", style = MaterialTheme.typography.titleLarge)
                    Text("build ${BuildConfigInfo.versionCode(context)} · ${s.updateChannel} channel", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { scope.launch { Graph.updater.check() } }, enabled = !checking) {
                        if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Check for updates")
                        }
                    }
                }
            }

            // ---------- Result ----------
            when (val r = result) {
                null -> {}
                UpdateCheck.UpToDate -> StatusLine(Icons.Default.CheckCircle, Ink.Green, "You're on the latest ${s.updateChannel} build.")
                is UpdateCheck.Failed -> StatusLine(Icons.Default.Error, Ink.Red, r.message)
                is UpdateCheck.Available -> {
                    val info = r.info
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = Ink.Surface2, border = BorderStroke(1.dp, Ink.Amber), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NewReleases, null, tint = Ink.Amber)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(info.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "build ${info.versionCode} · ${Format.bytes(info.asset.size)} · ${Format.ago(info.release.published_at)}",
                                        style = MaterialTheme.typography.labelSmall, color = Ink.TextDim,
                                    )
                                }
                            }
                            val notes = info.release.body?.trim().orEmpty()
                            if (notes.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Surface(color = Ink.Bg, shape = RoundedCornerShape(8.dp)) {
                                    SelectionContainer {
                                        Text(
                                            notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(10.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            val file = downloaded?.takeIf { it.name == info.asset.name && it.exists() }
                            when {
                                progress != null -> {
                                    Text("Downloading… ${((progress ?: 0f) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(progress = { progress ?: 0f }, modifier = Modifier.fillMaxWidth())
                                }
                                file != null -> Button(onClick = {
                                    if (Graph.updater.canInstall()) Graph.updater.install(context, file)
                                    else {
                                        Graph.toast("Allow ProtoBooru to install apps, then come back and tap Install")
                                        Graph.updater.openInstallPermission(context)
                                    }
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.SystemUpdate, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Install update")
                                }
                                else -> Button(onClick = {
                                    scope.launch {
                                        try {
                                            val f = Graph.updater.download(info)
                                            if (Graph.updater.canInstall()) Graph.updater.install(context, f)
                                            else {
                                                Graph.toast("Allow ProtoBooru to install apps, then tap Install")
                                                Graph.updater.openInstallPermission(context)
                                            }
                                        } catch (e: Exception) {
                                            Graph.toast(e.message ?: "Download failed")
                                        }
                                    }
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Download, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Download & install")
                                }
                            }
                            if (info.release.html_url.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                OutlinedButton(onClick = { openUrl(context, info.release.html_url) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("View on GitHub")
                                }
                            }
                        }
                    }
                }
            }

            // ---------- Settings ----------
            SectionHeader("Channel")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.updateChannel == "stable", { setAndReschedule { it.copy(updateChannel = "stable") }; scope.launch { Graph.updater.check() } }, { Text("Stable") })
                FilterChip(s.updateChannel == "nightly", { setAndReschedule { it.copy(updateChannel = "nightly") }; scope.launch { Graph.updater.check() } }, { Text("Nightly") })
            }
            Text(
                if (s.updateChannel == "nightly") "Every push to main. Newest features, least tested."
                else "Tagged releases (v1.2.0 and so on).",
                style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, modifier = Modifier.padding(top = 4.dp),
            )

            SectionHeader("Automatic checks")
            SwitchRow("Check for updates in the background", s.autoUpdateCheck) { on -> setAndReschedule { it.copy(autoUpdateCheck = on) } }
            SwitchRow("Notify me when an update is available", s.updateNotify, enabled = s.autoUpdateCheck) { on ->
                if (on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    Graph.lock.allowLeave(); notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Graph.updateSettings { it.copy(updateNotify = on) }
                }
            }
            Text(
                "Checks roughly every 6 hours, plus each time the app opens. With notifications on, " +
                    "new builds also pop up in the app.",
                style = MaterialTheme.typography.bodySmall, color = Ink.TextDim,
            )
            if (s.skippedUpdate > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Pop-up skipped for build ${s.skippedUpdate}",
                        style = MaterialTheme.typography.bodySmall, color = Ink.TextDim, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        Graph.updateSettings { it.copy(skippedUpdate = 0) }
                        Graph.updater.popupDismissed.value = 0
                    }) { Text("Show again") }
                }
            }

            SectionHeader("Source")
            var repo by remember(s.updateRepo) { mutableStateOf(s.updateRepo) }
            var token by remember(s.githubToken) { mutableStateOf(s.githubToken) }
            OutlinedTextField(repo, { repo = it.trim() }, label = { Text("GitHub repo (owner/name)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                token, { token = it.trim() }, label = { Text("GitHub token (only for a private repo)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("A fine-grained token with read-only “Contents” access to this repo.") },
            )
            OutlinedButton(
                enabled = repo != s.updateRepo || token != s.githubToken,
                onClick = {
                    Graph.updateSettings { it.copy(updateRepo = repo.ifBlank { "FrameEnder/ProtoBooru" }, githubToken = token) }
                    scope.launch { Graph.updater.check() }
                },
            ) { Text("Save & check") }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = if (enabled) Ink.Text else Ink.TextDim)
        Switch(value, onChange, enabled = enabled)
    }
}

package com.frameender.protobooru.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.UpdateInfo
import com.frameender.protobooru.ui.theme.Ink
import kotlinx.coroutines.launch

/**
 * "Update available" pop-up, shown over whatever screen you're on.
 *
 * It appears only when both "Check for updates in the background" and "Notify me when an
 * update is available" are on, and a newer build than the installed one has been found.
 *  - Update now: downloads and opens the installer right here.
 *  - Later: hides it until the app is next opened.
 *  - Skip this build: never pops up for this build again (a newer one will still show).
 * The Updates screen keeps working as before; the pop-up stays away while you're on it.
 *
 * [suppressed] is true on screens where it would get in the way (the Updates screen itself).
 */
@Composable
fun UpdatePopup(suppressed: Boolean, onDetails: () -> Unit) {
    val s by Graph.settings.collectAsState()
    val available by Graph.updater.available.collectAsState()
    val dismissed by Graph.updater.popupDismissed.collectAsState()
    val info = available ?: return

    val wanted = s.autoUpdateCheck && s.updateNotify &&
        info.versionCode != dismissed &&
        info.versionCode != s.skippedUpdate &&
        info.versionCode > Graph.updater.installedVersionCode()
    if (!wanted || suppressed) return

    UpdateDialog(
        info = info,
        onLater = { Graph.updater.popupDismissed.value = info.versionCode },
        onSkip = {
            Graph.updateSettings { it.copy(skippedUpdate = info.versionCode) }
            Graph.toast("Skipped build ${info.versionCode}. It's still on the Updates screen.")
        },
        onDetails = {
            Graph.updater.popupDismissed.value = info.versionCode
            onDetails()
        },
    )
}

@Composable
private fun UpdateDialog(info: UpdateInfo, onLater: () -> Unit, onSkip: () -> Unit, onDetails: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress by Graph.updater.downloadProgress.collectAsState()
    val downloaded by Graph.updater.downloaded.collectAsState()
    val file = downloaded?.takeIf { it.name == info.asset.name && it.exists() }
    val pct = progress
    val busy = pct != null

    fun installOrAskPermission(f: java.io.File) {
        if (Graph.updater.canInstall()) {
            Graph.updater.install(context, f)
        } else {
            Graph.toast("Allow ProtoBooru to install apps, then tap Install")
            Graph.updater.openInstallPermission(context)
        }
    }

    AlertDialog(
        // Tapping outside or pressing back counts as "Later", except mid-download.
        onDismissRequest = { if (!busy) onLater() },
        icon = { Icon(Icons.Default.NewReleases, null, tint = Ink.Amber) },
        title = { Text("Update available") },
        text = {
            Column {
                Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        "build ${info.versionCode}",
                        "you have ${Graph.updater.installedVersionCode()}",
                        info.asset.size.takeIf { it > 0 }?.let { Format.bytes(it) },
                        info.release.published_at?.let { Format.ago(it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = Ink.TextDim,
                )
                val notes = info.release.body?.trim().orEmpty()
                if (notes.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(color = Ink.Bg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            notes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                when {
                    busy -> {
                        Text("Downloading… ${((pct ?: 0f) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { pct ?: 0f }, color = Ink.Amber, modifier = Modifier.fillMaxWidth())
                    }
                    file != null -> Button(onClick = { installOrAskPermission(file) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.SystemUpdate, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Install update", maxLines = 1)
                    }
                    else -> Button(
                        onClick = {
                            scope.launch {
                                try {
                                    installOrAskPermission(Graph.updater.download(info))
                                } catch (e: Exception) {
                                    Graph.toast(e.message ?: "Download failed")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Update now", maxLines = 1)
                    }
                }
                if (!busy) {
                    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                        Text("Skip this build", color = Ink.TextDim, maxLines = 1)
                    }
                }
            }
        },
        // The dialog's own button area moves a button to the next line rather than squeezing it.
        confirmButton = { TextButton(onClick = onLater, enabled = !busy) { Text("Later", maxLines = 1) } },
        dismissButton = { TextButton(onClick = onDetails, enabled = !busy) { Text("Details", maxLines = 1) } },
        containerColor = Ink.Surface2,
    )
}

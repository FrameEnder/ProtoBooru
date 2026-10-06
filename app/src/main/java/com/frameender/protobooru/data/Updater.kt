package com.frameender.protobooru.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.frameender.protobooru.BuildConfigInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Serializable
data class GhAsset(
    val name: String = "",
    val size: Long = 0,
    val browser_download_url: String = "",
    val url: String = "",
)

@Serializable
data class GhRelease(
    val tag_name: String = "",
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val published_at: String? = null,
    val html_url: String = "",
    val assets: List<GhAsset> = emptyList(),
)

/** A release that's newer than what's installed, plus the APK to fetch. */
data class UpdateInfo(val release: GhRelease, val asset: GhAsset, val versionCode: Long) {
    val title: String get() = release.name?.ifBlank { null } ?: release.tag_name
}

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val info: UpdateInfo) : UpdateCheck
    data class Failed(val message: String) : UpdateCheck
}

/**
 * In-app updates from GitHub Releases.
 *
 * Every CI build sets the app's versionCode to the Actions run number and names its APK
 * `ProtoBooru-<run number>.apk`, so "newer" simply means a bigger number in the asset name.
 *  - stable:  the latest non-prerelease release (made by pushing a `v*` tag)
 *  - nightly: the rolling `nightly` pre-release, rebuilt on every push to main
 */
class Updater(private val context: Context, private val http: OkHttpClient) {
    val available = MutableStateFlow<UpdateInfo?>(null)
    val checking = MutableStateFlow(false)
    val lastResult = MutableStateFlow<UpdateCheck?>(null)

    /** Download progress 0..1 while downloading; null otherwise. */
    val downloadProgress = MutableStateFlow<Float?>(null)
    val downloaded = MutableStateFlow<File?>(null)

    private val apkName = Regex("""ProtoBooru-(\d+)\.apk""")
    private val dir: File get() = File(context.cacheDir, "updates").apply { mkdirs() }

    fun installedVersionCode(): Long = BuildConfigInfo.versionCode(context)

    private fun ghRequest(url: String, s: AppSettings, accept: String = "application/vnd.github+json"): Request {
        val b = Request.Builder().url(url)
            .header("Accept", accept)
            .header("User-Agent", "ProtoBooru-Android")
            .header("X-GitHub-Api-Version", "2022-11-28")
        if (s.githubToken.isNotBlank()) b.header("Authorization", "Bearer ${s.githubToken.trim()}")
        return b.build()
    }

    /** Asks GitHub for the newest release on the chosen channel. Never throws. */
    suspend fun check(s: AppSettings = Graph.settings.value): UpdateCheck {
        checking.value = true
        val result = try {
            val repo = s.updateRepo.trim().trim('/')
            val path = if (s.updateChannel == "nightly") "releases/tags/nightly" else "releases/latest"
            val body = withContext(Dispatchers.IO) {
                http.newCall(ghRequest("https://api.github.com/repos/$repo/$path", s)).execute().use { r ->
                    when {
                        r.code == 404 -> throw IllegalStateException(
                            if (s.githubToken.isBlank()) "No release found. If the repo is private, add a GitHub token."
                            else "No ${s.updateChannel} release found in $repo yet.",
                        )
                        r.code == 401 || r.code == 403 -> throw IllegalStateException("GitHub refused the request (HTTP ${r.code}). Check the token.")
                        !r.isSuccessful -> throw IllegalStateException("GitHub returned HTTP ${r.code}")
                    }
                    r.body?.string().orEmpty()
                }
            }
            val release = Graph.api.json.decodeFromString(GhRelease.serializer(), body)
            val best = release.assets
                .mapNotNull { a -> apkName.matchEntire(a.name)?.groupValues?.get(1)?.toLongOrNull()?.let { a to it } }
                .maxByOrNull { it.second }
                ?: throw IllegalStateException("Release ${release.tag_name} has no ProtoBooru-<build>.apk attached.")
            if (best.second > installedVersionCode()) {
                UpdateCheck.Available(UpdateInfo(release, best.first, best.second))
            } else {
                UpdateCheck.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheck.Failed(e.message ?: e.javaClass.simpleName)
        }
        lastResult.value = result
        available.value = (result as? UpdateCheck.Available)?.info
        checking.value = false
        return result
    }

    /** Downloads the APK into the cache (reusing an earlier finished download). */
    suspend fun download(info: UpdateInfo, s: AppSettings = Graph.settings.value): File = withContext(Dispatchers.IO) {
        val target = File(dir, info.asset.name)
        if (target.exists() && info.asset.size > 0 && target.length() == info.asset.size) {
            downloaded.value = target
            return@withContext target
        }
        dir.listFiles()?.forEach { it.delete() }
        // Private repos need the API asset URL with a token; public ones can use the plain link.
        val req = if (s.githubToken.isNotBlank() && info.asset.url.isNotBlank()) {
            ghRequest(info.asset.url, s, accept = "application/octet-stream")
        } else {
            Request.Builder().url(info.asset.browser_download_url).header("User-Agent", "ProtoBooru-Android").build()
        }
        val part = File(dir, info.asset.name + ".part")
        downloadProgress.value = 0f
        try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("Download failed (HTTP ${r.code})")
                val body = r.body ?: throw IllegalStateException("Empty download")
                val total = body.contentLength().takeIf { it > 0 } ?: info.asset.size
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) downloadProgress.value = (done.toFloat() / total).coerceIn(0f, 1f)
                        }
                    }
                }
            }
            if (!part.renameTo(target)) throw IllegalStateException("Could not save the update")
            downloaded.value = target
            target
        } finally {
            downloadProgress.value = null
            part.delete()
        }
    }

    /** Whether Android will let us open the installer (the "install unknown apps" permission). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system page where the user can allow ProtoBooru to install updates. */
    fun openInstallPermission(activityContext: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activityContext.startActivity(i)
    }

    /** Hands the APK to the system installer, which replaces the app in place. */
    fun install(activityContext: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activityContext.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            Graph.toast("No installer found on this device")
        }
    }
}

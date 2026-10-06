package com.frameender.protobooru

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.ui.AppRoot
import com.frameender.protobooru.ui.theme.ProtoBooruTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by Graph.settings.collectAsState()
            ProtoBooruTheme(amoled = settings.amoled) {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Two share targets point at this activity:
     *  - "Search ProtoBooru" (the activity itself): reverse image search.
     *  - "Upload to ProtoBooru" (UploadShareAlias): queue files or links for upload.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val viaUpload = intent.component?.className?.endsWith("UploadShareAlias") == true

        val uris: List<Uri> = if (action == Intent.ACTION_SEND_MULTIPLE) {
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            }
        } else {
            val single: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            listOfNotNull(single)
        }

        if (viaUpload) {
            if (uris.isNotEmpty()) {
                Graph.pendingUploadUris.value = Graph.pendingUploadUris.value + uris
            } else {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { Graph.pendingUploadUrl.value = it }
            }
        } else {
            uris.firstOrNull()?.let { Graph.pendingSharedImage.value = it }
        }
    }
}

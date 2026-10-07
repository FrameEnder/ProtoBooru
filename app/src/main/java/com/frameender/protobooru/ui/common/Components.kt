package com.frameender.protobooru.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.MicroTag
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.Mono
import com.frameender.protobooru.ui.theme.categoryColor
import com.frameender.protobooru.ui.theme.safetyColor
import kotlinx.coroutines.flow.distinctUntilChanged

/** True while the root bottom navigation bar is visible (it already pads the navigation bar). */
val LocalBottomBarShown = compositionLocalOf { false }

@Composable
fun screenInsets(): WindowInsets =
    if (LocalBottomBarShown.current) WindowInsets.statusBars else WindowInsets.systemBars

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
    }
}

@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.CloudOff, null, tint = Ink.Red, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Ink.TextDim, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

/** Standard loading / error / empty handling for a [PagedLoader]-backed screen. */
@Composable
fun <T> PagedStates(loader: PagedLoader<T>, emptyText: String, content: @Composable () -> Unit) {
    when {
        !loader.loadedOnce && loader.loading -> LoadingBox()
        !loader.loadedOnce && loader.error != null -> ErrorBox(loader.error!!, onRetry = loader::retry)
        loader.loadedOnce && loader.items.isEmpty() && !loader.loading -> EmptyBox(emptyText)
        else -> content()
    }
}

@Composable
fun <T> ListFooter(loader: PagedLoader<T>) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            loader.error != null -> OutlinedButton(onClick = loader::retry) { Text("Error: ${loader.error} — retry", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            loader.loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            loader.endReached && loader.items.isNotEmpty() ->
                Text("— ${loader.total} total —", style = MaterialTheme.typography.labelSmall, color = Ink.TextDim)
        }
    }
}

// ---------- Infinite scroll triggers ----------

@Composable
fun InfiniteScroll(state: LazyListState, buffer: Int = 8, onLoadMore: () -> Unit) {
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - buffer
        }.distinctUntilChanged().collect { if (it) onLoadMore() }
    }
}

@Composable
fun InfiniteScroll(state: LazyGridState, buffer: Int = 12, onLoadMore: () -> Unit) {
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - buffer
        }.distinctUntilChanged().collect { if (it) onLoadMore() }
    }
}

@Composable
fun InfiniteScroll(state: LazyStaggeredGridState, buffer: Int = 12, onLoadMore: () -> Unit) {
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - buffer
        }.distinctUntilChanged().collect { if (it) onLoadMore() }
    }
}

// ---------- Images ----------

@Composable
fun RemoteImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val settings by Graph.settings.collectAsState()
    AsyncImage(
        model = Graph.api.resolve(path, settings),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.background(Ink.Surface2),
    )
}

@Composable
fun Avatar(url: String?, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(Ink.Surface3),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Default.Person, null, tint = Ink.TextDim, modifier = Modifier.size(size * 0.6f))
        } else {
            RemoteImage(url, Modifier.fillMaxSize())
        }
    }
}

// ---------- Chips & labels ----------

@Composable
fun TagChip(
    tag: MicroTag,
    modifier: Modifier = Modifier,
    showCount: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val cats by Graph.tagCategories.collectAsState()
    val color = categoryColor(cats[tag.category]?.color)
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tag.name.replace('_', ' '), color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        if (showCount && tag.usages > 0) {
            Spacer(Modifier.width(6.dp))
            Text(com.frameender.protobooru.data.Format.count(tag.usages), color = color.copy(alpha = 0.6f), fontFamily = Mono, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun SafetyBadge(safety: String, modifier: Modifier = Modifier) {
    val c = safetyColor(safety)
    Text(
        safety.uppercase(),
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(c.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp),
        color = c,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(key, style = MaterialTheme.typography.labelMedium, color = Ink.TextDim, modifier = Modifier.width(110.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun StatTile(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Ink.Surface,
        border = BorderStroke(1.dp, Ink.Line),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.TextDim, maxLines = 1)
        }
    }
}

/** Small mono counter used on grid tiles and action rows. */
@Composable
fun CountLabel(icon: ImageVector, value: Int, tint: Color = Ink.TextDim, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(com.frameender.protobooru.data.Format.count(value), color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

// ---------- Inputs ----------

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder, color = Ink.TextDim) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange(""); onSearch() }) { Icon(Icons.Default.Close, "Clear") }
        },
        textStyle = if (mono) MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Ink.Surface,
            focusedContainerColor = Ink.Surface,
            unfocusedBorderColor = Ink.Line,
        ),
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Confirm",
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) Ink.Red else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Ink.Surface2,
    )
}

val ScreenPadding = PaddingValues(horizontal = 16.dp)

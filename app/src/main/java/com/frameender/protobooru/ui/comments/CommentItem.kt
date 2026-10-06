package com.frameender.protobooru.ui.comments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.frameender.protobooru.data.Comment
import com.frameender.protobooru.data.Format
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.ui.common.Avatar
import com.frameender.protobooru.ui.common.ConfirmDialog
import com.frameender.protobooru.ui.common.RemoteImage
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.theme.Mono

@Composable
fun CommentItem(
    c: Comment,
    onOpenUser: (String) -> Unit,
    onVote: (Comment, Int) -> Unit,
    onEdit: (Comment, String) -> Unit,
    onDelete: (Comment) -> Unit,
    modifier: Modifier = Modifier,
    postThumb: String? = null,
    onOpenPost: ((Int) -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mine = Graph.isMe(c.user?.name)
    val canEdit = (mine && Graph.can("comments:edit:own")) || Graph.can("comments:edit:any")
    val canDelete = (mine && Graph.can("comments:delete:own")) || Graph.can("comments:delete:any")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Ink.Surface2,
        border = BorderStroke(1.dp, Ink.Line),
    ) {
        Row(Modifier.padding(12.dp)) {
            if (postThumb != null && onOpenPost != null) {
                RemoteImage(
                    postThumb,
                    Modifier.size(56.dp).clickable { onOpenPost(c.postId) },
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.user?.avatarUrl, 28.dp, Modifier.clickable { c.user?.name?.let(onOpenUser) })
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.user?.name ?: "deleted user",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.clickable { c.user?.name?.let(onOpenUser) },
                        )
                        val edited = c.lastEditTime != null && c.lastEditTime != c.creationTime
                        Text(
                            Format.ago(c.creationTime) + (if (edited) " · edited" else "") +
                                (if (onOpenPost != null) " · post #${c.postId}" else ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink.TextDim,
                        )
                    }
                    if (canEdit || canDelete) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Comment actions", tint = Ink.TextDim) }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                if (canEdit) DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = true })
                                if (canDelete) DropdownMenuItem(text = { Text("Delete", color = Ink.Red) }, onClick = { menu = false; confirmDelete = true })
                            }
                        }
                    }
                }
                Spacer(Modifier.size(6.dp))
                SelectionContainer { Text(renderCommentText(c.text), style = MaterialTheme.typography.bodyMedium) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { onVote(c, 1) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (c.ownScore == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Upvote comment",
                            tint = if (c.ownScore == 1) Ink.Amber else Ink.TextDim, modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(c.score.toString(), style = MaterialTheme.typography.labelMedium, fontFamily = Mono)
                    IconButton(onClick = { onVote(c, -1) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (c.ownScore == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "Downvote comment",
                            tint = if (c.ownScore == -1) Ink.Red else Ink.TextDim, modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    if (editing) {
        var text by remember { mutableStateOf(c.text) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit comment") },
            text = {
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
            },
            confirmButton = {
                TextButton(onClick = { editing = false; onEdit(c, text) }, enabled = text.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
            containerColor = Ink.Surface2,
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete comment?",
            text = "This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = { onDelete(c) },
        )
    }
}

/**
 * Minimal rendering of Szurubooru's comment markdown: **bold**, *italic*, ~~strike~~ and
 * > quotes. Anything else is shown as typed.
 */
fun renderCommentText(src: String): AnnotatedString = buildAnnotatedString {
    src.lines().forEachIndexed { i, rawLine ->
        if (i > 0) append('\n')
        val quote = rawLine.startsWith(">")
        val line = if (quote) rawLine.removePrefix(">").trimStart() else rawLine
        if (quote) pushStyle(SpanStyle(color = Ink.TextDim, fontStyle = FontStyle.Italic))
        var idx = 0
        val re = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|~~(.+?)~~""")
        re.findAll(line).forEach { m ->
            append(line.substring(idx, m.range.first))
            when {
                m.groupValues[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
                m.groupValues[2].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[2]) }
                else -> withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) { append(m.groupValues[3]) }
            }
            idx = m.range.last + 1
        }
        append(line.substring(idx))
        if (quote) pop()
    }
}

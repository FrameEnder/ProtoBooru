package com.frameender.protobooru.ui.post

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frameender.protobooru.data.Comment
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.data.Post
import com.frameender.protobooru.data.PostSource
import com.frameender.protobooru.data.StaticPostSource
import kotlinx.coroutines.launch

class PostViewerViewModel(handle: SavedStateHandle) : ViewModel() {
    private val api = Graph.api

    // All navigation arguments are declared as strings (see AppNav).
    val startId: Int = handle.get<String>("id")?.toIntOrNull() ?: 0
    private val useSource: Boolean = handle.get<String>("src") == "1"

    /** The list being swiped through. Falls back to just this post after process death. */
    val source: PostSource = Graph.viewerSource
        ?.takeIf { useSource && startId in it.ids }
        ?: StaticPostSource(listOf(startId))

    val posts = mutableStateMapOf<Int, Post>()
    val errors = mutableStateMapOf<Int, String>()
    private val inFlight = mutableSetOf<Int>()

    init {
        // Reload a post when it's edited elsewhere (e.g. the edit screen on top of us).
        viewModelScope.launch {
            Graph.postChanged.collect { id -> if (posts.containsKey(id) || errors.containsKey(id)) ensureLoaded(id, force = true) }
        }
    }

    fun ensureLoaded(id: Int, force: Boolean = false) {
        if (!force && (posts.containsKey(id) || id in inFlight)) return
        inFlight += id
        errors.remove(id)
        viewModelScope.launch {
            try {
                posts[id] = api.post(id)
            } catch (e: Exception) {
                errors[id] = e.message ?: "Failed to load post"
            } finally {
                inFlight -= id
            }
        }
    }

    private fun requireLogin(): Boolean {
        if (!Graph.settings.value.loggedIn) {
            Graph.toast("Log in to do that")
            return false
        }
        return true
    }

    private fun mutate(id: Int, block: suspend () -> Post) {
        viewModelScope.launch {
            try {
                posts[id] = block()
            } catch (e: Exception) {
                Graph.toast(e.message ?: "Action failed")
            }
        }
    }

    fun toggleFavorite(p: Post) {
        if (!requireLogin()) return
        mutate(p.id) { if (p.ownFavorite) api.unfavorite(p.id) else api.favorite(p.id) }
    }

    fun vote(p: Post, dir: Int) {
        if (!requireLogin()) return
        val score = if (p.ownScore == dir) 0 else dir
        mutate(p.id) { api.ratePost(p.id, score) }
    }

    // ---- Comments (operate on the post's embedded comment list, then refresh it) ----

    fun addComment(postId: Int, text: String, onDone: () -> Unit) {
        if (!requireLogin() || text.isBlank()) return
        viewModelScope.launch {
            try {
                api.createComment(postId, text.trim())
                posts[postId] = api.post(postId)
                onDone()
            } catch (e: Exception) {
                Graph.toast(e.message ?: "Could not post comment")
            }
        }
    }

    fun editComment(c: Comment, text: String) {
        viewModelScope.launch {
            try {
                api.editComment(c, text.trim())
                posts[c.postId] = api.post(c.postId)
            } catch (e: Exception) {
                Graph.toast(e.message ?: "Could not edit comment")
            }
        }
    }

    fun deleteComment(c: Comment) {
        viewModelScope.launch {
            try {
                api.deleteComment(c)
                posts[c.postId] = api.post(c.postId)
                Graph.toast("Comment deleted")
            } catch (e: Exception) {
                Graph.toast(e.message ?: "Could not delete comment")
            }
        }
    }

    fun voteComment(c: Comment, dir: Int) {
        if (!requireLogin()) return
        val score = if (c.ownScore == dir) 0 else dir
        viewModelScope.launch {
            try {
                val updated = api.rateComment(c.id, score)
                posts[c.postId]?.let { p ->
                    posts[c.postId] = p.copy(comments = p.comments.map { if (it.id == updated.id) updated else it })
                }
            } catch (e: Exception) {
                Graph.toast(e.message ?: "Could not vote")
            }
        }
    }
}

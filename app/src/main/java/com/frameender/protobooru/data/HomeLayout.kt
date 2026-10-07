package com.frameender.protobooru.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * One block on the Home screen. A single flexible shape is shared by every widget type;
 * each type only uses the fields that make sense for it (see [WidgetType]).
 */
@Serializable
data class HomeWidget(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val enabled: Boolean = true,
    val title: String = "",
    /** Szurubooru search query. `{me}` is replaced with the signed-in username. */
    val query: String = "",
    val count: Int = 15,
    /** "s" / "m" / "l" for thumbnail rows; "compact" / "large" for the featured card. */
    val size: String = "m",
    val columns: Int = 3,
    /** Stats tiles, shortcuts, or saved searches ("Label|query"), depending on type. */
    val items: List<String> = emptyList(),
    /** Tag category filter for the tags widget ("" = any). */
    val category: String = "",
    /** Sort for the tags widget: "usages", "creation-time", or "last-edit-time". */
    val sort: String = "",
)

/** Everything the customizer needs to know about a widget type. */
enum class WidgetType(val key: String, val label: String, val description: String) {
    SEARCH("search", "Search bar", "A search box for posts."),
    STATS("stats", "Stats tiles", "Counts for posts, tags, pools and more. Each tile opens its section."),
    FEATURED("featured", "Featured post", "The post currently featured on your booru."),
    STRIP("strip", "Post row", "A sideways-scrolling row of posts from any search."),
    GRID("grid", "Post grid", "A small grid of posts from any search."),
    RANDOM("random", "Random post", "One big random post from a search, with a re-roll button."),
    TAGS("tags", "Top tags", "Tag chips, filtered by category and sorted how you like."),
    POOLS("pools", "Pools", "A row of pool covers."),
    COMMENTS("comments", "Recent comments", "The latest comments with their posts."),
    SHORTCUTS("shortcuts", "Shortcuts", "Buttons for the places you go most."),
    SAVED("saved", "Saved searches", "One-tap chips for searches you run often."),
    HEADER("header", "Section title", "A heading to group the widgets below it.");

    companion object {
        fun of(key: String): WidgetType? = entries.firstOrNull { it.key == key }
    }
}

/** Options shown in the editor for multi-choice fields. */
object WidgetOptions {
    val stats = listOf(
        "posts" to "Posts", "tags" to "Tags", "pools" to "Pools", "comments" to "Comments",
        "users" to "Users", "disk" to "Disk", "random" to "Random", "favorites" to "My favorites",
    )
    val shortcuts = listOf(
        "upload" to "Upload", "users" to "Users", "imageSearch" to "Image search", "history" to "Site history",
        "favorites" to "My favorites", "myUploads" to "My uploads", "tags" to "Tags", "pools" to "Pools",
        "comments" to "Comments", "random" to "Random post", "settings" to "Settings", "updates" to "Updates",
    )
    val tagSorts = listOf("usages" to "Most used", "creation-time" to "Newest", "last-edit-time" to "Recently edited")
}

object HomeLayouts {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(HomeWidget.serializer())

    /** The original Home screen, rebuilt from widgets. */
    fun default(): List<HomeWidget> = listOf(
        HomeWidget(id = "d-search", type = "search"),
        HomeWidget(id = "d-stats", type = "stats", columns = 3, items = listOf("posts", "tags", "pools", "comments", "disk", "random")),
        HomeWidget(id = "d-featured", type = "featured", title = "Featured post", size = "large"),
        HomeWidget(id = "d-latest", type = "strip", title = "Latest uploads", query = "", count = 15),
        HomeWidget(id = "d-faves", type = "strip", title = "Most favorited", query = "sort:fav-count", count = 15),
        HomeWidget(id = "d-explore", type = "shortcuts", title = "Explore", items = listOf("upload", "users", "imageSearch", "history", "favorites")),
    )

    /** A new widget of [type] with sensible starting values. */
    fun create(type: WidgetType): HomeWidget = when (type) {
        WidgetType.SEARCH -> HomeWidget(type = type.key)
        WidgetType.STATS -> HomeWidget(type = type.key, columns = 3, items = listOf("posts", "tags", "pools"))
        WidgetType.FEATURED -> HomeWidget(type = type.key, title = "Featured post", size = "large")
        WidgetType.STRIP -> HomeWidget(type = type.key, title = "New posts", count = 15)
        WidgetType.GRID -> HomeWidget(type = type.key, title = "Top rated", query = "sort:score", count = 9, columns = 3)
        WidgetType.RANDOM -> HomeWidget(type = type.key, title = "Random pick", size = "large")
        WidgetType.TAGS -> HomeWidget(type = type.key, title = "Popular tags", count = 20, sort = "usages")
        WidgetType.POOLS -> HomeWidget(type = type.key, title = "Pools", count = 8, query = "sort:last-edit-time")
        WidgetType.COMMENTS -> HomeWidget(type = type.key, title = "Recent comments", count = 5)
        WidgetType.SHORTCUTS -> HomeWidget(type = type.key, title = "Shortcuts", items = listOf("upload", "favorites", "imageSearch"))
        WidgetType.SAVED -> HomeWidget(type = type.key, title = "Saved searches", items = listOf("Videos|type:video", "My favorites|fav:{me}"))
        WidgetType.HEADER -> HomeWidget(type = type.key, title = "Section")
    }

    fun decode(raw: String): List<HomeWidget> =
        if (raw.isBlank()) default()
        else runCatching { json.decodeFromString(serializer, raw) }.getOrNull()?.filter { WidgetType.of(it.type) != null } ?: default()

    /** Parses a pasted layout; throws if it isn't one. Fresh IDs avoid clashes with the current layout. */
    fun decodeStrict(raw: String): List<HomeWidget> {
        val list = json.decodeFromString(serializer, raw.trim())
        require(list.isNotEmpty() && list.all { WidgetType.of(it.type) != null })
        return list.map { it.copy(id = UUID.randomUUID().toString()) }
    }

    fun encode(list: List<HomeWidget>): String = json.encodeToString(serializer, list)

    /** Expands `{me}` and appends the global safety filter to a post query. */
    fun postQuery(raw: String, s: AppSettings): String {
        val q = raw.replace("{me}", s.username.ifBlank { "anonymous" }).trim()
        return (listOfNotNull(q.ifBlank { null }) + s.filterTerms(q)).joinToString(" ")
    }
}

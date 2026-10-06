package com.frameender.protobooru.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

// ---------- Generic envelopes ----------

@Serializable
data class Paged<T>(
    val query: String? = null,
    val offset: Int = 0,
    val limit: Int = 0,
    val total: Int = 0,
    val results: List<T> = emptyList(),
)

@Serializable
data class Unpaged<T>(val results: List<T> = emptyList())

@Serializable
data class ApiError(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
)

// ---------- Micro resources ----------

@Serializable
data class MicroUser(val name: String = "", val avatarUrl: String? = null)

@Serializable
data class MicroTag(
    val names: List<String> = emptyList(),
    val category: String = "default",
    val usages: Int = 0,
) {
    val name: String get() = names.firstOrNull().orEmpty()
}

@Serializable
data class MicroPost(val id: Int = 0, val thumbnailUrl: String? = null)

@Serializable
data class MicroPool(
    val id: Int = 0,
    val names: List<String> = emptyList(),
    val category: String = "default",
    val description: String? = null,
    val postCount: Int = 0,
) {
    val name: String get() = names.firstOrNull().orEmpty()
}

// ---------- Posts ----------

@Serializable
data class Note(val polygon: List<List<Double>> = emptyList(), val text: String = "")

@Serializable
data class Post(
    val id: Int = 0,
    val version: Int = 0,
    val creationTime: String? = null,
    val lastEditTime: String? = null,
    val safety: String = "safe",
    val source: String? = null,
    val type: String = "image",
    val mimeType: String? = null,
    val checksum: String? = null,
    val checksumMD5: String? = null,
    val fileSize: Long? = null,
    val canvasWidth: Int? = null,
    val canvasHeight: Int? = null,
    val contentUrl: String? = null,
    val thumbnailUrl: String? = null,
    val flags: List<String> = emptyList(),
    val tags: List<MicroTag> = emptyList(),
    val relations: List<MicroPost> = emptyList(),
    val notes: List<Note> = emptyList(),
    val user: MicroUser? = null,
    val score: Int = 0,
    val ownScore: Int = 0,
    val ownFavorite: Boolean = false,
    val tagCount: Int = 0,
    val favoriteCount: Int = 0,
    val commentCount: Int = 0,
    val noteCount: Int = 0,
    val featureCount: Int = 0,
    val relationCount: Int = 0,
    val lastFeatureTime: String? = null,
    val favoritedBy: List<MicroUser> = emptyList(),
    val hasCustomThumbnail: Boolean = false,
    val comments: List<Comment> = emptyList(),
    val pools: List<MicroPool> = emptyList(),
) {
    val isVideo: Boolean get() = type == "video"
    val isAnimation: Boolean get() = type == "animation"
    val isFlash: Boolean get() = type == "flash"
    val aspect: Float
        get() {
            val w = canvasWidth ?: 0
            val h = canvasHeight ?: 0
            return if (w > 0 && h > 0) w.toFloat() / h else 1f
        }
    val extension: String
        get() {
            val fromUrl = contentUrl?.substringAfterLast('.', "")?.substringBefore('?').orEmpty()
            if (fromUrl.isNotBlank() && fromUrl.length <= 5) return fromUrl.lowercase()
            return when (mimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                "video/mp4" -> "mp4"
                "video/webm" -> "webm"
                "application/x-shockwave-flash" -> "swf"
                else -> "bin"
            }
        }
}

/** Fields requested for grid/list views to keep payloads small. */
const val GRID_FIELDS =
    "id,thumbnailUrl,type,safety,score,favoriteCount,commentCount,tagCount,canvasWidth,canvasHeight,ownFavorite,ownScore,mimeType,contentUrl"

@Serializable
data class UploadToken(val token: String = "")

val SAFETIES = listOf("safe", "sketchy", "unsafe")

@Serializable
data class Around(val prev: MicroPost? = null, val next: MicroPost? = null)

@Serializable
data class SimilarPost(val distance: Double = 0.0, val post: Post = Post())

@Serializable
data class ReverseSearchResult(
    val exactPost: Post? = null,
    val similarPosts: List<SimilarPost> = emptyList(),
)

// ---------- Comments ----------

@Serializable
data class Comment(
    val id: Int = 0,
    val version: Int = 0,
    val postId: Int = 0,
    val user: MicroUser? = null,
    val text: String = "",
    val creationTime: String? = null,
    val lastEditTime: String? = null,
    val score: Int = 0,
    val ownScore: Int = 0,
)

// ---------- Tags ----------

@Serializable
data class Tag(
    val version: Int = 0,
    val names: List<String> = emptyList(),
    val category: String = "default",
    val implications: List<MicroTag> = emptyList(),
    val suggestions: List<MicroTag> = emptyList(),
    val creationTime: String? = null,
    val lastEditTime: String? = null,
    val usages: Int = 0,
    val description: String? = null,
) {
    val name: String get() = names.firstOrNull().orEmpty()
}

@Serializable
data class TagCategory(
    val name: String = "",
    val version: Int = 0,
    val color: String = "default",
    val usages: Int = 0,
    val order: Int = 0,
    val default: Boolean = false,
)

@Serializable
data class TagSibling(val tag: MicroTag = MicroTag(), val occurrences: Int = 0)

// ---------- Pools ----------

@Serializable
data class Pool(
    val id: Int = 0,
    val version: Int = 0,
    val names: List<String> = emptyList(),
    val category: String = "default",
    val posts: List<MicroPost> = emptyList(),
    val creationTime: String? = null,
    val lastEditTime: String? = null,
    val postCount: Int = 0,
    val description: String? = null,
) {
    val name: String get() = names.firstOrNull().orEmpty()
}

@Serializable
data class PoolCategory(
    val name: String = "",
    val version: Int = 0,
    val color: String = "default",
    val usages: Int = 0,
    val default: Boolean = false,
)

// ---------- Users ----------

@Serializable
data class User(
    val version: Int = 0,
    val name: String = "",
    // Szurubooru returns `false` instead of a value when you lack permission to see these,
    // so they are kept as raw JSON and read through the helpers below.
    val email: JsonElement? = null,
    val rank: String = "anonymous",
    val lastLoginTime: String? = null,
    val creationTime: String? = null,
    val avatarStyle: String = "gravatar",
    val avatarUrl: String? = null,
    val commentCount: Int = 0,
    val uploadedPostCount: Int = 0,
    val likedPostCount: JsonElement? = null,
    val dislikedPostCount: JsonElement? = null,
    val favoritePostCount: Int = 0,
) {
    val emailText: String?
        get() = (email as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    val likedCount: Int? get() = (likedPostCount as? JsonPrimitive)?.intOrNull
    val dislikedCount: Int? get() = (dislikedPostCount as? JsonPrimitive)?.intOrNull
}

@Serializable
data class UserToken(
    val user: MicroUser? = null,
    val token: String = "",
    val note: String? = null,
    val enabled: Boolean = true,
    val expirationTime: String? = null,
    val version: Int = 0,
    val creationTime: String? = null,
    val lastEditTime: String? = null,
    val lastUsageTime: String? = null,
)

// ---------- Snapshots ----------

@Serializable
data class Snapshot(
    val operation: String = "",
    val type: String = "",
    val id: JsonElement? = null,
    val user: MicroUser? = null,
    val data: JsonElement? = null,
    val time: String? = null,
) {
    val idText: String get() = (id as? JsonPrimitive)?.contentOrNull ?: id?.toString().orEmpty()
}

// ---------- Info ----------

@Serializable
data class InfoConfig(
    val name: String? = null,
    val userNameRegex: String? = null,
    val passwordRegex: String? = null,
    val tagNameRegex: String? = null,
    val defaultUserRank: String? = null,
    val enableSafety: Boolean = true,
    val contactEmail: String? = null,
    val canSendMails: Boolean = false,
    val privileges: JsonElement? = null,
) {
    /** Privilege name -> minimum rank. Parsed defensively; unknown shapes yield an empty map. */
    val privilegeMap: Map<String, String>
        get() = (privileges as? JsonObject)?.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
        }?.toMap().orEmpty()
}

@Serializable
data class Info(
    val postCount: Int = 0,
    val diskUsage: Long = 0,
    val featuredPost: Post? = null,
    val featuringTime: String? = null,
    val featuringUser: MicroUser? = null,
    val serverTime: String? = null,
    val config: InfoConfig = InfoConfig(),
)

// ---------- Ranks & privileges ----------

object Ranks {
    val order = listOf("anonymous", "restricted", "regular", "power", "moderator", "administrator", "nobody")
    fun level(rank: String?): Int = order.indexOf(rank ?: "anonymous").let { if (it < 0) 0 else it }
}

@Suppress("unused")
private fun JsonElement?.asBool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

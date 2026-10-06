package com.frameender.protobooru.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.ui.account.AccountNav
import com.frameender.protobooru.ui.account.AccountScreen
import com.frameender.protobooru.ui.account.TokensScreen
import com.frameender.protobooru.ui.comments.CommentsScreen
import com.frameender.protobooru.ui.common.LocalBottomBarShown
import com.frameender.protobooru.ui.history.HistoryNav
import com.frameender.protobooru.ui.history.HistoryScreen
import com.frameender.protobooru.ui.home.HomeNav
import com.frameender.protobooru.ui.home.HomeScreen
import com.frameender.protobooru.ui.pools.PoolDetailScreen
import com.frameender.protobooru.ui.pools.PoolsScreen
import com.frameender.protobooru.ui.post.PostViewerScreen
import com.frameender.protobooru.ui.post.ViewerNav
import com.frameender.protobooru.ui.posts.PostsScreen
import com.frameender.protobooru.ui.search.ImageSearchScreen
import com.frameender.protobooru.ui.settings.SettingsScreen
import com.frameender.protobooru.ui.tags.TagDetailScreen
import com.frameender.protobooru.ui.tags.TagsScreen
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.users.UserDetailScreen
import com.frameender.protobooru.ui.users.UsersScreen

object Routes {
    const val HOME = "home"
    const val POSTS = "posts?q={q}"
    const val POST = "post/{id}?src={src}"
    const val TAGS = "tags"
    const val TAG = "tag/{name}"
    const val POOLS = "pools"
    const val POOL = "pool/{id}"
    const val COMMENTS = "comments?q={q}"
    const val USERS = "users"
    const val USER = "user/{name}"
    const val ACCOUNT = "account"
    const val TOKENS = "tokens"
    const val HISTORY = "history?q={q}"
    const val SIMILAR = "similar?post={post}"
    const val SETTINGS = "settings"

    private fun e(s: String) = Uri.encode(s)
    fun posts(q: String = "") = "posts?q=${e(q)}"
    fun post(id: Int, fromList: Boolean) = "post/$id?src=${if (fromList) "1" else "0"}"
    fun tag(name: String) = "tag/${e(name)}"
    fun pool(id: Int) = "pool/$id"
    fun comments(q: String = "") = "comments?q=${e(q)}"
    fun user(name: String) = "user/${e(name)}"
    fun history(q: String = "") = "history?q=${e(q)}"
    fun similar(postId: Int? = null) = "similar?post=${postId ?: ""}"
}

private data class Tab(val route: String, val navRoute: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, Routes.HOME, "Home", Icons.Default.Home),
    Tab(Routes.POSTS, "posts", "Posts", Icons.Default.PhotoLibrary),
    Tab(Routes.TAGS, Routes.TAGS, "Tags", Icons.Default.LocalOffer),
    Tab(Routes.POOLS, Routes.POOLS, "Pools", Icons.Default.Collections),
    Tab(Routes.ACCOUNT, Routes.ACCOUNT, "Account", Icons.Default.AccountCircle),
)

private fun strArg(name: String, default: String? = "") = navArgument(name) {
    type = NavType.StringType
    if (default != null) {
        defaultValue = default
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val progress by Graph.downloads.progress.collectAsState()
    val sharedImage by Graph.pendingSharedImage.collectAsState()

    // A posts search pushed from elsewhere (non-empty q) hides the bar; the Posts tab itself shows it.
    val isTabRoot = route in TABS.map { it.route } &&
        !(route == Routes.POSTS && !entry?.arguments?.getString("q").isNullOrEmpty())
    val showBar = isTabRoot

    LaunchedEffect(Unit) { Graph.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(sharedImage) {
        if (sharedImage != null && nav.currentDestination?.route != Routes.SIMILAR) nav.navigate(Routes.similar())
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = Ink.Surface) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                if (route == tab.route) return@NavigationBarItem
                                nav.navigate(tab.navRoute) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Ink.AmberDim),
                        )
                    }
                }
            }
        },
    ) { pad ->
        CompositionLocalProvider(LocalBottomBarShown provides showBar) {
            Box(Modifier.padding(pad).fillMaxSize()) {
                AppNavHost(nav)
                progress?.let { p ->
                    LinearProgressIndicator(
                        progress = { if (p.total == 0) 0f else (p.done + p.failed + p.skipped).toFloat() / p.total },
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    val openPost: (Int) -> Unit = { id -> nav.navigate(Routes.post(id, fromList = true)) }
    val openSinglePost: (Int) -> Unit = { id -> nav.navigate(Routes.post(id, fromList = false)) }
    val searchPosts: (String) -> Unit = { q -> nav.navigate(Routes.posts(q)) }
    val openTag: (String) -> Unit = { n -> nav.navigate(Routes.tag(n)) }
    val openPool: (Int) -> Unit = { id -> nav.navigate(Routes.pool(id)) }
    val openUser: (String) -> Unit = { n -> nav.navigate(Routes.user(n)) }
    val openComments: (String) -> Unit = { q -> nav.navigate(Routes.comments(q)) }
    val openHistory: (String) -> Unit = { q -> nav.navigate(Routes.history(q)) }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                HomeNav(
                    search = { q -> if (q.isBlank()) nav.navigate("posts") { launchSingleTop = true } else searchPosts(q) },
                    openPost = openPost,
                    tags = { nav.navigate(Routes.TAGS) { launchSingleTop = true } },
                    pools = { nav.navigate(Routes.POOLS) { launchSingleTop = true } },
                    comments = { openComments("") },
                    users = { nav.navigate(Routes.USERS) },
                    history = { openHistory("") },
                    imageSearch = { nav.navigate(Routes.similar()) },
                    settings = { nav.navigate(Routes.SETTINGS) },
                ),
            )
        }
        composable(Routes.POSTS, arguments = listOf(strArg("q"))) { e ->
            val q = e.arguments?.getString("q").orEmpty()
            PostsScreen(canGoBack = q.isNotEmpty(), onBack = back, onOpenPost = openPost)
        }
        composable(Routes.POST, arguments = listOf(strArg("id", null), strArg("src", "0"))) {
            PostViewerScreen(
                ViewerNav(
                    back = back,
                    searchTag = searchPosts,
                    openTag = openTag,
                    openPool = openPool,
                    openUser = openUser,
                    openPost = openSinglePost,
                    similar = { id -> nav.navigate(Routes.similar(id)) },
                ),
            )
        }
        composable(Routes.TAGS) { TagsScreen(onOpenTag = openTag) }
        composable(Routes.TAG, arguments = listOf(strArg("name", null))) {
            TagDetailScreen(onBack = back, onSearch = searchPosts, onOpenTag = openTag)
        }
        composable(Routes.POOLS) { PoolsScreen(onOpenPool = openPool) }
        composable(Routes.POOL, arguments = listOf(strArg("id", null))) {
            PoolDetailScreen(onBack = back, onOpenPost = openPost, onSearch = searchPosts)
        }
        composable(Routes.COMMENTS, arguments = listOf(strArg("q"))) {
            CommentsScreen(onBack = back, onOpenPost = openSinglePost, onOpenUser = openUser)
        }
        composable(Routes.USERS) { UsersScreen(onBack = back, onOpenUser = openUser) }
        composable(Routes.USER, arguments = listOf(strArg("name", null))) {
            UserDetailScreen(onBack = back, onSearchPosts = searchPosts, onComments = openComments, onHistory = openHistory)
        }
        composable(Routes.ACCOUNT) {
            AccountScreen(
                AccountNav(
                    searchPosts = searchPosts,
                    comments = openComments,
                    history = openHistory,
                    users = { nav.navigate(Routes.USERS) },
                    tokens = { nav.navigate(Routes.TOKENS) },
                    similar = { nav.navigate(Routes.similar()) },
                    settings = { nav.navigate(Routes.SETTINGS) },
                ),
            )
        }
        composable(Routes.TOKENS) { TokensScreen(onBack = back) }
        composable(Routes.HISTORY, arguments = listOf(strArg("q"))) {
            HistoryScreen(HistoryNav(back = back, openPost = openSinglePost, openTag = openTag, openPool = openPool, openUser = openUser))
        }
        composable(Routes.SIMILAR, arguments = listOf(strArg("post"))) {
            ImageSearchScreen(onBack = back, onOpenPost = openPost)
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
    }
}

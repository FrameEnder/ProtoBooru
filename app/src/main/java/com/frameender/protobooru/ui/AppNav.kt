package com.frameender.protobooru.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.frameender.protobooru.data.Graph
import com.frameender.protobooru.security.LockGate
import com.frameender.protobooru.ui.account.AccountNav
import com.frameender.protobooru.ui.account.AccountScreen
import com.frameender.protobooru.ui.account.TokensScreen
import com.frameender.protobooru.ui.categories.CategoriesScreen
import com.frameender.protobooru.ui.comments.CommentsScreen
import com.frameender.protobooru.ui.common.LocalBottomBarShown
import com.frameender.protobooru.ui.history.HistoryNav
import com.frameender.protobooru.ui.history.HistoryScreen
import com.frameender.protobooru.ui.home.HomeLayoutScreen
import com.frameender.protobooru.ui.home.HomeNav
import com.frameender.protobooru.ui.home.HomeScreen
import com.frameender.protobooru.ui.pools.PoolDetailScreen
import com.frameender.protobooru.ui.pools.PoolEditScreen
import com.frameender.protobooru.ui.pools.PoolsScreen
import com.frameender.protobooru.ui.post.PostEditScreen
import com.frameender.protobooru.ui.post.PostViewerScreen
import com.frameender.protobooru.ui.post.ViewerNav
import com.frameender.protobooru.ui.posts.PostsScreen
import com.frameender.protobooru.ui.search.ImageSearchScreen
import com.frameender.protobooru.ui.settings.SettingsScreen
import com.frameender.protobooru.ui.settings.SettingsSectionScreen
import com.frameender.protobooru.ui.settings.UpdatePopup
import com.frameender.protobooru.ui.settings.UpdatesScreen
import com.frameender.protobooru.ui.tags.TagDetailScreen
import com.frameender.protobooru.ui.tags.TagEditScreen
import com.frameender.protobooru.ui.tags.TagsScreen
import com.frameender.protobooru.ui.theme.Ink
import com.frameender.protobooru.ui.upload.UploadScreen
import com.frameender.protobooru.ui.users.UserDetailScreen
import com.frameender.protobooru.ui.users.UsersScreen
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val POSTS = "posts?q={q}"          // the Posts tab
    const val SEARCH = "search?q={q}"        // search results opened from anywhere else
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
    const val UPLOAD = "upload"
    const val UPDATES = "updates"
    const val POST_EDIT = "post/{id}/edit"
    const val TAG_EDIT = "tag-edit?name={name}"
    const val POOL_EDIT = "pool-edit?id={id}"
    const val CATEGORIES = "categories/{kind}"
    const val HOME_LAYOUT = "home-layout"
    const val SETTINGS_PAGE = "settings/{page}"
    fun settingsPage(key: String) = "settings/$key"

    private fun e(s: String) = Uri.encode(s)
    fun posts(q: String = "") = "posts?q=${e(q)}"
    fun search(q: String) = "search?q=${e(q)}"
    fun post(id: Int, fromList: Boolean) = "post/$id?src=${if (fromList) "1" else "0"}"
    fun tag(name: String) = "tag/${e(name)}"
    fun pool(id: Int) = "pool/$id"
    fun comments(q: String = "") = "comments?q=${e(q)}"
    fun user(name: String) = "user/${e(name)}"
    fun history(q: String = "") = "history?q=${e(q)}"
    fun similar(postId: Int? = null) = "similar?post=${postId ?: ""}"
    fun postEdit(id: Int) = "post/$id/edit"
    fun tagEdit(name: String? = null) = "tag-edit?name=${e(name.orEmpty())}"
    fun poolEdit(id: Int? = null) = "pool-edit?id=${id ?: ""}"
    fun categories(kind: String) = "categories/$kind"
}

private data class Tab(val route: String, val navRoute: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, Routes.HOME, "Home", Icons.Default.Home),
    Tab(Routes.POSTS, "posts", "Posts", Icons.Default.PhotoLibrary),
    Tab(Routes.TAGS, Routes.TAGS, "Tags", Icons.Default.LocalOffer),
    Tab(Routes.POOLS, Routes.POOLS, "Pools", Icons.Default.Collections),
    Tab(Routes.ACCOUNT, Routes.ACCOUNT, "Account", Icons.Default.AccountCircle),
)

/**
 * A NavHost destination that ignores touches while it's animating away.
 *
 * After a back gesture (or a tab switch) the old screen keeps drawing on top for a moment
 * while it fades out. Without this, a tap aimed at the screen you're returning to could land
 * on whatever used to be in that spot, e.g. opening a favorite instead of the Random tile.
 */
@OptIn(ExperimentalAnimationApi::class)
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    val scope = this
    val leaving = transition.targetState == EnterExitState.PostExit
    Box(Modifier.fillMaxSize()) {
        scope.content(entry)
        if (leaving) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    },
            )
        }
    }
}

/** Screens that hide the bottom bar: the full-screen post viewer and the editors. */
private val NO_BAR_ROUTES = setOf(
    Routes.POST, Routes.POST_EDIT, Routes.TAG_EDIT, Routes.POOL_EDIT, Routes.UPLOAD,
)

/**
 * Which tab the current screen belongs to. Each tab other than Home sits directly on top of
 * Home in the back stack (switching tabs pops the previous one), so at most one is present.
 */
private fun NavController.currentTabRoute(): String =
    TABS.drop(1).firstOrNull { tab -> runCatching { getBackStackEntry(tab.route) }.isSuccess }?.route
        ?: Routes.HOME

/**
 * Switch to a bottom-bar tab, keeping each tab's own history.
 *
 * Home is handled separately: it's the start destination, so we pop straight back to it.
 * Navigating to it with `restoreState` can restore the stack that was just popped off it
 * (e.g. the Posts list opened from a Home "see all" link) and leave you stuck there.
 */
fun NavController.selectTab(navRoute: String) {
    if (navRoute == Routes.HOME) {
        if (!popBackStack(Routes.HOME, inclusive = false, saveState = true)) {
            navigate(Routes.HOME) { launchSingleTop = true }
        }
        return
    }
    navigate(navRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

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
    val bulkProgress by Graph.bulk.progress.collectAsState()
    val offlineSave by Graph.offlineSaver.progress.collectAsState()
    val offline by Graph.offline.collectAsState()
    val sharedImage by Graph.pendingSharedImage.collectAsState()
    val sharedUploads by Graph.pendingUploadUris.collectAsState()
    val sharedUploadUrl by Graph.pendingUploadUrl.collectAsState()
    val pendingRoute by Graph.pendingRoute.collectAsState()

    // The bar stays up everywhere except full-screen and editing screens (see NO_BAR_ROUTES).
    val showBar = route != null && route !in NO_BAR_ROUTES
    // The highlighted tab is the one this screen was reached from, e.g. Home for a Home "see all".
    val currentTab = remember(entry) { nav.currentTabRoute() }

    LaunchedEffect(Unit) { Graph.messages.collect { snackbar.showSnackbar(it) } }

    // Coming back to the app re-checks for updates if the last check is over 30 minutes old.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START && Graph.settings.value.autoUpdateCheck) {
                scope.launch { Graph.updater.checkIfStale(30 * 60 * 1000L) }
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(sharedImage) {
        if (sharedImage != null && nav.currentDestination?.route != Routes.SIMILAR) nav.navigate(Routes.similar())
    }
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { r ->
            Graph.pendingRoute.value = null
            if (r == "updates" && nav.currentDestination?.route != Routes.UPDATES) nav.navigate(Routes.UPDATES)
        }
    }
    LaunchedEffect(sharedUploads, sharedUploadUrl) {
        if ((sharedUploads.isNotEmpty() || sharedUploadUrl != null) && nav.currentDestination?.route != Routes.UPLOAD) {
            nav.navigate(Routes.UPLOAD)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = Ink.Surface) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab.route,
                            onClick = {
                                when {
                                    route == tab.route -> {}
                                    // Tapping the tab you're already in goes back to its first screen.
                                    currentTab == tab.route -> nav.popBackStack(tab.route, inclusive = false)
                                    else -> nav.selectTab(tab.navRoute)
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
                bulkProgress?.let { p ->
                    Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 4.dp)) {
                        Text(
                            "${p.label} ${p.done}/${p.total}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink.Amber,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        LinearProgressIndicator(
                            progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total },
                            modifier = Modifier.fillMaxWidth(),
                            color = Ink.Amber,
                        )
                    }
                }
                if (bulkProgress == null) offlineSave?.let { p ->
                    Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 4.dp)) {
                        Text(
                            "Saving “${p.label}” for offline ${p.done}/${p.total}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink.Teal,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        if (p.total == 0) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Ink.Teal)
                        } else {
                            LinearProgressIndicator(
                                progress = { p.done.toFloat() / p.total },
                                modifier = Modifier.fillMaxWidth(),
                                color = Ink.Teal,
                            )
                        }
                    }
                }
                // Showing saved copies because the server can't be reached.
                AnimatedVisibility(
                    visible = (offline || Graph.settings.value.forceOffline) && route != Routes.POST,
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Ink.Surface3,
                        border = BorderStroke(1.dp, Ink.Line),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, null, tint = Ink.TextDim, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Offline · showing what's saved on this phone", style = MaterialTheme.typography.labelMedium, color = Ink.Text)
                        }
                    }
                }
            }
        }
    }

    // New release pop-up (only with background checks and update notifications both on).
    val locked by Graph.lock.locked.collectAsState()
    UpdatePopup(
        suppressed = route == Routes.UPDATES || locked,
        onDetails = { nav.navigate(Routes.UPDATES) { launchSingleTop = true } },
    )

    // App lock: drawn last, in its own window, so it covers everything above.
    LockGate()
}

@Composable
private fun AppNavHost(nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    val openPost: (Int) -> Unit = { id -> nav.navigate(Routes.post(id, fromList = true)) }
    val openSinglePost: (Int) -> Unit = { id -> nav.navigate(Routes.post(id, fromList = false)) }
    val searchPosts: (String) -> Unit = { q -> nav.navigate(Routes.search(q)) }
    val openTag: (String) -> Unit = { n -> nav.navigate(Routes.tag(n)) }
    val openPool: (Int) -> Unit = { id -> nav.navigate(Routes.pool(id)) }
    val openUser: (String) -> Unit = { n -> nav.navigate(Routes.user(n)) }
    val openComments: (String) -> Unit = { q -> nav.navigate(Routes.comments(q)) }
    val openHistory: (String) -> Unit = { q -> nav.navigate(Routes.history(q)) }

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        // Quick cross-fades (the default is 700 ms, long enough to tap the old screen by accident).
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) },
    ) {
        screen(Routes.HOME) {
            HomeScreen(
                HomeNav(
                    // Unfiltered "see all" links switch to the Posts tab, just like tapping it in the bar.
                    search = { q -> if (q.isBlank()) nav.selectTab("posts") else searchPosts(q) },
                    openPost = openPost,
                    openTag = openTag,
                    openPool = openPool,
                    tags = { nav.selectTab(Routes.TAGS) },
                    pools = { nav.selectTab(Routes.POOLS) },
                    comments = { openComments("") },
                    users = { nav.navigate(Routes.USERS) },
                    history = { openHistory("") },
                    imageSearch = { nav.navigate(Routes.similar()) },
                    settings = { nav.navigate(Routes.SETTINGS) },
                    upload = { nav.navigate(Routes.UPLOAD) },
                    updates = { nav.navigate(Routes.UPDATES) },
                    customize = { nav.navigate(Routes.HOME_LAYOUT) },
                ),
            )
        }
        screen(Routes.POSTS, arguments = listOf(strArg("q"))) {
            PostsScreen(canGoBack = false, onBack = back, onOpenPost = openPost, onUpload = { nav.navigate(Routes.UPLOAD) })
        }
        // Same screen as the Posts tab, but its own destination so a search never replaces the tab.
        screen(Routes.SEARCH, arguments = listOf(strArg("q"))) {
            PostsScreen(canGoBack = true, onBack = back, onOpenPost = openPost, onUpload = { nav.navigate(Routes.UPLOAD) })
        }
        screen(Routes.POST, arguments = listOf(strArg("id", null), strArg("src", "0"))) {
            PostViewerScreen(
                ViewerNav(
                    back = back,
                    searchTag = searchPosts,
                    openTag = openTag,
                    openPool = openPool,
                    openUser = openUser,
                    openPost = openSinglePost,
                    similar = { id -> nav.navigate(Routes.similar(id)) },
                    edit = { id -> nav.navigate(Routes.postEdit(id)) },
                ),
            )
        }
        screen(Routes.POST_EDIT, arguments = listOf(strArg("id", null))) {
            PostEditScreen(
                onBack = back,
                // Deleting or merging away the post also closes the viewer underneath.
                onDeleted = { nav.popBackStack(); nav.popBackStack() },
                onMerged = { target -> nav.popBackStack(); nav.popBackStack(); openSinglePost(target) },
            )
        }
        screen(Routes.TAGS) {
            TagsScreen(
                onOpenTag = openTag,
                onNewTag = { nav.navigate(Routes.tagEdit()) },
                onCategories = { nav.navigate(Routes.categories("tag")) },
            )
        }
        screen(Routes.TAG, arguments = listOf(strArg("name", null))) {
            TagDetailScreen(onBack = back, onSearch = searchPosts, onOpenTag = openTag, onEdit = { n -> nav.navigate(Routes.tagEdit(n)) })
        }
        screen(Routes.TAG_EDIT, arguments = listOf(strArg("name"))) { e ->
            val isNew = e.arguments?.getString("name").isNullOrEmpty()
            TagEditScreen(onBack = back, onSaved = { name -> nav.popBackStack(); if (isNew) openTag(name) })
        }
        screen(Routes.POOLS) {
            PoolsScreen(
                onOpenPool = openPool,
                onNewPool = { nav.navigate(Routes.poolEdit()) },
                onCategories = { nav.navigate(Routes.categories("pool")) },
            )
        }
        screen(Routes.POOL, arguments = listOf(strArg("id", null))) {
            PoolDetailScreen(
                onBack = back,
                onOpenPost = openPost,
                onSearch = searchPosts,
                onEdit = { id -> nav.navigate(Routes.poolEdit(id)) },
                // After a merge, replace this (now deleted) pool with the merge target.
                onOpenPool = { id -> nav.popBackStack(); openPool(id) },
            )
        }
        screen(Routes.POOL_EDIT, arguments = listOf(strArg("id"))) { e ->
            val isNew = e.arguments?.getString("id").isNullOrEmpty()
            PoolEditScreen(onBack = back, onSaved = { id -> nav.popBackStack(); if (isNew) openPool(id) })
        }
        screen(Routes.CATEGORIES, arguments = listOf(strArg("kind", null))) { e ->
            CategoriesScreen(kind = e.arguments?.getString("kind") ?: "tag", onBack = back)
        }
        screen(Routes.UPLOAD) { UploadScreen(onBack = back, onOpenPost = openSinglePost) }
        screen(Routes.COMMENTS, arguments = listOf(strArg("q"))) {
            CommentsScreen(onBack = back, onOpenPost = openSinglePost, onOpenUser = openUser)
        }
        screen(Routes.USERS) { UsersScreen(onBack = back, onOpenUser = openUser) }
        screen(Routes.USER, arguments = listOf(strArg("name", null))) {
            UserDetailScreen(onBack = back, onSearchPosts = searchPosts, onComments = openComments, onHistory = openHistory)
        }
        screen(Routes.ACCOUNT) {
            AccountScreen(
                AccountNav(
                    searchPosts = searchPosts,
                    comments = openComments,
                    history = openHistory,
                    users = { nav.navigate(Routes.USERS) },
                    tokens = { nav.navigate(Routes.TOKENS) },
                    similar = { nav.navigate(Routes.similar()) },
                    settings = { nav.navigate(Routes.SETTINGS) },
                    upload = { nav.navigate(Routes.UPLOAD) },
                ),
            )
        }
        screen(Routes.TOKENS) { TokensScreen(onBack = back) }
        screen(Routes.HISTORY, arguments = listOf(strArg("q"))) {
            HistoryScreen(HistoryNav(back = back, openPost = openSinglePost, openTag = openTag, openPool = openPool, openUser = openUser))
        }
        screen(Routes.SIMILAR, arguments = listOf(strArg("post"))) {
            ImageSearchScreen(onBack = back, onOpenPost = openPost)
        }
        screen(Routes.SETTINGS) {
            SettingsScreen(
                onBack = back,
                onUpdates = { nav.navigate(Routes.UPDATES) },
                onCustomizeHome = { nav.navigate(Routes.HOME_LAYOUT) },
                onOpenSection = { key -> nav.navigate(Routes.settingsPage(key)) },
            )
        }
        screen(Routes.SETTINGS_PAGE, arguments = listOf(strArg("page", null))) { e ->
            SettingsSectionScreen(
                key = e.arguments?.getString("page"),
                onBack = back,
                onOpenAccount = { nav.selectTab(Routes.ACCOUNT) },
            )
        }
        screen(Routes.HOME_LAYOUT) { HomeLayoutScreen(onBack = back) }
        screen(Routes.UPDATES) { UpdatesScreen(onBack = back) }
    }
}

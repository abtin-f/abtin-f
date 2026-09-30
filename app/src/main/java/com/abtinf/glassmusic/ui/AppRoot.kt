package com.abtinf.glassmusic.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.abtinf.glassmusic.data.MusicRepository
import com.abtinf.glassmusic.ui.browse.FoldersScreen
import com.abtinf.glassmusic.ui.components.AppTabBar
import com.abtinf.glassmusic.ui.components.LocalBackdrop
import com.abtinf.glassmusic.ui.components.MiniPlayer
import com.abtinf.glassmusic.ui.components.Tab
import com.abtinf.glassmusic.ui.detail.DetailScreen
import com.abtinf.glassmusic.ui.detail.MetadataScreen
import com.abtinf.glassmusic.ui.home.HomeScreen
import com.abtinf.glassmusic.ui.library.LibraryListScreen
import com.abtinf.glassmusic.ui.library.LibraryScreen
import com.abtinf.glassmusic.ui.nowplaying.NowPlayingScreen
import com.abtinf.glassmusic.ui.playlist.PlaylistEditor
import com.abtinf.glassmusic.ui.search.SearchScreen
import com.abtinf.glassmusic.ui.theme.LocalAm
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

private const val PLAYGROUND = "playground"

@Composable
fun AppRoot(vm: MusicViewModel = viewModel()) {
    val am = LocalAm.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()

    val ps by vm.playback.collectAsState()
    val menu by vm.menu.collectAsState()
    val picker by vm.pickerTracks.collectAsState()
    val showSettings by vm.showSettings.collectAsState()

    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    LaunchedEffect(route) { Tab.entries.firstOrNull { it.route == route }?.let { tab = it } }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshLibrary() }
    val requestPermission = { permissions.launch(MusicRepository.permissionsToRequest()) }
    LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, MusicRepository.audioPermission()) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) requestPermission()
    }

    // 0 = mini-player, 1 = full-screen Now Playing
    val expand = remember { Animatable(0f) }
    val expanded by remember { androidx.compose.runtime.derivedStateOf { expand.value > 0.001f } }
    BackHandler(enabled = expanded) { scope.launch { expand.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) } }

    val lightBars = false
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val c = WindowCompat.getInsetsController(window, view)
        c.isAppearanceLightStatusBars = lightBars
        c.isAppearanceLightNavigationBars = lightBars
    }

    fun settle(toExpanded: Boolean) {
        scope.launch { expand.animateTo(if (toExpanded) 1f else 0f, tween(300, easing = FastOutSlowInEasing)) }
    }

    fun goTab(t: Tab) {
        tab = t
        nav.navigate(t.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun openAlbum(id: Long) = nav.navigate("detail/album/$id")
    fun openArtist(id: Long) = nav.navigate("detail/artist/$id")
    fun openPlaylist(id: String) = nav.navigate("detail/playlist/$id")
    fun openList(kind: String) = nav.navigate("list/$kind")
    fun openMetadata(id: Long) = nav.navigate("meta/$id")
    fun openPlayground(playlistId: String?) {
        vm.openEditor(playlistId?.let { id -> vm.playlists.value.firstOrNull { it.id == id } })
        nav.navigate(PLAYGROUND)
    }

    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
    BoxWithConstraints(Modifier.fillMaxSize().background(am.background)) {
        val heightPx = with(density) { maxHeight.toPx() }
        var barsHeight by remember { mutableStateOf(0.dp) }
        val showBars = route != PLAYGROUND
        val bottomPad = if (showBars) barsHeight + 8.dp else 0.dp

        val dragModifier = Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = { settle(expand.value > 0.65f) },
                onDragCancel = { settle(expand.value > 0.65f) },
            ) { change, dy ->
                change.consume()
                scope.launch { expand.snapTo((expand.value - dy / heightPx).coerceIn(0f, 1f)) }
            }
        }
        val miniDragModifier = Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = { settle(expand.value > 0.3f) },
                onDragCancel = { settle(expand.value > 0.3f) },
            ) { change, dy ->
                change.consume()
                scope.launch { expand.snapTo((expand.value - dy / heightPx).coerceIn(0f, 1f)) }
            }
        }

        val fade = tween<Float>(240)
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
        NavHost(
            navController = nav,
            startDestination = Tab.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(fade) },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(fade) },
            popExitTransition = { fadeOut(tween(140)) },
        ) {
            composable(Tab.Home.route) {
                HomeScreen(vm, bottomPad, requestPermission, ::openAlbum, ::openArtist, ::openPlaylist, ::openMetadata, ::openList)
            }
            composable(Tab.Folders.route) {
                FoldersScreen(vm, bottomPad, onOpenFolder = { nav.navigate("detail/folder/$it") })
            }
            composable(Tab.Library.route) {
                LibraryScreen(vm, bottomPad, ::openList, ::openAlbum, onOpenSearch = { goTab(Tab.Search) })
            }
            composable(Tab.Search.route) {
                SearchScreen(vm, bottomPad, ::openAlbum, ::openArtist, ::openPlaylist, ::openList)
            }
            composable(
                "list/{kind}", arguments = listOf(navArgument("kind") { type = NavType.StringType }),
            ) { entry ->
                LibraryListScreen(
                    kind = entry.arguments?.getString("kind").orEmpty(), vm = vm, bottomPad = bottomPad,
                    onBack = { nav.popBackStack() }, onOpenAlbum = ::openAlbum, onOpenArtist = ::openArtist,
                    onOpenPlaylist = ::openPlaylist, onOpenMetadata = ::openMetadata, onNewPlaylist = { openPlayground(null) },
                )
            }
            composable(
                "detail/{kind}/{id}",
                arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                DetailScreen(
                    kind = entry.arguments?.getString("kind").orEmpty(),
                    id = entry.arguments?.getString("id").orEmpty(),
                    vm = vm, bottomPad = bottomPad, onBack = { nav.popBackStack() },
                    onOpenAlbum = ::openAlbum, onEditPlaylist = { openPlayground(it) }, onOpenMetadata = ::openMetadata,
                )
            }
            composable(
                "meta/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                MetadataScreen(entry.arguments?.getLong("id") ?: 0L, vm, bottomPad, onBack = { nav.popBackStack() })
            }
            composable(
                PLAYGROUND,
                enterTransition = { slideInVertically(tween(320)) { it / 6 } + fadeIn(fade) },
                popExitTransition = { slideOutVertically(tween(280)) { it / 6 } + fadeOut(tween(200)) },
            ) {
                val close = { vm.closeEditor(); nav.popBackStack(); Unit }
                BackHandler(onBack = close)
                PlaylistEditor(vm, onClose = close)
            }
        }

        }

        // floating mini-player + navigation pill
        AnimatedVisibility(
            visible = showBars,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260)) { it } + fadeIn(),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(),
        ) {
            Column(
                Modifier
                    .onSizeChanged { barsHeight = with(density) { it.height.toDp() } }
                    .background(Brush.verticalGradient(listOf(Color.Transparent, am.background.copy(alpha = 0.94f))))
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp)
                    .navigationBarsPadding(),
            ) {
                val current = ps.current
                AnimatedVisibility(
                    visible = current != null && ps.started,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 2 } + fadeIn(tween(220)) + expandVertically(spring(0.85f, 420f)),
                    exit = fadeOut(tween(160)) + shrinkVertically(),
                ) {
                    Column {
                        if (current != null) MiniPlayer(
                            track = current, isPlaying = ps.isPlaying,
                            onToggle = vm::togglePlay, onNext = vm::next, onExpand = { settle(true) },
                            modifier = miniDragModifier.graphicsLayer { alpha = (1f - expand.value * 3f).coerceIn(0f, 1f) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                AppTabBar(tab, ::goTab, backdrop)
            }
        }

        // Now Playing sheet: slides up from the mini-player
        if (expanded && ps.current != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = (1f - expand.value) * heightPx
                        shape = RoundedCornerShape(0.dp)
                        clip = true
                    },
            ) {
                NowPlayingScreen(
                    vm = vm,
                    dragModifier = dragModifier,
                    onCollapse = { settle(false) },
                    onOpenAlbum = ::openAlbum,
                    onOpenArtist = ::openArtist,
                )
            }
        }
    }
    }

    menu?.let { m -> TrackMenuSheet(m, vm, onOpenAlbum = { openAlbum(it); settle(false) }, onOpenArtist = { openArtist(it); settle(false) }) }
    picker?.let { PlaylistPickerDialog(it, vm) }
    if (showSettings) SettingsDialog(vm, requestPermission)
}

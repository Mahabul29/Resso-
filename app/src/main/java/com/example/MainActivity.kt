package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.deleteFirebaseAccount
import com.example.data.model.Song
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.components.LyricsBottomSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.components.QueueBottomSheet
import com.example.ui.components.SongMenuBottomSheet
import com.example.ui.navigation.EvaRoute
import com.example.ui.screens.ArtistScreen
import com.example.ui.screens.AddArtistScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.UserProfileScreen
import com.example.ui.screens.MusicLanguageScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.MeScreen
import com.example.ui.screens.MusicScreen
import com.example.ui.theme.EvaBackground
import com.example.ui.theme.EvaMusicTheme
import com.example.ui.theme.EvaOutline
import com.example.ui.theme.EvaTextMuted
import com.example.ui.viewmodel.EvaMusicViewModel
import com.example.util.AppLanguage
import com.example.util.findActivityOrNull
import com.example.util.openNotificationSettings
import com.example.util.openTelegram
import com.example.util.shareApp

class MainActivity : ComponentActivity() {
    // Applies the in-app language (English / Hindi) chosen in Settings.
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            EvaMusicTheme {
                EvaMusicApp()
            }
        }
    }
}

@Composable
fun EvaMusicApp(
    viewModel: EvaMusicViewModel = viewModel(factory = EvaMusicViewModel.Factory)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    // Runtime notification permission for Android 13+ background media controls
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Permission abhi mili hai to pending admin notification turant dikhao.
        if (granted) com.example.util.AdminNotifier.checkSoon(context)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTrendingIfNewDay()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isMusicTab = currentDestination?.hasRoute<EvaRoute.MusicTab>() == true
    val isExploreTab = currentDestination?.hasRoute<EvaRoute.ExploreTab>() == true
    val isMeTab = currentDestination?.hasRoute<EvaRoute.MeTab>() == true

    val authUser by viewModel.authUser.collectAsStateWithLifecycle()
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val trendingState by viewModel.trendingState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchResultsState by viewModel.searchResultsState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val artistSongsState by viewModel.artistSongsState.collectAsStateWithLifecycle()
    val exploreArtists by viewModel.exploreArtists.collectAsStateWithLifecycle()
    val exploreUsers by viewModel.exploreUsers.collectAsStateWithLifecycle()
    val viewedProfileState by viewModel.viewedProfileState.collectAsStateWithLifecycle()

    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val hasSwiped by viewModel.hasSwipedMusicFeed.collectAsStateWithLifecycle()
    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val historySongs by viewModel.historySongs.collectAsStateWithLifecycle()
    val historyCount by viewModel.historyCount.collectAsStateWithLifecycle()
    val meArtists by viewModel.meArtists.collectAsStateWithLifecycle()
    val followedArtistNames by viewModel.followedArtistNames.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val musicLanguages by viewModel.musicLanguages.collectAsStateWithLifecycle()
    val artistSearchState by viewModel.artistSearchState.collectAsStateWithLifecycle()
    val sleepTimerEndMs by viewModel.sleepTimerEndMs.collectAsStateWithLifecycle()
    val eqSettings by viewModel.eqSettings.collectAsStateWithLifecycle()
    val eqInfo by viewModel.eqInfo.collectAsStateWithLifecycle()

    val queue by viewModel.playerManager.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.playerManager.currentIndex.collectAsStateWithLifecycle()
    val currentSong by viewModel.playerManager.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by viewModel.playerManager.isBuffering.collectAsStateWithLifecycle()
    val positionMs by viewModel.playerManager.positionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.playerManager.durationMs.collectAsStateWithLifecycle()
    val contextTitle by viewModel.playerManager.contextTitle.collectAsStateWithLifecycle()
    val isFromList by viewModel.playerManager.isFromList.collectAsStateWithLifecycle()

    val currentSongStats by viewModel.currentSongStats.collectAsStateWithLifecycle()
    val activeLyricsSong by viewModel.activeLyricsSong.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val activeCommentsSong by viewModel.activeCommentsSong.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()

    var showQueueSheet by remember { mutableStateOf(false) }
    var menuTargetSong by remember { mutableStateOf<Song?>(null) }

    fun navigateToTab(route: EvaRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = EvaBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EvaBackground)
            ) {
                if (!isMusicTab && currentSong != null) {
                    MiniPlayer(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
                        onClick = { navigateToTab(EvaRoute.MusicTab) }
                    )
                }

                HorizontalDivider(color = EvaOutline.copy(alpha = 0.65f))

                EvaBottomBar(
                    isMusicSelected = isMusicTab,
                    isExploreSelected = isExploreTab,
                    isMeSelected = isMeTab,
                    onSelectMusic = { navigateToTab(EvaRoute.MusicTab) },
                    onSelectExplore = { navigateToTab(EvaRoute.ExploreTab) },
                    onSelectMe = { navigateToTab(EvaRoute.MeTab) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = EvaRoute.MusicTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable<EvaRoute.MusicTab> {
                MusicScreen(
                    feedState = feedState,
                    queue = queue,
                    currentIndex = currentIndex,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    contextTitle = contextTitle,
                    isFromList = isFromList,
                    hasSwipedBefore = hasSwiped,
                    likedSongIds = likedIds,
                    followedArtists = followedArtistNames,
                    currentSongStats = currentSongStats,
                    onSelectQueueIndex = { idx -> viewModel.playerManager.selectQueueIndex(idx) },
                    onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
                    onSeekTo = { pos -> viewModel.playerManager.seekTo(pos) },
                    onToggleLike = { song -> viewModel.toggleLike(song) },
                    onDoubleTapLike = { song -> viewModel.likeFromDoubleTap(song) },
                    onToggleFollowArtist = { name, img -> viewModel.toggleFollowArtist(name, img) },
                    onOpenComments = { song -> viewModel.openCommentsFor(song) },
                    onOpenQueue = { showQueueSheet = true },
                    onOpenMenu = { song -> menuTargetSong = song },
                    onOpenArtist = { artistName, imageUrl ->
                        navController.navigate(EvaRoute.Artist(artistName, imageUrl))
                    },
                    onShareApp = { shareApp(context) },
                    onBackClick = {
                        viewModel.playerManager.resetFromListFlag()
                        navController.popBackStack()
                    },
                    onUserSwiped = { viewModel.markSwipedMusicFeed() },
                    onRetryFeed = { viewModel.loadInitialFeedAndTrending() },
                    selectedLanguages = musicLanguages,
                    onSelectLanguage = { viewModel.selectMusicLanguage(it) }
                )
            }

            composable<EvaRoute.ExploreTab> {
                ExploreScreen(
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory,
                    recentSearches = recentSearches,
                    trendingState = trendingState,
                    searchResultsState = searchResultsState,
                    artistResults = exploreArtists,
                    userResults = exploreUsers,
                    currentSongId = currentSong?.id,
                    onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onClearSearch = { viewModel.clearSearch() },
                    onCategoryClick = { viewModel.onCategorySelected(it) },
                    onRecentSearchClick = { viewModel.onRecentSearchSelected(it) },
                    onClearRecentSearches = { viewModel.clearRecentSearches() },
                    onPlaySongFromList = { list, index, _ ->
                        list.getOrNull(index)?.let { viewModel.playSongWithRelated(it) }
                        navigateToTab(EvaRoute.MusicTab)
                    },
                    onOpenMenu = { song -> menuTargetSong = song },
                    onOpenArtist = { artistName, imageUrl ->
                        navController.navigate(EvaRoute.Artist(artistName, imageUrl))
                    },
                    onOpenUser = { uid -> navController.navigate(EvaRoute.UserProfile(uid)) },
                    onRetryTrending = { viewModel.loadInitialFeedAndTrending() }
                )
            }

            composable<EvaRoute.MeTab> {
                MeScreen(
                    displayName = displayName,
                    authUser = authUser,
                    likedSongs = likedSongs,
                    artists = meArtists,
                    historySongs = historySongs,
                    followedCount = followedArtistNames.size,
                    playedCount = historyCount,
                    currentSongId = currentSong?.id,
                    onUpdateDisplayName = { viewModel.updateDisplayName(it) },
                    onUpdateProfile = { name, user -> viewModel.updateProfile(name, user) },
                    onPlaySongs = { list, index, title ->
                        viewModel.playFromList(list, index, title)
                        navigateToTab(EvaRoute.MusicTab)
                    },
                    onPlaySong = { song ->
                        viewModel.playSongWithRelated(song)
                        navigateToTab(EvaRoute.MusicTab)
                    },
                    onShuffleSongs = { list, title ->
                        viewModel.playShuffled(list, title)
                        navigateToTab(EvaRoute.MusicTab)
                    },
                    onToggleFollowArtist = { name, img -> viewModel.toggleFollowArtist(name, img) },
                    onOpenArtist = { artistName, imageUrl ->
                        navController.navigate(EvaRoute.Artist(artistName, imageUrl))
                    },
                    onOpenMenu = { song -> menuTargetSong = song },
                    onAuthMessage = { msg -> viewModel.showMessage(msg) },
                    onOpenSettings = { navController.navigate(EvaRoute.Settings) },
                    username = username
                )
            }

            composable<EvaRoute.Settings> {
                SettingsScreen(
                    displayName = displayName,
                    username = username,
                    email = authUser?.email,
                    musicLanguages = musicLanguages,
                    appLanguage = AppLanguage.get(context),
                    sleepTimerEndMs = sleepTimerEndMs,
                    onBack = { navController.popBackStack() },
                    onUpdateProfile = { name, user -> viewModel.updateProfile(name, user) },
                    onOpenMusicLanguage = { navController.navigate(EvaRoute.MusicLanguage) },
                    onSetAppLanguage = { code ->
                        AppLanguage.set(context, code)
                        // Rebuild the screen so every text is read again in the new language.
                        context.findActivityOrNull()?.recreate()
                    },
                    onOpenAddArtist = { navController.navigate(EvaRoute.AddArtist) },
                    onOpenEqualizer = { navController.navigate(EvaRoute.Equalizer) },
                    onStartSleepTimer = { minutes ->
                        viewModel.startSleepTimer(minutes)
                        viewModel.showMessage(context.getString(R.string.sleep_timer_set, minutes))
                    },
                    onCancelSleepTimer = {
                        viewModel.cancelSleepTimer()
                        viewModel.showMessage(context.getString(R.string.sleep_timer_cancelled))
                    },
                    onOpenNotifications = { openNotificationSettings(context) },
                    onShareApp = { shareApp(context) },
                    onJoinTelegram = { openTelegram(context) },
                    onDeleteAccount = { onFinished ->
                        scope.launch {
                            val result = deleteFirebaseAccount(context, credentialManager)
                            if (result.isSuccess) {
                                viewModel.wipeLocalDataAfterAccountDelete {
                                    viewModel.showMessage(context.getString(R.string.account_deleted))
                                    onFinished(true)
                                    navController.popBackStack(EvaRoute.MeTab, inclusive = false)
                                }
                            } else {
                                viewModel.showMessage(context.getString(R.string.account_delete_failed))
                                onFinished(false)
                            }
                        }
                    },
                    versionName = BuildConfig.VERSION_NAME
                )
            }

            composable<EvaRoute.MusicLanguage> {
                MusicLanguageScreen(
                    initialSelection = musicLanguages,
                    onSave = { viewModel.setMusicLanguages(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<EvaRoute.AddArtist> {
                AddArtistScreen(
                    searchState = artistSearchState,
                    followedArtistNames = followedArtistNames,
                    onSearch = { viewModel.searchArtists(it) },
                    onClearSearch = { viewModel.clearArtistSearch() },
                    onToggleFollow = { name, img -> viewModel.toggleFollowArtist(name, img) },
                    onOpenArtist = { artistName, imageUrl ->
                        navController.navigate(EvaRoute.Artist(artistName, imageUrl))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<EvaRoute.Equalizer> {
                EqualizerScreen(
                    settings = eqSettings,
                    info = eqInfo,
                    onUpdate = { settings, persist -> viewModel.updateEqualizer(settings, persist) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<EvaRoute.UserProfile> { backStackEntry ->
                val route = backStackEntry.toRoute<EvaRoute.UserProfile>()
                UserProfileScreen(
                    uid = route.uid,
                    profileState = viewedProfileState,
                    currentSongId = currentSong?.id,
                    onLoadProfile = { viewModel.loadUserProfile(it) },
                    onPlaySongs = { list, index, title ->
                        viewModel.playFromList(list, index, title)
                        navigateToTab(EvaRoute.MusicTab)
                    },
                    onShuffleSongs = { list, title ->
                        viewModel.playShuffled(list, title)
                        navigateToTab(EvaRoute.MusicTab)
                    },
                    onOpenArtist = { artistName, imageUrl ->
                        navController.navigate(EvaRoute.Artist(artistName, imageUrl))
                    },
                    onOpenMenu = { song -> menuTargetSong = song },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<EvaRoute.Artist> { backStackEntry ->
                val route = backStackEntry.toRoute<EvaRoute.Artist>()
                ArtistScreen(
                    artistName = route.artistName,
                    initialImageUrl = route.imageUrl,
                    isFollowed = viewModel.isArtistFollowed(route.artistName),
                    songsState = artistSongsState,
                    currentSongId = currentSong?.id,
                    onLoadArtistSongs = { viewModel.loadArtistSongs(it) },
                    onToggleFollow = { name, img -> viewModel.toggleFollowArtist(name, img) },
                    onPlaySongs = { list, index, title ->
                        viewModel.playFromList(list, index, title)
                        navigateToTab(EvaRoute.MusicTab)
                    },
                    onPlaySong = { song ->
                        viewModel.playSongWithRelated(song)
                        navigateToTab(EvaRoute.MusicTab)
                    },
                    onShuffleSongs = { list, title ->
                        viewModel.playShuffled(list, title)
                        navigateToTab(EvaRoute.MusicTab)
                    },
                    onOpenMenu = { song -> menuTargetSong = song },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    // Modal Bottom Sheets
    if (showQueueSheet) {
        QueueBottomSheet(
            queue = queue,
            currentIndex = currentIndex,
            contextTitle = contextTitle,
            onSelectIndex = { idx -> viewModel.playerManager.selectQueueIndex(idx) },
            onDismiss = { showQueueSheet = false }
        )
    }

    menuTargetSong?.let { target ->
        SongMenuBottomSheet(
            song = target,
            isLiked = target.id in likedIds,
            onToggleLike = { viewModel.toggleLike(target) },
            onOpenComments = { viewModel.openCommentsFor(target) },
            onOpenLyrics = { viewModel.openLyricsFor(target) },
            onPlayNext = {
                viewModel.playerManager.playNext(target)
                viewModel.showMessage("Playing next: ${target.title}")
            },
            onAddToQueue = {
                viewModel.playerManager.addToQueue(target)
                viewModel.showMessage("Added to queue: ${target.title}")
            },
            onGoToArtist = {
                navController.navigate(
                    EvaRoute.Artist(
                        artistName = target.firstArtist,
                        imageUrl = target.primaryArtistImage.ifBlank { target.image }
                    )
                )
            },
            onShareApp = { shareApp(context) },
            onDismiss = { menuTargetSong = null }
        )
    }

    activeLyricsSong?.let { target ->
        LyricsBottomSheet(
            song = target,
            lyricsState = lyricsState,
            positionMs = if (currentSong?.id == target.id) positionMs else null,
            onSeekTo = { viewModel.playerManager.seekTo(it) },
            onRetry = { viewModel.loadLyrics(target) },
            onDismiss = { viewModel.closeLyrics() }
        )
    }

    activeCommentsSong?.let { target ->
        CommentsBottomSheet(
            song = target,
            commentsState = commentsState,
            isAuthenticated = authUser != null,
            currentUid = authUser?.uid,
            onAddComment = { text -> viewModel.addComment(target, text) },
            onDeleteComment = { comment -> viewModel.deleteComment(target, comment) },
            onAuthMessage = { msg -> viewModel.showMessage(msg) },
            onDismiss = { viewModel.closeComments() }
        )
    }
}

@Composable
private fun EvaBottomBar(
    isMusicSelected: Boolean,
    isExploreSelected: Boolean,
    isMeSelected: Boolean,
    onSelectMusic: () -> Unit,
    onSelectExplore: () -> Unit,
    onSelectMe: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EvaBackground)
            .navigationBarsPadding()
            .height(58.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavTab(
            label = stringResource(R.string.nav_music),
            icon = Icons.Filled.GraphicEq,
            selected = isMusicSelected,
            testTag = "bottom_tab_music",
            onClick = onSelectMusic,
            modifier = Modifier.weight(1f)
        )
        BottomNavTab(
            label = stringResource(R.string.nav_explore),
            icon = if (isExploreSelected) Icons.Filled.Search else Icons.Outlined.Search,
            selected = isExploreSelected,
            testTag = "bottom_tab_explore",
            onClick = onSelectExplore,
            modifier = Modifier.weight(1f)
        )
        BottomNavTab(
            label = stringResource(R.string.nav_me),
            icon = if (isMeSelected) Icons.Filled.Person else Icons.Outlined.Person,
            selected = isMeSelected,
            testTag = "bottom_tab_me",
            onClick = onSelectMe,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BottomNavTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) Color.White else EvaTextMuted
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = contentColor,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            )
        }
    }
}

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Song
import com.example.data.model.SongStats
import com.example.ui.components.BlurredBackgroundImage
import com.example.ui.components.FollowChip
import com.example.ui.components.OutlinedUppercaseTitle
import com.example.ui.theme.EvaBackground
import com.example.ui.theme.EvaOrange
import com.example.ui.theme.EvaPinkRed
import com.example.ui.theme.EvaSurfaceElevated
import com.example.ui.theme.EvaTextPrimary
import com.example.ui.theme.EvaTextSecondary
import com.example.ui.viewmodel.UiState
import com.example.util.formatCompactCount
import com.example.util.formatRemainingMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun MusicScreen(
    feedState: UiState<List<Song>>,
    queue: List<Song>,
    currentIndex: Int,
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    contextTitle: String,
    isFromList: Boolean,
    hasSwipedBefore: Boolean,
    likedSongIds: Set<String>,
    followedArtists: Set<String>,
    currentSongStats: SongStats?,
    onSelectQueueIndex: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleLike: (Song) -> Unit,
    onDoubleTapLike: (Song) -> Unit,
    onToggleFollowArtist: (String, String) -> Unit,
    onOpenComments: (Song) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenMenu: (Song) -> Unit,
    onOpenArtist: (String, String) -> Unit,
    onShareApp: () -> Unit,
    onBackClick: () -> Unit,
    onUserSwiped: () -> Unit,
    onRetryFeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EvaBackground)
    ) {
        if (queue.isEmpty()) {
            when (feedState) {
                is UiState.Loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = EvaPinkRed)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.loading_music),
                            style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextSecondary)
                        )
                    }
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = feedState.message,
                            style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextSecondary),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRetryFeed,
                            colors = ButtonDefaults.buttonColors(containerColor = EvaPinkRed)
                        ) {
                            Text(stringResource(R.string.retry), color = Color.White)
                        }
                    }
                }
                is UiState.Success -> {
                    CircularProgressIndicator(
                        color = EvaPinkRed,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            return@Box
        }

        val pagerState = rememberPagerState(
            initialPage = currentIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0)),
            pageCount = { queue.size }
        )

        // Sync external queue index selection -> VerticalPager
        LaunchedEffect(currentIndex, queue.size) {
            val safeIndex = currentIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
            if (pagerState.currentPage != safeIndex && !pagerState.isScrollInProgress) {
                pagerState.scrollToPage(safeIndex)
            }
        }

        // Sync VerticalPager user swipe -> PlayerManager
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .collect { settled ->
                    if (settled != currentIndex && settled in queue.indices) {
                        onUserSwiped()
                        onSelectQueueIndex(settled)
                    }
                }
        }

        val activeSong = currentSong ?: queue.getOrNull(pagerState.currentPage) ?: queue.first()

        BlurredBackgroundImage(
            imageUrl = activeSong.image,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFromList) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("music_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Text(
                    text = contextTitle.ifBlank { stringResource(R.string.context_daily_mix) },
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { onOpenMenu(activeSong) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("music_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Song menu",
                        tint = Color.White
                    )
                }
            }

            // Full-screen vertical swipe feed
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                VerticalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    key = { index -> queue.getOrNull(index)?.id ?: index },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("music_vertical_pager")
                ) { page ->
                    val song = queue[page]
                    val isCurrentPage = page == pagerState.currentPage
                    val isArtistFollowed = song.firstArtist.lowercase() in followedArtists

                    SongFeedPage(
                        song = song,
                        isPaused = isCurrentPage && !isPlaying && !isBuffering,
                        isBuffering = isCurrentPage && isBuffering,
                        isArtistFollowed = isArtistFollowed,
                        onTapTogglePlay = onTogglePlayPause,
                        onDoubleTapLike = { onDoubleTapLike(song) },
                        onArtistClick = {
                            onOpenArtist(
                                song.firstArtist,
                                song.primaryArtistImage.ifBlank { song.image }
                            )
                        },
                        onFollowClick = {
                            onToggleFollowArtist(
                                song.firstArtist,
                                song.primaryArtistImage.ifBlank { song.image }
                            )
                        }
                    )
                }

                // Animated swipe-up chevrons until user swipes for the first time
                if (!hasSwipedBefore && queue.size > 1) {
                    SwipeUpChevronsHint(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 14.dp, top = 70.dp)
                    )
                }
            }

            // Action row + Seek bar below pager
            val isLiked = activeSong.id in likedSongIds
            val displayLikes = (currentSongStats?.likeCount ?: 0L).let { remote ->
                if (isLiked && remote == 0L) 1L else remote
            }
            val displayComments = currentSongStats?.commentCount ?: 0L

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Like + count
                    ActionCountButton(
                        icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        tint = if (isLiked) EvaPinkRed else Color.White,
                        countText = if (displayLikes > 0L) formatCompactCount(displayLikes) else "",
                        contentDescription = if (isLiked) "Unlike song" else "Like song",
                        testTag = "music_like_button",
                        onClick = { onToggleLike(activeSong) }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Comment + count
                    ActionCountButton(
                        icon = Icons.Filled.ChatBubbleOutline,
                        tint = Color.White,
                        countText = if (displayComments > 0L) formatCompactCount(displayComments) else "",
                        contentDescription = "Comments",
                        testTag = "music_comment_button",
                        onClick = { onOpenComments(activeSong) }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Share app (flipped Reply icon looks like forward share arrow)
                    IconButton(
                        onClick = onShareApp,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("music_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Reply,
                            contentDescription = "Share Resso",
                            tint = Color.White,
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer { scaleX = -1f }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Queue icon on the right (no download icon)
                    IconButton(
                        onClick = onOpenQueue,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("music_queue_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Play/Pause + thin draggable seek bar + remaining time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("music_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    ThinGradientSeekBar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeekTo = onSeekTo,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    )

                    Text(
                        text = formatRemainingMs(positionMs, durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.75f)
                        ),
                        modifier = Modifier.padding(start = 6.dp, end = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SongFeedPage(
    song: Song,
    isPaused: Boolean,
    isBuffering: Boolean,
    isArtistFollowed: Boolean,
    onTapTogglePlay: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onArtistClick: () -> Unit,
    onFollowClick: () -> Unit
) {
    var showHeartBurst by remember(song.id) { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (showHeartBurst) 1.25f else 0.4f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "heartBurstScale"
    )

    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            delay(650L)
            showHeartBurst = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Two large lines at the top: title in white, album in dim grey
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = song.album,
            style = MaterialTheme.typography.titleMedium.copy(
                color = Color.White.copy(alpha = 0.45f),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Full-width album art with gradient fade and big outlined title at bottom
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(EvaSurfaceElevated)
                .pointerInput(song.id) {
                    detectTapGestures(
                        onTap = { onTapTogglePlay() },
                        onDoubleTap = {
                            showHeartBurst = true
                            onDoubleTapLike()
                        }
                    )
                }
        ) {
            if (song.image.isNotBlank()) {
                AsyncImage(
                    model = song.image,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom gradient fading smoothly into dark background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x990E0C13),
                                Color(0xEB0E0C13)
                            )
                        )
                    )
            )

            // Big outlined uppercase song title at bottom of the art
            OutlinedUppercaseTitle(
                title = song.title,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )

            // Paused big play icon or buffering spinner
            androidx.compose.animation.AnimatedVisibility(
                visible = isPaused && !showHeartBurst,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(Color(0x880E0C13)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Paused, tap to play",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            if (isBuffering && !isPaused) {
                CircularProgressIndicator(
                    color = EvaOrange,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(42.dp)
                        .align(Alignment.Center)
                )
            }

            // Double-tap heart burst animation
            androidx.compose.animation.AnimatedVisibility(
                visible = showHeartBurst,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(260)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = EvaPinkRed,
                    modifier = Modifier
                        .size(96.dp)
                        .scale(heartScale)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Full title + artist name (tap opens artist screen) + Follow/Following chip
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleLarge.copy(
                color = EvaTextPrimary,
                fontSize = 19.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = EvaTextSecondary,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onArtistClick)
                    .padding(vertical = 4.dp)
                    .testTag("music_artist_name")
            )
            Spacer(modifier = Modifier.width(10.dp))
            FollowChip(
                isFollowed = isArtistFollowed,
                onClick = onFollowClick,
                modifier = Modifier.testTag("music_follow_chip")
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun ActionCountButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    countText: String,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
        if (countText.isNotBlank()) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
private fun ThinGradientSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var lastSentSeekMs by remember { mutableLongStateOf(0L) }

    val fraction = if (isDragging) {
        dragFraction
    } else if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .height(32.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0L) {
                        val f = (offset.x / widthPx).coerceIn(0f, 1f)
                        onSeekTo((f * durationMs).toLong())
                    }
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        if (durationMs > 0L) {
                            val target = (dragFraction * durationMs).toLong()
                            lastSentSeekMs = target
                            onSeekTo(target)
                        }
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val trackHeight = 3.dp.toPx()
            val centerY = size.height / 2f
            val activeWidth = (size.width * fraction).coerceIn(0f, size.width)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.24f),
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )

            if (activeWidth > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(EvaOrange, EvaPinkRed)),
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(activeWidth, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )
            }

            val thumbRadius = if (isDragging) 6.5.dp.toPx() else 4.5.dp.toPx()
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(activeWidth, centerY)
            )
        }
    }
}

@Composable
private fun SwipeUpChevronsHint(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "swipeHint")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevronFloat"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevronAlpha"
    )

    Column(
        modifier = modifier
            .graphicsLayer { translationY = offsetY }
            .alpha(pulseAlpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-14).dp)
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = "Swipe up for next song",
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(30.dp)
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(30.dp)
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(30.dp)
        )
    }
}

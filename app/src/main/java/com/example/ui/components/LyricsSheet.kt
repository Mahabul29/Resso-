package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Lyrics
import com.example.data.model.Song
import com.example.ui.theme.EvaOrange
import com.example.ui.theme.EvaSurface
import com.example.ui.theme.EvaTextMuted
import com.example.ui.theme.EvaTextPrimary
import com.example.ui.theme.EvaTextSecondary
import com.example.ui.viewmodel.UiState

/**
 * Lyrics for [song]. When the lyrics carry timestamps and the song is the one playing
 * ([positionMs] != null), the current line is highlighted and scrolled into view, and tapping a
 * line jumps there ([onSeekTo]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsBottomSheet(
    song: Song,
    lyricsState: UiState<Lyrics>,
    positionMs: Long?,
    onSeekTo: (Long) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EvaSurface,
        contentColor = EvaTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 360.dp, max = 620.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${stringResource(R.string.lyrics_title)} • ${song.artist}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when (lyricsState) {
                is UiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EvaOrange)
                }

                is UiState.Error -> CenteredLyricsMessage(
                    message = stringResource(R.string.lyrics_load_failed),
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry
                )

                is UiState.Success -> {
                    val lyrics = lyricsState.data
                    if (lyrics.isEmpty) {
                        CenteredLyricsMessage(
                            message = stringResource(R.string.lyrics_not_available),
                            actionLabel = null,
                            onAction = {}
                        )
                    } else {
                        LyricsList(lyrics = lyrics, positionMs = positionMs, onSeekTo = onSeekTo)
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsList(
    lyrics: Lyrics,
    positionMs: Long?,
    onSeekTo: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val synced = lyrics.isSynced

    // Index of the line being sung: the last line whose time has been reached.
    val currentIndex by remember(lyrics, positionMs) {
        derivedStateOf {
            if (!synced || positionMs == null) {
                -1
            } else {
                var idx = -1
                for (i in lyrics.lines.indices) {
                    val t = lyrics.lines[i].timeMs ?: continue
                    if (t <= positionMs + LOOK_AHEAD_MS) idx = i else break
                }
                idx
            }
        }
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            if (line.text.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                return@itemsIndexed
            }
            val isCurrent = index == currentIndex
            val seekTime = line.timeMs
            Text(
                text = line.text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (isCurrent) 22.sp else 18.sp,
                    lineHeight = if (isCurrent) 30.sp else 26.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        !synced -> EvaTextPrimary
                        isCurrent -> EvaTextPrimary
                        else -> EvaTextMuted
                    }
                ),
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (positionMs != null && seekTime != null) {
                            Modifier.clickable { onSeekTo(seekTime) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun CenteredLyricsMessage(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextSecondary),
            textAlign = TextAlign.Center
        )
        if (actionLabel != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = EvaOrange)
            }
        }
    }
}

/** Highlights a line a little before its exact time so it feels in sync. */
private const val LOOK_AHEAD_MS = 250L

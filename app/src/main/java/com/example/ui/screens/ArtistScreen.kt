package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Song
import com.example.ui.components.BlurredBackgroundImage
import com.example.ui.components.FollowChip
import com.example.ui.components.PlayShuffleActionRow
import com.example.ui.components.SongListRow
import com.example.ui.theme.EvaAccentDiagonalGradient
import com.example.ui.theme.EvaBackground
import com.example.ui.theme.EvaOrange
import com.example.ui.theme.EvaPinkRed
import com.example.ui.theme.EvaSurfaceElevated
import com.example.ui.theme.EvaTextPrimary
import com.example.ui.theme.EvaTextSecondary
import com.example.ui.viewmodel.UiState

@Composable
fun ArtistScreen(
    artistName: String,
    initialImageUrl: String,
    isFollowed: Boolean,
    songsState: UiState<List<Song>>,
    currentSongId: String?,
    onLoadArtistSongs: (String) -> Unit,
    onToggleFollow: (String, String) -> Unit,
    onPlaySongs: (songs: List<Song>, startIndex: Int, contextTitle: String) -> Unit,
    onPlaySong: (Song) -> Unit,
    onShuffleSongs: (songs: List<Song>, contextTitle: String) -> Unit,
    onOpenMenu: (Song) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(artistName) {
        onLoadArtistSongs(artistName)
    }

    val songs = (songsState as? UiState.Success)?.data.orEmpty()
    val heroImage = initialImageUrl.ifBlank {
        songs.firstOrNull()?.primaryArtistImage?.ifBlank { songs.firstOrNull()?.image.orEmpty() }.orEmpty()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EvaBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp)
                ) {
                    BlurredBackgroundImage(
                        imageUrl = heroImage,
                        modifier = Modifier.fillMaxSize()
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("artist_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(98.dp)
                                .clip(CircleShape)
                                .background(EvaSurfaceElevated)
                                .border(2.5.dp, EvaAccentDiagonalGradient, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = artistName.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                                style = MaterialTheme.typography.displayLarge.copy(color = EvaOrange)
                            )
                            if (heroImage.isNotBlank()) {
                                AsyncImage(
                                    model = heroImage,
                                    contentDescription = artistName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = EvaTextPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        FollowChip(
                            isFollowed = isFollowed,
                            onClick = { onToggleFollow(artistName, heroImage) },
                            modifier = Modifier.testTag("artist_follow_chip")
                        )
                    }
                }
            }

            when (songsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = EvaPinkRed)
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = songsState.message,
                                style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextSecondary),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onLoadArtistSongs(artistName) },
                                colors = ButtonDefaults.buttonColors(containerColor = EvaPinkRed)
                            ) {
                                Text(stringResource(R.string.retry), color = Color.White)
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    if (songs.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                PlayShuffleActionRow(
                                    onPlayClick = { onPlaySongs(songs, 0, "$artistName Mix") },
                                    onShuffleClick = { onShuffleSongs(songs, "$artistName Mix") }
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "${songs.size} songs",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary)
                                )
                            }
                        }
                        itemsIndexed(songs, key = { idx, s -> "artist_${s.id}_$idx" }) { idx, song ->
                            SongListRow(
                                song = song,
                                isCurrentlyPlaying = song.id == currentSongId,
                                onClick = { onPlaySong(song) },
                                onMenuClick = { onOpenMenu(song) }
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "No tracks found for $artistName.",
                                style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextSecondary),
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

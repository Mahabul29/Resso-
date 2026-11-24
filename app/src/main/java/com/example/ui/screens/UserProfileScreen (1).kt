package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.model.ArtistInfo
import com.example.data.model.Song
import com.example.data.model.ViewedProfile
import com.example.ui.components.BlurredBackgroundImage
import com.example.ui.components.PlayShuffleActionRow
import com.example.ui.components.SongListRow
import com.example.ui.theme.EvaAccentDiagonalGradient
import com.example.ui.theme.EvaBackground
import com.example.ui.theme.EvaOrange
import com.example.ui.theme.EvaOutline
import com.example.ui.theme.EvaPinkRed
import com.example.ui.theme.EvaSurfaceElevated
import com.example.ui.theme.EvaTextPrimary
import com.example.ui.theme.EvaTextSecondary
import com.example.ui.viewmodel.UiState

/** Another listener's profile: name, @username, the artists they like and their liked songs. */
@Composable
fun UserProfileScreen(
    uid: String,
    profileState: UiState<ViewedProfile>,
    currentSongId: String?,
    onLoadProfile: (String) -> Unit,
    onPlaySongs: (songs: List<Song>, startIndex: Int, contextTitle: String) -> Unit,
    onShuffleSongs: (songs: List<Song>, contextTitle: String) -> Unit,
    onOpenArtist: (artistName: String, imageUrl: String) -> Unit,
    onOpenMenu: (Song) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(uid) {
        onLoadProfile(uid)
    }

    val data = (profileState as? UiState.Success)?.data
    val shownName = data?.profile?.displayName?.ifBlank { data.profile.username }.orEmpty()
    val photoUrl = data?.profile?.photoUrl.orEmpty()
    val heroImage = photoUrl.ifBlank { data?.likedSongs?.firstOrNull()?.image.orEmpty() }

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
                        .height(270.dp)
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
                                    .testTag("user_profile_back_button")
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
                                text = shownName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                style = MaterialTheme.typography.displayLarge.copy(color = EvaOrange)
                            )
                            if (photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = shownName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = shownName,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = EvaTextPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        if (!data?.profile?.username.isNullOrBlank()) {
                            Text(
                                text = "@${data?.profile?.username}",
                                style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextSecondary)
                            )
                        }
                    }
                }
            }

            when (profileState) {
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
                                text = profileState.message,
                                style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextSecondary),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onLoadProfile(uid) },
                                colors = ButtonDefaults.buttonColors(containerColor = EvaPinkRed)
                            ) {
                                Text(stringResource(R.string.retry), color = Color.White)
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    val liked = profileState.data.likedSongs
                    val artists = profileState.data.artists
                    val listTitle = "$shownName's Liked Songs"

                    if (artists.isNotEmpty()) {
                        item {
                            Text(
                                text = "Artists",
                                style = MaterialTheme.typography.titleLarge.copy(color = EvaTextPrimary),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(artists, key = { "profile_artist_${it.name}" }) { artist ->
                                    ProfileArtistCard(
                                        artist = artist,
                                        onClick = { onOpenArtist(artist.name, artist.imageUrl) }
                                    )
                                }
                            }
                        }
                    }

                    if (liked.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                PlayShuffleActionRow(
                                    onPlayClick = { onPlaySongs(liked, 0, listTitle) },
                                    onShuffleClick = { onShuffleSongs(liked, listTitle) }
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Liked Songs · ${liked.size}",
                                    style = MaterialTheme.typography.titleLarge.copy(color = EvaTextPrimary)
                                )
                            }
                        }
                        itemsIndexed(liked, key = { idx, s -> "profile_liked_${s.id}_$idx" }) { idx, song ->
                            SongListRow(
                                song = song,
                                isCurrentlyPlaying = song.id == currentSongId,
                                onClick = { onPlaySongs(liked, idx, listTitle) },
                                onMenuClick = { onOpenMenu(song) }
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "No liked songs yet.",
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

@Composable
private fun ProfileArtistCard(
    artist: ArtistInfo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(EvaSurfaceElevated)
                .border(1.dp, EvaOutline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = artist.name.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                style = MaterialTheme.typography.headlineMedium.copy(color = EvaOrange)
            )
            if (artist.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = artist.imageUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelLarge.copy(color = EvaTextPrimary),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

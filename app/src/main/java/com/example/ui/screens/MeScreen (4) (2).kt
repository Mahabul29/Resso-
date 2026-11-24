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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.R
import com.example.data.firebase.onGoogleSignInClicked
import com.example.data.firebase.signOutUser
import com.example.data.model.ArtistInfo
import com.example.data.model.Song
import com.example.ui.components.FollowChip
import com.example.ui.components.MonetagAd
import com.example.ui.components.PlayShuffleActionRow
import com.example.ui.components.SongListRow
import com.example.ui.theme.EvaAccentDiagonalGradient
import com.example.ui.theme.EvaAccentGradient
import com.example.ui.theme.EvaBackground
import com.example.ui.theme.EvaOrange
import com.example.ui.theme.EvaOutline
import com.example.ui.theme.EvaPinkRed
import com.example.ui.theme.EvaSurface
import com.example.ui.theme.EvaSurfaceElevated
import com.example.ui.theme.EvaTextMuted
import com.example.ui.theme.EvaTextPrimary
import com.example.ui.theme.EvaTextSecondary
import com.google.firebase.auth.FirebaseUser

@Composable
fun MeScreen(
    displayName: String,
    authUser: FirebaseUser?,
    likedSongs: List<Song>,
    artists: List<ArtistInfo>,
    historySongs: List<Song>,
    followedCount: Int,
    playedCount: Int,
    currentSongId: String?,
    onUpdateDisplayName: (String) -> Unit,
    onPlaySongs: (songs: List<Song>, startIndex: Int, contextTitle: String) -> Unit,
    onPlaySong: (Song) -> Unit,
    onShuffleSongs: (songs: List<Song>, contextTitle: String) -> Unit,
    onToggleFollowArtist: (String, String) -> Unit,
    onOpenArtist: (String, String) -> Unit,
    onOpenMenu: (Song) -> Unit,
    onAuthMessage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    username: String = "",
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var isAuthBusy by remember { mutableStateOf(false) }

    val librarySongs = remember(likedSongs, historySongs) {
        (likedSongs + historySongs).distinctBy { it.id }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val tabs = listOf(
        stringResource(R.string.tab_liked_songs),
        stringResource(R.string.tab_library),
        stringResource(R.string.tab_artist),
        stringResource(R.string.tab_history)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EvaBackground)
            .statusBarsPadding()
    ) {
        // Profile Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x33FF2D5F), Color(0x14FF7A45), EvaBackground)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar with first letter of display name
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(EvaSurface)
                            .border(2.5.dp, EvaAccentDiagonalGradient, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "L",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showEditNameDialog = true }
                                .testTag("me_edit_name_button")
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    color = EvaTextPrimary,
                                    fontSize = 21.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit display name",
                                tint = EvaTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                username.isNotBlank() -> "@$username"
                                else -> authUser?.email ?: "Local Profile"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Google Sign-In / Sign-Out pill
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(EvaSurfaceElevated)
                            .border(1.dp, EvaOutline, CircleShape)
                            .clickable(enabled = !isAuthBusy) {
                                isAuthBusy = true
                                if (authUser == null) {
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onAuthSuccess = {
                                            isAuthBusy = false
                                            onAuthMessage("Signed in with Google")
                                        },
                                        onAuthError = { msg ->
                                            isAuthBusy = false
                                            onAuthMessage(msg)
                                        },
                                        onAuthCancelled = { isAuthBusy = false }
                                    )
                                } else {
                                    signOutUser(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onSignOutComplete = {
                                            isAuthBusy = false
                                            onAuthMessage("Signed out")
                                        }
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("me_auth_button")
                    ) {
                        if (isAuthBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = EvaOrange
                            )
                        } else {
                            Text(
                                text = if (authUser == null) "Sign in" else stringResource(R.string.sign_out),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (authUser == null) EvaOrange else EvaTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Settings icon
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("me_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_open),
                            tint = EvaTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Stats row: Likes, Following, Played
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(EvaSurface)
                        .border(1.dp, EvaOutline, RoundedCornerShape(16.dp))
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatColumn(value = likedSongs.size.toString(), label = stringResource(R.string.likes_label))
                    StatColumn(value = followedCount.toString(), label = stringResource(R.string.following_label))
                    StatColumn(value = playedCount.toString(), label = stringResource(R.string.played_label))
                }

                // Monetag ad - profile ke niche
                MonetagAd(
                    zoneId = com.example.util.AppConstants.ZONE_PROFILE,
                    height = 90.dp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        // Tabs: Library | Artist | History
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTabIndex == index
                Column(
                    modifier = Modifier
                        .clickable { selectedTabIndex = index }
                        .padding(vertical = 10.dp)
                        .testTag("me_tab_$title"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = if (isSelected) EvaTextPrimary else EvaTextMuted,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) EvaAccentGradient
                                else androidx.compose.ui.graphics.SolidColor(Color.Transparent)
                            )
                    )
                }
            }
        }

        HorizontalDivider(color = EvaOutline)

        // Tab Content
        when (selectedTabIndex) {
            0 -> {
                // Liked Songs
                if (likedSongs.isEmpty()) {
                    MeEmptyState(
                        icon = Icons.Filled.FavoriteBorder,
                        message = stringResource(R.string.empty_library)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        item {
                            PlayShuffleActionRow(
                                onPlayClick = {
                                    onPlaySongs(likedSongs, 0, "Liked Songs")
                                },
                                onShuffleClick = {
                                    onShuffleSongs(likedSongs, "Liked Songs")
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                        itemsIndexed(likedSongs, key = { _, song -> "liked_${song.id}" }) { idx, song ->
                            SongListRow(
                                song = song,
                                isCurrentlyPlaying = song.id == currentSongId,
                                onClick = { onPlaySong(song) },
                                onMenuClick = { onOpenMenu(song) }
                            )
                        }
                    }
                }
            }

            1 -> {
                // Library (liked + recently played, sab ek jagah)
                if (librarySongs.isEmpty()) {
                    MeEmptyState(
                        icon = Icons.Filled.FavoriteBorder,
                        message = stringResource(R.string.empty_library)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        item {
                            PlayShuffleActionRow(
                                onPlayClick = { onPlaySongs(librarySongs, 0, "Library") },
                                onShuffleClick = { onShuffleSongs(librarySongs, "Library") },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                        itemsIndexed(librarySongs, key = { _, song -> "lib_${song.id}" }) { _, song ->
                            SongListRow(
                                song = song,
                                isCurrentlyPlaying = song.id == currentSongId,
                                onClick = { onPlaySong(song) },
                                onMenuClick = { onOpenMenu(song) }
                            )
                        }
                    }
                }
            }

            2 -> {
                // Artist (Followed + Liked songs artists)
                if (artists.isEmpty()) {
                    MeEmptyState(
                        icon = Icons.Filled.PersonOutline,
                        message = stringResource(R.string.empty_artists)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        items(artists, key = { it.name.lowercase() }) { artist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenArtist(artist.name, artist.imageUrl) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(EvaSurfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = artist.name.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
                                        style = MaterialTheme.typography.titleLarge.copy(color = EvaOrange)
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
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.titleMedium.copy(color = EvaTextPrimary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                FollowChip(
                                    isFollowed = artist.isFollowed,
                                    onClick = { onToggleFollowArtist(artist.name, artist.imageUrl) }
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                // History
                if (historySongs.isEmpty()) {
                    MeEmptyState(
                        icon = Icons.Filled.History,
                        message = stringResource(R.string.empty_history)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        item {
                            PlayShuffleActionRow(
                                onPlayClick = {
                                    onPlaySongs(historySongs, 0, "Recently Played")
                                },
                                onShuffleClick = {
                                    onShuffleSongs(historySongs, "Recently Played")
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                        itemsIndexed(historySongs, key = { _, song -> "hist_${song.id}" }) { idx, song ->
                            SongListRow(
                                song = song,
                                isCurrentlyPlaying = song.id == currentSongId,
                                onClick = { onPlaySong(song) },
                                onMenuClick = { onOpenMenu(song) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditNameDialog) {
        var draftName by remember(displayName) { mutableStateOf(displayName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            containerColor = EvaSurface,
            titleContentColor = EvaTextPrimary,
            textContentColor = EvaTextPrimary,
            title = { Text(stringResource(R.string.edit_name_title)) },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { if (it.length <= 40) draftName = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.edit_name_hint)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EvaPinkRed,
                        unfocusedBorderColor = EvaOutline,
                        focusedTextColor = EvaTextPrimary,
                        unfocusedTextColor = EvaTextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (draftName.isNotBlank()) {
                            onUpdateDisplayName(draftName)
                            showEditNameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EvaPinkRed)
                ) {
                    Text(stringResource(R.string.save), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text(stringResource(R.string.cancel), color = EvaTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                color = EvaTextPrimary,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(color = EvaTextSecondary)
        )
    }
}

@Composable
private fun MeEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EvaTextMuted,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextSecondary),
                textAlign = TextAlign.Center
            )
        }
    }
}

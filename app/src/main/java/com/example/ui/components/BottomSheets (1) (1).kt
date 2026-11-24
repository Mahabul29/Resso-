package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.firebase.onGoogleSignInClicked
import com.example.data.model.Song
import com.example.data.model.SongComment
import com.example.ui.theme.EvaAccentGradient
import com.example.ui.theme.EvaOrange
import com.example.ui.theme.EvaOutline
import com.example.ui.theme.EvaPinkRed
import com.example.ui.theme.EvaSurface
import com.example.ui.theme.EvaSurfaceElevated
import com.example.ui.theme.EvaTextMuted
import com.example.ui.theme.EvaTextPrimary
import com.example.ui.theme.EvaTextSecondary
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentIndex: Int,
    contextTitle: String,
    onSelectIndex: (Int) -> Unit,
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
                .heightIn(max = 520.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = EvaOrange
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.queue_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "$contextTitle • ${queue.size} songs",
                            style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary)
                        )
                    }
                }
            }
            HorizontalDivider(color = EvaOutline)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(queue, key = { index, song -> "${song.id}_$index" }) { index, song ->
                    val isCurrent = index == currentIndex
                    Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectIndex(index)
                                onDismiss()
                            }
                            .background(if (isCurrent) EvaSurfaceElevated else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SongArtworkThumb(
                            imageUrl = song.image,
                            contentDescription = song.title,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = if (isCurrent) EvaOrange else EvaTextPrimary,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Now playing",
                                tint = EvaPinkRed
                            )
                        }
                    }
                    // Monetag ad - har 8 songs ke baad
                    if ((index + 1) % com.example.util.AppConstants.UP_NEXT_AD_INTERVAL == 0) {
                        val zones = com.example.util.AppConstants.ZONES_UP_NEXT
                        val adNo = (index + 1) / com.example.util.AppConstants.UP_NEXT_AD_INTERVAL - 1
                        MonetagAd(
                            zoneId = zones[adNo % zones.size],
                            height = 90.dp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuBottomSheet(
    song: Song,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenLyrics: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onGoToArtist: () -> Unit,
    onShareApp: () -> Unit,
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
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongArtworkThumb(
                    imageUrl = song.image,
                    contentDescription = song.title,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artist} • ${song.album}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(color = EvaOutline)
            Spacer(modifier = Modifier.height(6.dp))

            MenuActionItem(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                iconTint = if (isLiked) EvaPinkRed else EvaTextPrimary,
                label = if (isLiked) stringResource(R.string.menu_unlike) else stringResource(R.string.menu_like),
                onClick = {
                    onToggleLike()
                    onDismiss()
                }
            )
            MenuActionItem(
                icon = Icons.Filled.ChatBubbleOutline,
                label = stringResource(R.string.menu_comments),
                onClick = {
                    onDismiss()
                    onOpenComments()
                }
            )
            MenuActionItem(
                icon = Icons.Filled.MusicNote,
                label = stringResource(R.string.menu_lyrics),
                onClick = {
                    onDismiss()
                    onOpenLyrics()
                }
            )
            MenuActionItem(
                icon = Icons.Filled.PlaylistPlay,
                label = stringResource(R.string.menu_play_next),
                onClick = {
                    onPlayNext()
                    onDismiss()
                }
            )
            MenuActionItem(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = stringResource(R.string.menu_add_queue),
                onClick = {
                    onAddToQueue()
                    onDismiss()
                }
            )
            MenuActionItem(
                icon = Icons.Filled.Person,
                label = stringResource(R.string.menu_go_artist),
                onClick = {
                    onDismiss()
                    onGoToArtist()
                }
            )
            MenuActionItem(
                icon = Icons.Filled.Share,
                label = stringResource(R.string.menu_share_app),
                onClick = {
                    onDismiss()
                    onShareApp()
                }
            )
        }
    }
}

@Composable
private fun MenuActionItem(
    icon: ImageVector,
    label: String,
    iconTint: Color = EvaTextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextPrimary)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    song: Song,
    commentsState: UiState<List<SongComment>>,
    isAuthenticated: Boolean,
    currentUid: String?,
    onAddComment: (String) -> Unit,
    onDeleteComment: (SongComment) -> Unit,
    onAuthMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commentText by remember { mutableStateOf("") }
    var isSigningIn by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

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
                .imePadding()
                .heightIn(min = 340.dp, max = 560.dp)
        ) {
            val count = (commentsState as? UiState.Success)?.data?.size ?: 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${stringResource(R.string.comments_title)} ($count)",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isAuthenticated) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EvaSurfaceElevated)
                        .border(1.dp, EvaOutline, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.comments_local_note),
                        style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            isSigningIn = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                scope = scope,
                                onAuthSuccess = {
                                    isSigningIn = false
                                    onAuthMessage("Signed in! Live comments synced.")
                                },
                                onAuthError = { msg ->
                                    isSigningIn = false
                                    onAuthMessage(msg)
                                },
                                onAuthCancelled = { isSigningIn = false }
                            )
                        },
                        enabled = !isSigningIn,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = EvaPinkRed),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("comments_google_sign_in_button")
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Sign in",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = EvaOutline)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (commentsState) {
                    is UiState.Loading -> {
                        CircularProgressIndicator(
                            color = EvaPinkRed,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is UiState.Error -> {
                        Text(
                            text = commentsState.message,
                            style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(20.dp)
                        )
                    }
                    is UiState.Success -> {
                        if (commentsState.data.isEmpty()) {
                            Text(
                                text = stringResource(R.string.empty_comments),
                                style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(commentsState.data, key = { it.id }) { comment ->
                                    val canDelete = comment.uid == currentUid || comment.uid == "local_user"
                                    CommentRow(
                                        comment = comment,
                                        canDelete = canDelete,
                                        onDelete = { onDeleteComment(comment) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = EvaOutline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { if (it.length <= 300) commentText = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.comment_hint),
                            style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextMuted)
                        )
                    },
                    supportingText = {
                        Text(
                            text = "${commentText.length}/300",
                            style = MaterialTheme.typography.labelSmall.copy(color = EvaTextMuted)
                        )
                    },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EvaPinkRed,
                        unfocusedBorderColor = EvaOutline,
                        focusedTextColor = EvaTextPrimary,
                        unfocusedTextColor = EvaTextPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("comment_input_field")
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            onAddComment(commentText)
                            commentText = ""
                        }
                    },
                    enabled = commentText.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (commentText.isNotBlank()) EvaAccentGradient
                            else androidx.compose.ui.graphics.SolidColor(EvaSurfaceElevated)
                        )
                        .testTag("send_comment_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send comment",
                        tint = if (commentText.isNotBlank()) Color.White else EvaTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: SongComment,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(EvaSurfaceElevated)
                .border(1.dp, EvaOutline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = comment.name.firstOrNull()?.uppercaseChar()?.toString() ?: "L",
                style = MaterialTheme.typography.labelLarge.copy(color = EvaOrange)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.name.ifBlank { "Listener" },
                    style = MaterialTheme.typography.labelLarge.copy(color = EvaTextSecondary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val timestampMillis = comment.createdAt?.toDate()?.time ?: comment.localCreatedAtMillis
                Text(
                    text = formatRelativeTime(timestampMillis),
                    style = MaterialTheme.typography.labelSmall.copy(color = EvaTextMuted)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextPrimary)
            )
        }
        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete comment",
                    tint = EvaTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatRelativeTime(millis: Long): String {
    if (millis <= 0L) return "Just now"
    val diffSec = ((System.currentTimeMillis() - millis) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60 -> "Just now"
        diffSec < 3600 -> "${diffSec / 60}m"
        diffSec < 86400 -> "${diffSec / 3600}h"
        else -> "${diffSec / 86400}d"
    }
}

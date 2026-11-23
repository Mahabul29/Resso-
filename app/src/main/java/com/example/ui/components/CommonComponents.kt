package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Song
import com.example.ui.theme.EvaAccentGradient
import com.example.ui.theme.EvaBackground
import com.example.ui.theme.EvaOrange
import com.example.ui.theme.EvaOutline
import com.example.ui.theme.EvaPinkRed
import com.example.ui.theme.EvaSurfaceElevated
import com.example.ui.theme.EvaTextMuted
import com.example.ui.theme.EvaTextPrimary
import com.example.ui.theme.EvaTextSecondary
import com.example.ui.theme.OutfitFontFamily
import com.example.util.formatDurationMs

@Composable
fun BlurredBackgroundImage(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(EvaBackground)) {
        if (imageUrl.isNotBlank()) {
            val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier
                    .fillMaxSize()
                    .blur(55.dp)
                    .alpha(0.55f)
            } else {
                Modifier
                    .fillMaxSize()
                    .alpha(0.28f)
            }
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = blurModifier
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x990E0C13),
                            Color(0x6617141F),
                            Color(0xDD0E0C13),
                            EvaBackground
                        )
                    )
                )
        )
    }
}

@Composable
fun OutlinedUppercaseTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    val cleanUpper = title.uppercase()
    Box(modifier = modifier) {
        Text(
            text = cleanUpper,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                lineHeight = 34.sp,
                letterSpacing = 1.sp,
                color = Color.White.copy(alpha = 0.72f),
                drawStyle = Stroke(
                    width = 3.2f,
                    join = StrokeJoin.Round
                )
            )
        )
    }
}

@Composable
fun SongArtworkThumb(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(EvaSurfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = EvaTextMuted,
            modifier = Modifier.size(22.dp)
        )
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun SongListRow(
    song: Song,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    rankNumber: Int? = null,
    isCurrentlyPlaying: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rankNumber != null) {
            val rankColor = when (rankNumber) {
                1 -> EvaPinkRed
                2 -> EvaOrange
                3 -> Color(0xFFFFA366)
                else -> EvaTextMuted
            }
            Text(
                text = String.format(java.util.Locale.US, "%02d", rankNumber),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = rankColor
                ),
                modifier = Modifier.width(32.dp)
            )
        }

        Box(modifier = Modifier.size(52.dp)) {
            SongArtworkThumb(
                imageUrl = song.image,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrentlyPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x990E0C13)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = EvaOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = if (isCurrentlyPlaying) EvaOrange else EvaTextPrimary,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val subtitle = buildString {
                append(song.artist)
                if (song.album.isNotBlank() && song.album != "Single") {
                    append(" • ")
                    append(song.album)
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (song.duration > 0) {
            Text(
                text = formatDurationMs(song.duration * 1000L),
                style = MaterialTheme.typography.labelSmall.copy(color = EvaTextMuted),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }

        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Song options for ${song.title}",
                tint = EvaTextSecondary
            )
        }
    }
}

@Composable
fun PlayShuffleActionRow(
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPlayClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .background(EvaAccentGradient, CircleShape)
                .testTag("play_all_button")
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.play_all).uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        OutlinedButton(
            onClick = onShuffleClick,
            shape = CircleShape,
            border = BorderStroke(1.dp, EvaOutline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = EvaSurfaceElevated,
                contentColor = EvaTextPrimary
            ),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("shuffle_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = null,
                tint = EvaTextPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.shuffle).uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = EvaTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
fun FollowChip(
    isFollowed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (isFollowed) {
                    Modifier
                        .background(EvaSurfaceElevated, shape)
                        .border(1.dp, EvaOutline, shape)
                } else {
                    Modifier
                        .background(Color.Transparent, shape)
                        .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isFollowed) stringResource(R.string.following) else stringResource(R.string.follow),
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (isFollowed) EvaOrange else Color.White,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

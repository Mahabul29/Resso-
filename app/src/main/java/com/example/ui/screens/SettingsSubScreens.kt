package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.example.data.model.EqSettings
import com.example.player.EqualizerManager
import com.example.ui.components.FollowChip
import com.example.ui.components.SettingsTopBar
import com.example.ui.theme.EvaBackground
import com.example.ui.theme.EvaOrange
import com.example.ui.theme.EvaOutline
import com.example.ui.theme.EvaPinkRed
import com.example.ui.theme.EvaSurface
import com.example.ui.theme.EvaSurfaceElevated
import com.example.ui.theme.EvaTextMuted
import com.example.ui.theme.EvaTextPrimary
import com.example.ui.theme.EvaTextSecondary
import com.example.ui.viewmodel.UiState
import com.example.util.AppConstants
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Music language
// ---------------------------------------------------------------------------------------------

@Composable
fun MusicLanguageScreen(
    initialSelection: Set<String>,
    onSave: (Set<String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember(initialSelection) { mutableStateOf(initialSelection) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EvaBackground)
    ) {
        SettingsTopBar(title = stringResource(R.string.music_language), onBack = onBack)

        Text(
            text = stringResource(R.string.music_language_hint),
            style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AppConstants.MUSIC_LANGUAGES, key = { it.key }) { language ->
                val isSelected = language.key in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(EvaSurface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) EvaPinkRed else EvaOutline,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            selected = if (isSelected) selected - language.key else selected + language.key
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("music_language_${language.key}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = language.key,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = EvaTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        if (language.native != language.key) {
                            Text(
                                text = language.native,
                                style = MaterialTheme.typography.bodySmall.copy(color = EvaTextSecondary)
                            )
                        }
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = EvaPinkRed
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                onSave(selected)
                onBack()
            },
            colors = ButtonDefaults.buttonColors(containerColor = EvaPinkRed),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .testTag("music_language_save")
        ) {
            Text(
                text = stringResource(R.string.save),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Add artist
// ---------------------------------------------------------------------------------------------

@Composable
fun AddArtistScreen(
    searchState: UiState<List<ArtistInfo>>,
    followedArtistNames: Set<String>,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggleFollow: (name: String, imageUrl: String) -> Unit,
    onOpenArtist: (name: String, imageUrl: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }

    // Wait for the typing to pause before searching.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            onClearSearch()
        } else {
            delay(400L)
            onSearch(query)
        }
    }
    DisposableEffect(Unit) {
        onDispose { onClearSearch() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EvaBackground)
    ) {
        SettingsTopBar(title = stringResource(R.string.add_artist), onBack = onBack)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.add_artist_hint),
                    style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextMuted)
                )
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = EvaTextSecondary)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = EvaTextSecondary)
                    }
                }
            },
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = EvaSurface,
                unfocusedContainerColor = EvaSurface,
                focusedBorderColor = EvaPinkRed,
                unfocusedBorderColor = EvaOutline,
                focusedTextColor = EvaTextPrimary,
                unfocusedTextColor = EvaTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("add_artist_search_field")
        )

        val artists = (searchState as? UiState.Success)?.data.orEmpty()
        when {
            query.isBlank() -> CenteredMessage(stringResource(R.string.add_artist_empty), Icons.Filled.PersonAdd)
            searchState is UiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EvaOrange)
            }
            searchState is UiState.Error -> CenteredMessage(searchState.message, Icons.Filled.Search)
            artists.isEmpty() -> CenteredMessage(stringResource(R.string.add_artist_none), Icons.Filled.Search)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 6.dp)
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
                            isFollowed = artist.name.lowercase() in followedArtistNames,
                            onClick = { onToggleFollow(artist.name, artist.imageUrl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
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

// ---------------------------------------------------------------------------------------------
// Equalizer
// ---------------------------------------------------------------------------------------------

@Composable
fun EqualizerScreen(
    settings: EqSettings,
    info: EqualizerManager.BandInfo,
    onUpdate: (settings: EqSettings, persist: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val levels = List(info.bandCount) { settings.levels.getOrElse(it) { 0 } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EvaBackground)
    ) {
        SettingsTopBar(title = stringResource(R.string.equalizer), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // On / off
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.equalizer_enable),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = EvaTextPrimary,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { on ->
                        onUpdate(settings.copy(enabled = on, levels = levels), true)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = EvaPinkRed,
                        uncheckedThumbColor = EvaTextSecondary,
                        uncheckedTrackColor = EvaSurfaceElevated,
                        uncheckedBorderColor = EvaOutline
                    ),
                    modifier = Modifier.testTag("equalizer_switch")
                )
            }

            if (!info.isAttached) {
                Text(
                    text = stringResource(R.string.equalizer_not_ready),
                    style = MaterialTheme.typography.bodySmall.copy(color = EvaTextMuted),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            // Presets
            Text(
                text = stringResource(R.string.equalizer_presets),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = EvaTextSecondary,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerManager.PRESETS.keys.forEach { preset ->
                    EqChip(
                        label = preset,
                        selected = settings.preset == preset,
                        onClick = {
                            onUpdate(
                                EqSettings(
                                    enabled = true,
                                    preset = preset,
                                    levels = EqualizerManager.levelsForPreset(preset)
                                ),
                                true
                            )
                        }
                    )
                }
                if (settings.preset == EqualizerManager.CUSTOM) {
                    EqChip(
                        label = stringResource(R.string.equalizer_custom),
                        selected = true,
                        onClick = {}
                    )
                }
            }

            // Bands
            Spacer(modifier = Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(EvaSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .alpha(if (settings.enabled) 1f else 0.5f)
            ) {
                levels.forEachIndexed { band, level ->
                    val freq = info.centerFreqsHz.getOrNull(band) ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatFrequency(freq),
                            style = MaterialTheme.typography.labelMedium.copy(color = EvaTextSecondary),
                            modifier = Modifier.width(64.dp)
                        )
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { newValue ->
                                val updated = levels.toMutableList().also { it[band] = newValue.toInt() }
                                onUpdate(
                                    settings.copy(
                                        enabled = true,
                                        preset = EqualizerManager.CUSTOM,
                                        levels = updated
                                    ),
                                    false
                                )
                            },
                            onValueChangeFinished = { onUpdate(EqualizerManager.settings.value, true) },
                            valueRange = info.minLevel.toFloat()..info.maxLevel.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = EvaOrange,
                                activeTrackColor = EvaPinkRed,
                                inactiveTrackColor = EvaSurfaceElevated
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("equalizer_band_$band")
                        )
                        Text(
                            text = formatDb(level),
                            style = MaterialTheme.typography.labelMedium.copy(color = EvaTextPrimary),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(52.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EqChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) EvaPinkRed else EvaSurface)
            .border(1.dp, if (selected) EvaPinkRed else EvaOutline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                color = if (selected) Color.White else EvaTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

private fun formatFrequency(hz: Int): String =
    if (hz >= 1000) String.format(java.util.Locale.US, "%.1f kHz", hz / 1000f).replace(".0 kHz", " kHz")
    else "$hz Hz"

private fun formatDb(millibels: Int): String {
    val db = millibels / 100f
    return String.format(java.util.Locale.US, "%+.1f dB", db)
}

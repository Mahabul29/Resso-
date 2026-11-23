package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Song
import com.example.ui.components.MonetagAd
import com.example.ui.components.SongListRow
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
import com.example.ui.viewmodel.UiState
import com.example.util.AppConstants

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(
    searchQuery: String,
    selectedCategory: String?,
    recentSearches: List<String>,
    trendingState: UiState<List<Song>>,
    searchResultsState: UiState<List<Song>>,
    currentSongId: String?,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onRecentSearchClick: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onPlaySongFromList: (songs: List<Song>, index: Int, contextTitle: String) -> Unit,
    onOpenMenu: (Song) -> Unit,
    onRetryTrending: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSearching = searchQuery.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EvaBackground)
            .statusBarsPadding()
    ) {
        // Header + Search Bar
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.nav_explore),
                style = MaterialTheme.typography.headlineLarge.copy(color = EvaTextPrimary)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextMuted)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = EvaTextSecondary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = onClearSearch,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = EvaTextSecondary
                            )
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
                    .testTag("explore_search_field")
            )
        }

        // Category Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(AppConstants.EXPLORE_CATEGORIES) { category ->
                val isSelected = selectedCategory == category && searchQuery.equals(category, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .then(
                            if (isSelected) {
                                Modifier.background(EvaAccentGradient, CircleShape)
                            } else {
                                Modifier
                                    .background(EvaSurfaceElevated, CircleShape)
                                    .border(1.dp, EvaOutline, CircleShape)
                            }
                        )
                        .clickable { onCategoryClick(category) }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                        .testTag("category_chip_$category")
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = if (isSelected) Color.White else EvaTextSecondary
                        )
                    )
                }
            }
        }

        // Body: either Search Results or Recent Searches + Numbered Trending List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (!isSearching && recentSearches.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.recent_searches),
                                style = MaterialTheme.typography.titleMedium.copy(color = EvaTextPrimary)
                            )
                            TextButton(onClick = onClearRecentSearches) {
                                Text(
                                    text = stringResource(R.string.clear),
                                    style = MaterialTheme.typography.labelLarge.copy(color = EvaOrange)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentSearches.forEach { query ->
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(EvaSurface)
                                        .border(1.dp, EvaOutline, CircleShape)
                                        .clickable { onRecentSearchClick(query) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.History,
                                        contentDescription = null,
                                        tint = EvaTextMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(
                                        text = query,
                                        style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Monetag ad - recent searches ke niche
            if (!isSearching) {
                item {
                    MonetagAd(
                        zoneId = com.example.util.AppConstants.ZONE_EXPLORE_RECENT,
                        height = 100.dp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (isSearching) {
                item {
                    Text(
                        text = "Results for \"${searchQuery.trim()}\"",
                        style = MaterialTheme.typography.titleLarge.copy(color = EvaTextPrimary),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                when (searchResultsState) {
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
                            Text(
                                text = searchResultsState.message,
                                style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextSecondary),
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                    is UiState.Success -> {
                        val results = searchResultsState.data
                        if (results.isEmpty()) {
                            item {
                                Text(
                                    text = "No matching tracks found.",
                                    style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextSecondary),
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        } else {
                            itemsIndexed(results, key = { idx, s -> "search_${s.id}_$idx" }) { idx, song ->
                                SongListRow(
                                    song = song,
                                    isCurrentlyPlaying = song.id == currentSongId,
                                    onClick = {
                                        onPlaySongFromList(
                                            results,
                                            idx,
                                            "Search: ${searchQuery.trim()}"
                                        )
                                    },
                                    onMenuClick = { onOpenMenu(song) }
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.trending_title),
                        style = MaterialTheme.typography.titleLarge.copy(color = EvaTextPrimary),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                when (trendingState) {
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
                                    text = trendingState.message,
                                    style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextSecondary)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onRetryTrending,
                                    colors = ButtonDefaults.buttonColors(containerColor = EvaPinkRed)
                                ) {
                                    Text(stringResource(R.string.retry), color = Color.White)
                                }
                            }
                        }
                    }
                    is UiState.Success -> {
                        val trending = trendingState.data
                        itemsIndexed(trending, key = { idx, s -> "trend_${s.id}_$idx" }) { idx, song ->
                            SongListRow(
                                song = song,
                                rankNumber = idx + 1,
                                isCurrentlyPlaying = song.id == currentSongId,
                                onClick = {
                                    onPlaySongFromList(
                                        trending,
                                        idx,
                                        "Trending Now"
                                    )
                                },
                                onMenuClick = { onOpenMenu(song) }
                            )
                        }
                    }
                }
            }
        }
    }
}

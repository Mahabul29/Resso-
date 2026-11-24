package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.SettingsCard
import com.example.ui.components.SettingsDivider
import com.example.ui.components.SettingsRow
import com.example.ui.components.SettingsSectionTitle
import com.example.ui.components.SettingsTopBar
import com.example.ui.theme.EvaAccentDiagonalGradient
import com.example.ui.theme.EvaBackground
import com.example.ui.theme.EvaOrange
import com.example.ui.theme.EvaOutline
import com.example.ui.theme.EvaPinkRed
import com.example.ui.theme.EvaSurface
import com.example.ui.theme.EvaTextMuted
import com.example.ui.theme.EvaTextPrimary
import com.example.ui.theme.EvaTextSecondary
import com.example.util.AppLanguage
import kotlinx.coroutines.delay

private val SLEEP_TIMER_MINUTES = listOf(5, 10, 15, 30, 45, 60)

@Composable
fun SettingsScreen(
    displayName: String,
    username: String,
    email: String?,
    musicLanguages: Set<String>,
    appLanguage: String,
    sleepTimerEndMs: Long?,
    onBack: () -> Unit,
    onUpdateProfile: (name: String, username: String) -> Unit,
    onOpenMusicLanguage: () -> Unit,
    onSetAppLanguage: (String) -> Unit,
    onOpenAddArtist: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onShareApp: () -> Unit,
    onJoinTelegram: () -> Unit,
    onDeleteAccount: (onFinished: (Boolean) -> Unit) -> Unit,
    versionName: String,
    modifier: Modifier = Modifier
) {
    var showEditProfile by remember { mutableStateOf(false) }
    var showAppLanguage by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // Re-read the clock every second only while a sleep timer is running.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepTimerEndMs) {
        while (sleepTimerEndMs != null) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EvaBackground)
    ) {
        SettingsTopBar(title = stringResource(R.string.settings_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // ---- Profile: avatar, name + username, then "Edit profile" right under them ----
            SettingsSectionTitle(stringResource(R.string.section_profile), Modifier.padding(top = 4.dp))
            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(EvaBackground)
                                .border(2.dp, EvaAccentDiagonalGradient, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "L",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = EvaTextPrimary,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (username.isNotBlank()) "@$username" else (email ?: ""),
                                style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { showEditProfile = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_edit_profile"),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.edit_profile),
                            color = EvaTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ---- Preferences ----
            SettingsSectionTitle(stringResource(R.string.section_preferences))
            SettingsCard {
                Column {
                    SettingsRow(
                        icon = Icons.Filled.LibraryMusic,
                        title = stringResource(R.string.music_language),
                        subtitle = if (musicLanguages.isEmpty()) {
                            stringResource(R.string.music_language_all)
                        } else {
                            musicLanguages.sorted().joinToString(", ")
                        },
                        onClick = onOpenMusicLanguage,
                        modifier = Modifier.testTag("settings_music_language")
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Translate,
                        title = stringResource(R.string.app_language),
                        subtitle = if (appLanguage == AppLanguage.HINDI) {
                            stringResource(R.string.language_hindi)
                        } else {
                            stringResource(R.string.language_english)
                        },
                        onClick = { showAppLanguage = true },
                        modifier = Modifier.testTag("settings_app_language")
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.PersonAdd,
                        title = stringResource(R.string.add_artist),
                        subtitle = stringResource(R.string.add_artist_subtitle),
                        onClick = onOpenAddArtist,
                        modifier = Modifier.testTag("settings_add_artist")
                    )
                }
            }

            // ---- Player ----
            SettingsSectionTitle(stringResource(R.string.section_player))
            SettingsCard {
                Column {
                    SettingsRow(
                        icon = Icons.Filled.GraphicEq,
                        title = stringResource(R.string.equalizer),
                        subtitle = stringResource(R.string.equalizer_subtitle),
                        onClick = onOpenEqualizer,
                        modifier = Modifier.testTag("settings_equalizer")
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Bedtime,
                        title = stringResource(R.string.sleep_timer),
                        subtitle = if (sleepTimerEndMs != null) {
                            stringResource(
                                R.string.sleep_timer_active,
                                formatTimerRemaining((sleepTimerEndMs - now).coerceAtLeast(0L))
                            )
                        } else {
                            stringResource(R.string.sleep_timer_off)
                        },
                        onClick = { showSleepTimer = true },
                        modifier = Modifier.testTag("settings_sleep_timer")
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Notifications,
                        title = stringResource(R.string.notifications),
                        subtitle = stringResource(R.string.notifications_subtitle),
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("settings_notifications")
                    )
                }
            }

            // ---- More ----
            SettingsSectionTitle(stringResource(R.string.section_more))
            SettingsCard {
                Column {
                    SettingsRow(
                        icon = Icons.Filled.Share,
                        title = stringResource(R.string.share_app),
                        subtitle = stringResource(R.string.share_app_subtitle),
                        onClick = onShareApp,
                        modifier = Modifier.testTag("settings_share_app")
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Send,
                        title = stringResource(R.string.join_telegram),
                        subtitle = stringResource(R.string.join_telegram_subtitle),
                        onClick = onJoinTelegram,
                        modifier = Modifier.testTag("settings_telegram")
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.ManageAccounts,
                        title = stringResource(R.string.account_settings),
                        subtitle = stringResource(R.string.account_settings_subtitle),
                        onClick = { showAccount = true },
                        modifier = Modifier.testTag("settings_account")
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.about_us),
                        onClick = { showAbout = true },
                        modifier = Modifier.testTag("settings_about")
                    )
                }
            }
        }
    }

    if (showEditProfile) {
        EditProfileDialog(
            initialName = displayName,
            initialUsername = username,
            onSave = { name, user ->
                onUpdateProfile(name, user)
                showEditProfile = false
            },
            onDismiss = { showEditProfile = false }
        )
    }

    if (showAppLanguage) {
        AppLanguageDialog(
            selected = appLanguage,
            onSelect = { code ->
                showAppLanguage = false
                if (code != appLanguage) onSetAppLanguage(code)
            },
            onDismiss = { showAppLanguage = false }
        )
    }

    if (showSleepTimer) {
        SleepTimerDialog(
            isRunning = sleepTimerEndMs != null,
            onPick = { minutes ->
                onStartSleepTimer(minutes)
                showSleepTimer = false
            },
            onCancelTimer = {
                onCancelSleepTimer()
                showSleepTimer = false
            },
            onDismiss = { showSleepTimer = false }
        )
    }

    if (showAccount) {
        AccountDialog(
            email = email,
            onDeleteAccount = onDeleteAccount,
            onDismiss = { showAccount = false }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = EvaSurface,
            titleContentColor = EvaTextPrimary,
            textContentColor = EvaTextSecondary,
            title = { Text(stringResource(R.string.about_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = stringResource(R.string.about_version, versionName),
                        style = MaterialTheme.typography.labelMedium.copy(color = EvaOrange)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.about_body),
                        style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    // Small diagnostic line: tells why the last ad did (not) appear.
                    Text(
                        text = "Ads:\n${com.example.ui.components.AdStatus.summary()}",
                        style = MaterialTheme.typography.labelSmall.copy(color = EvaTextMuted)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text(stringResource(R.string.close), color = EvaOrange)
                }
            }
        )
    }
}

private fun formatTimerRemaining(ms: Long): String {
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
}

@Composable
private fun EditProfileDialog(
    initialName: String,
    initialUsername: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var user by remember { mutableStateOf(initialUsername) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = EvaPinkRed,
        unfocusedBorderColor = EvaOutline,
        focusedTextColor = EvaTextPrimary,
        unfocusedTextColor = EvaTextPrimary,
        focusedLabelColor = EvaPinkRed,
        unfocusedLabelColor = EvaTextSecondary
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EvaSurface,
        titleContentColor = EvaTextPrimary,
        textContentColor = EvaTextPrimary,
        title = { Text(stringResource(R.string.edit_profile_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.name_label)) },
                    colors = fieldColors,
                    modifier = Modifier.testTag("edit_profile_name")
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { input ->
                        user = input.removePrefix("@").lowercase()
                            .filter { (it.isLetterOrDigit() && it.code < 128) || it == '_' || it == '.' }
                            .take(20)
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.username_label)) },
                    placeholder = { Text(stringResource(R.string.username_hint), color = EvaTextMuted) },
                    prefix = { Text("@", color = EvaTextSecondary) },
                    supportingText = {
                        Text(stringResource(R.string.username_helper), color = EvaTextMuted)
                    },
                    colors = fieldColors,
                    modifier = Modifier.testTag("edit_profile_username")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name, user) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EvaPinkRed)
            ) {
                Text(stringResource(R.string.save), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = EvaTextSecondary)
            }
        }
    )
}

@Composable
private fun AppLanguageDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        AppLanguage.ENGLISH to stringResource(R.string.language_english),
        AppLanguage.HINDI to stringResource(R.string.language_hindi)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EvaSurface,
        titleContentColor = EvaTextPrimary,
        title = { Text(stringResource(R.string.app_language)) },
        text = {
            Column {
                options.forEach { (code, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(code) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == code,
                            onClick = { onSelect(code) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = EvaPinkRed,
                                unselectedColor = EvaTextMuted
                            )
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextPrimary)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = EvaTextSecondary)
            }
        }
    )
}

@Composable
private fun SleepTimerDialog(
    isRunning: Boolean,
    onPick: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EvaSurface,
        titleContentColor = EvaTextPrimary,
        title = { Text(stringResource(R.string.sleep_timer)) },
        text = {
            Column {
                SLEEP_TIMER_MINUTES.forEach { minutes ->
                    Text(
                        text = if (minutes == 60) {
                            stringResource(R.string.sleep_timer_hour)
                        } else {
                            stringResource(R.string.sleep_timer_minutes, minutes)
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(color = EvaTextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(minutes) }
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
                if (isRunning) {
                    Text(
                        text = stringResource(R.string.sleep_timer_cancel),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = EvaPinkRed,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onCancelTimer)
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = EvaTextSecondary)
            }
        }
    )
}

@Composable
private fun AccountDialog(
    email: String?,
    onDeleteAccount: (onFinished: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var confirming by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    if (!confirming) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = EvaSurface,
            titleContentColor = EvaTextPrimary,
            title = { Text(stringResource(R.string.account_settings)) },
            text = {
                Column {
                    if (!email.isNullOrBlank()) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium.copy(color = EvaTextSecondary)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    OutlinedButton(
                        onClick = { confirming = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_delete_button"),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.delete_account),
                            color = EvaPinkRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close), color = EvaTextSecondary)
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { if (!working) confirming = false },
            containerColor = EvaSurface,
            titleContentColor = EvaTextPrimary,
            textContentColor = EvaTextSecondary,
            title = { Text(stringResource(R.string.delete_account)) },
            text = { Text(stringResource(R.string.delete_account_warning)) },
            confirmButton = {
                Button(
                    onClick = {
                        working = true
                        onDeleteAccount { success ->
                            working = false
                            if (success) onDismiss() else confirming = false
                        }
                    },
                    enabled = !working,
                    colors = ButtonDefaults.buttonColors(containerColor = EvaPinkRed),
                    modifier = Modifier.testTag("account_delete_confirm")
                ) {
                    if (working) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(stringResource(R.string.delete_account_confirm), color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }, enabled = !working) {
                    Text(stringResource(R.string.cancel), color = EvaTextSecondary)
                }
            }
        )
    }
}

package com.example

import android.app.Application
import com.example.data.firebase.FirestoreMusicRepository
import com.example.data.repository.MusicRepository
import com.example.player.EqualizerManager
import com.example.player.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EvaMusicApplication : Application() {

    lateinit var playerManager: PlayerManager
        private set

    lateinit var musicRepository: MusicRepository
        private set

    override fun onCreate() {
        super.onCreate()
        playerManager = PlayerManager(this)
        playerManager.connect()
        musicRepository = MusicRepository(
            context = this,
            firestoreRepo = FirestoreMusicRepository(this)
        )
        // Re-apply the equalizer the listener saved last time.
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            EqualizerManager.update(musicRepository.prefs.eqSettings.first())
        }
    }
}

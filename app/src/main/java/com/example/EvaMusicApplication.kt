package com.example

import android.app.Application
import com.example.data.firebase.FirestoreMusicRepository
import com.example.data.repository.MusicRepository
import com.example.player.PlayerManager

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
    }
}

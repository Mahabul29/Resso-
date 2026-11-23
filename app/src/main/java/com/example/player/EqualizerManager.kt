package com.example.player

import android.media.audiofx.Equalizer
import android.util.Log
import com.example.data.model.EqSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps android.media.audiofx.Equalizer. The player lives in PlaybackService, which runs in the
 * same process as the UI, so this singleton is the bridge: the service attaches the player's audio
 * session, the Settings screen changes the settings.
 *
 * Settings are remembered even while no player exists yet and are applied as soon as one attaches.
 */
object EqualizerManager {

    data class BandInfo(
        val bandCount: Int,
        val minLevel: Int,
        val maxLevel: Int,
        val centerFreqsHz: List<Int>,
        val isAttached: Boolean
    )

    /** Shown until a real equalizer is attached: a typical 5-band layout. */
    private val DEFAULT_INFO = BandInfo(
        bandCount = 5,
        minLevel = -1500,
        maxLevel = 1500,
        centerFreqsHz = listOf(60, 230, 910, 3600, 14000),
        isAttached = false
    )

    /** Preset gains in dB over 5 spread-out bands; mapped onto however many bands the device has. */
    val PRESETS: LinkedHashMap<String, List<Int>> = linkedMapOf(
        "Normal" to listOf(0, 0, 0, 0, 0),
        "Bass Boost" to listOf(7, 5, 1, 0, 0),
        "Treble Boost" to listOf(0, 0, 0, 4, 7),
        "Vocal" to listOf(-3, -1, 3, 3, 1),
        "Rock" to listOf(5, 3, -1, 3, 5),
        "Pop" to listOf(-1, 2, 4, 2, -1),
        "Jazz" to listOf(3, 2, -2, 2, 4),
        "Classical" to listOf(4, 3, -1, 3, 4)
    )

    const val CUSTOM = "Custom"

    private var equalizer: Equalizer? = null

    private val _info = MutableStateFlow(DEFAULT_INFO)
    val info: StateFlow<BandInfo> = _info.asStateFlow()

    private val _settings = MutableStateFlow(EqSettings())
    val settings: StateFlow<EqSettings> = _settings.asStateFlow()

    /** Called by PlaybackService when the player (or its audio session) is created. */
    @Synchronized
    fun attach(audioSessionId: Int) {
        releaseEffect()
        if (audioSessionId == 0) return
        try {
            val eq = Equalizer(0, audioSessionId)
            val range = eq.bandLevelRange
            val bands = eq.numberOfBands.toInt()
            _info.value = BandInfo(
                bandCount = bands,
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                centerFreqsHz = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 },
                isAttached = true
            )
            equalizer = eq
            applyToDevice()
        } catch (e: Exception) {
            Log.w("EqualizerManager", "Equalizer not available on this device: ${e.message}")
            equalizer = null
            _info.value = DEFAULT_INFO
        }
    }

    @Synchronized
    fun release() {
        releaseEffect()
        _info.value = DEFAULT_INFO
    }

    private fun releaseEffect() {
        try {
            equalizer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
    }

    /** Applies and remembers [newSettings] (the caller is responsible for saving to disk). */
    @Synchronized
    fun update(newSettings: EqSettings) {
        _settings.value = newSettings
        applyToDevice()
    }

    /** Levels in millibels for [preset], one per band of the current equalizer. */
    fun levelsForPreset(preset: String): List<Int> {
        val info = _info.value
        val gains = PRESETS[preset] ?: PRESETS.getValue("Normal")
        return (0 until info.bandCount).map { i ->
            val pos = if (info.bandCount == 1) 0f else i.toFloat() / (info.bandCount - 1) * (gains.size - 1)
            val lo = pos.toInt().coerceIn(0, gains.lastIndex)
            val hi = (lo + 1).coerceAtMost(gains.lastIndex)
            val gainDb = gains[lo] + (gains[hi] - gains[lo]) * (pos - lo)
            (gainDb * 100).toInt().coerceIn(info.minLevel, info.maxLevel)
        }
    }

    private fun applyToDevice() {
        val eq = equalizer ?: return
        val s = _settings.value
        val info = _info.value
        try {
            for (band in 0 until info.bandCount) {
                val level = s.levels.getOrElse(band) { 0 }.coerceIn(info.minLevel, info.maxLevel)
                eq.setBandLevel(band.toShort(), level.toShort())
            }
            eq.setEnabled(s.enabled)
        } catch (e: Exception) {
            Log.w("EqualizerManager", "Could not apply equalizer settings: ${e.message}")
        }
    }
}

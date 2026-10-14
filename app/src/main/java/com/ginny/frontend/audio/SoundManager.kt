package com.ginny.frontend.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import com.ginny.frontend.R
import com.ginny.frontend.data.MusicRepository
import com.ginny.frontend.data.SettingsRepository
import com.ginny.frontend.data.model.BundledMusicTrack
import com.ginny.frontend.data.model.MusicSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private fun BundledMusicTrack.resId(): Int = when (this) {
    BundledMusicTrack.BOSSA_NOVA -> R.raw.music_track_1
}

/**
 * Background music loop + short focus/selection SFX (see the MP3s under app/src/main/res/raw).
 * The music loop follows the user's chosen [MusicSelection] from Settings — the
 * bundled track, or a custom file from Interface/Background Music in the "Carpeta de Usuario" —
 * and swaps the underlying MediaPlayer whenever that choice (or the user folder's contents)
 * changes. Music and UI-sound volumes track Settings live.
 */
class SoundManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val musicRepository: MusicRepository,
    private val scope: CoroutineScope,
) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private var focusSoundId = 0
    private var launchSoundId = 0
    private var enterSystemSoundId = 0
    private var openModalSoundId = 0
    private var backSoundId = 0
    private var musicPlayer: MediaPlayer? = null
    private var currentMusicKey: String? = null

    private var uiVolume = 0.7f
    private var musicVolume = 0.5f
    private var started = false

    fun start() {
        if (started) return
        started = true

        focusSoundId = soundPool.load(context, R.raw.sfx_focus, 1)
        launchSoundId = soundPool.load(context, R.raw.sfx_launch, 1)
        enterSystemSoundId = soundPool.load(context, R.raw.sfx_enter_system, 1)
        openModalSoundId = soundPool.load(context, R.raw.sfx_open_modal, 1)
        backSoundId = soundPool.load(context, R.raw.sfx_back, 1)

        scope.launch {
            settingsRepository.uiSoundVolume.collect { uiVolume = it }
        }
        scope.launch {
            settingsRepository.musicVolume.collect { volume ->
                musicVolume = volume
                musicPlayer?.setVolume(volume, volume)
            }
        }

        scope.launch {
            combine(
                settingsRepository.userFolderUri,
                settingsRepository.backgroundMusicTrack,
            ) { userUri, stored -> userUri to stored }.collect { (userUri, stored) ->
                val customTracks = userUri?.let { musicRepository.listCustomTracks(Uri.parse(it)) }.orEmpty()
                playMusic(MusicSelection.decode(stored, customTracks))
            }
        }
    }

    private fun playMusic(selection: MusicSelection) {
        val key = MusicSelection.encode(selection)
        if (key == currentMusicKey && musicPlayer != null) return
        currentMusicKey = key

        musicPlayer?.release()
        musicPlayer = when (selection) {
            is MusicSelection.Bundled -> MediaPlayer.create(context, selection.track.resId())
            is MusicSelection.Custom -> MediaPlayer.create(context, selection.uri)
        }?.apply {
            isLooping = true
            setVolume(musicVolume, musicVolume)
            start()
        }
    }

    fun pauseMusic() {
        musicPlayer?.takeIf { it.isPlaying }?.pause()
    }

    fun resumeMusic() {
        musicPlayer?.start()
    }

    fun playFocusSound() {
        soundPool.play(focusSoundId, uiVolume, uiVolume, 1, 0, 1f)
    }

    fun playActivateSound() {
        soundPool.play(openModalSoundId, uiVolume, uiVolume, 1, 0, 1f)
    }

    fun playLaunchSound() {
        soundPool.play(launchSoundId, uiVolume, uiVolume, 1, 0, 1f)
    }

    fun playEnterSystemSound() {
        soundPool.play(enterSystemSoundId, uiVolume, uiVolume, 1, 0, 1f)
    }

    fun playOpenModalSound() {
        soundPool.play(openModalSoundId, uiVolume, uiVolume, 1, 0, 1f)
    }

    /** Al volver a la lista de sistemas o cerrar un modal (Kenney "minimize_008"). */
    fun playBackSound() {
        soundPool.play(backSoundId, uiVolume, uiVolume, 1, 0, 1f)
    }

    fun release() {
        musicPlayer?.release()
        musicPlayer = null
        soundPool.release()
        started = false
    }
}

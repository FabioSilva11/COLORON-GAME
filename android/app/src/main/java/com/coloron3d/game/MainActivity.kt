package com.coloron3d.game

import android.Manifest
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coloron3d.game.data.ReminderWorker
import com.coloron3d.game.engine.GameSurfaceView
import com.coloron3d.game.ui.*

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private var glView: GameSurfaceView? = null
    private var music: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        music = runCatching {
            assets.openFd("musica.mp3").use { fd ->
                MediaPlayer().apply {
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    isLooping = true
                    setVolume(0.5f, 0.5f)
                    prepare()
                }
            }
        }.getOrNull()

        setContent {
            val progress by vm.progress.collectAsStateWithLifecycle()
            val snap by vm.snapshot.collectAsStateWithLifecycle()
            val screen by vm.screen.collectAsStateWithLifecycle()
            val toast by vm.toast.collectAsStateWithLifecycle()

            val view = remember {
                GameSurfaceView(this) { s -> runOnUiThread { vm.onSnapshot(s) } }.also { glView = it }
            }
            LaunchedEffect(screen) {
                if (screen == Screen.Playing) view.startGame(progress.selectedSkin)
            }

            Box(Modifier.fillMaxSize()) {
                AndroidView({ view }, Modifier.fillMaxSize())
                when (screen) {
                    Screen.Menu -> MenuScreen(progress, vm)
                    Screen.Playing -> HudScreen(snap) { view.selectColor(it) }
                    Screen.Over -> GameOverScreen(snap, progress, vm.lastEarned, vm.newRecord, vm)
                    Screen.Shop -> ShopScreen(progress, vm)
                }
                RewardToast(toast) { vm.dismissToast() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        glView?.onResume()
        music?.start()
    }

    override fun onPause() {
        glView?.onPause()
        music?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        music?.release()
        super.onDestroy()
    }
}

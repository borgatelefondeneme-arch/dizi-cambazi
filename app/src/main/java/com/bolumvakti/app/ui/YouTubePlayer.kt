package com.bolumvakti.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/** Inline YouTube player. Calls onFail when the video cannot be played inside the app (embedding disabled etc.). */
@Composable
fun InlineYouTube(videoId: String, modifier: Modifier = Modifier, onFail: (String) -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember(videoId) {
        YouTubePlayerView(ctx).apply {
            enableAutomaticInitialization = false
            var fsView: View? = null
            addFullscreenListener(object : FullscreenListener {
                override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                    val act = ctx as? Activity ?: return
                    fsView = fullscreenView
                    (act.window.decorView as ViewGroup).addView(fullscreenView, FrameLayout.LayoutParams(-1, -1))
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    WindowInsetsControllerCompat(act.window, act.window.decorView).apply {
                        hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
                override fun onExitFullscreen() {
                    val act = ctx as? Activity ?: return
                    fsView?.let { (act.window.decorView as ViewGroup).removeView(it) }; fsView = null
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    WindowInsetsControllerCompat(act.window, act.window.decorView).show(WindowInsetsCompat.Type.systemBars())
                }
            })
            val opts = IFramePlayerOptions.Builder().controls(1).fullscreen(1).rel(0).ivLoadPolicy(3).ccLoadPolicy(0).build()
            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) { youTubePlayer.loadVideo(videoId, 0f) }
                override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) { onFail(error.name) }
            }, true, opts)
        }
    }
    DisposableEffect(view) {
        lifecycle.addObserver(view)
        onDispose { lifecycle.removeObserver(view); view.release() }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

@Suppress("unused") private fun keep(a: Activity) = WindowCompat.getInsetsController(a.window, a.window.decorView)

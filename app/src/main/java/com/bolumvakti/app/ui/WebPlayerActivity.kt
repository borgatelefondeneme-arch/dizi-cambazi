package com.bolumvakti.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Plays a channel-website episode inside the app. If the page fails, it hands over to the browser. */
class WebPlayerActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var custom: View? = null
    private var handedOff = false
    private val url by lazy { intent.getStringExtra("url") ?: "" }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.parseColor("#21142F")); gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 24, 24, 24)
        }
        fun btn(t: String, onClick: () -> Unit) = TextView(this).apply {
            text = t; setTextColor(Color.WHITE); textSize = 15f; setPadding(24, 12, 24, 12); setOnClickListener { onClick() }
        }
        bar.addView(btn("‹ Geri") { finish() })
        bar.addView(TextView(this).apply {
            text = intent.getStringExtra("title") ?: ""; setTextColor(Color.parseColor("#B9A6CB")); textSize = 13f
            maxLines = 1; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        bar.addView(btn("Tarayıcıda aç") { handOff(false) })
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true }
        web = WebView(this)
        col.addView(bar); col.addView(progress, LinearLayout.LayoutParams(-1, 8))
        col.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(col); setContentView(root)
        ViewCompatInsets.pad(bar)

        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true; useWideViewPort = true; mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url.toString()
                return !(u.startsWith("http://") || u.startsWith("https://")) // block app-scheme redirects, stay inside
            }
            override fun onPageFinished(view: WebView?, u: String?) { progress.visibility = View.GONE }
            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) handOff(true)
            }
            override fun onReceivedHttpError(view: WebView, req: WebResourceRequest, resp: WebResourceResponse) {
                if (req.isForMainFrame && resp.statusCode >= 400) handOff(true)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                custom = view; root.addView(view, FrameLayout.LayoutParams(-1, -1))
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE; immersive(true)
            }
            override fun onHideCustomView() {
                custom?.let { root.removeView(it) }; custom = null
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; immersive(false)
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when { custom != null -> web.webChromeClient?.onHideCustomView(); web.canGoBack() -> web.goBack(); else -> finish() }
            }
        })
        web.loadUrl(url)
    }

    private fun immersive(on: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (on) { c.hide(WindowInsetsCompat.Type.systemBars()); c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        else c.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun handOff(failed: Boolean) {
        if (handedOff) return; handedOff = true
        if (failed) Toast.makeText(this, "Bölüm burada açılamadı, kanalın sitesinde açıyorum", Toast.LENGTH_LONG).show()
        openExternal(this, url); finish()
    }

    override fun onPause() { super.onPause(); web.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onDestroy() { web.destroy(); super.onDestroy() }

    companion object {
        fun start(ctx: Context, url: String, title: String) =
            ctx.startActivity(Intent(ctx, WebPlayerActivity::class.java).putExtra("url", url).putExtra("title", title))
        fun openExternal(ctx: Context, url: String) {
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { Toast.makeText(ctx, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show() }
        }
    }
}

object ViewCompatInsets {
    fun pad(v: View) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(v) { view, ins ->
            val top = ins.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, 24 + top, view.paddingRight, view.paddingBottom); ins
        }
    }
}

package com.pedro.idlekeeper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * WebView que NÃO se pausa quando o app vai para segundo plano.
 * O truque está em KeepAliveWebView: ele ignora a mudança de visibilidade da janela,
 * então o Chromium continua achando que a página está visível e não congela o jogo.
 */
class KeepAliveWebView(context: Context) : WebView(context) {
    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != View.GONE) super.onWindowVisibilityChanged(View.VISIBLE)
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var webView: KeepAliveWebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = KeepAliveWebView(this)
        setContentView(FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(-1, -1))
        })

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean = false
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else moveTaskToBack(true)
            }
        })

        if (savedInstanceState == null) webView.loadUrl(GAME_URL)
        else webView.restoreState(savedInstanceState)

        askNotificationPermission()
        startKeepAlive()
        askIgnoreBatteryOptimizations()
    }

    private fun startKeepAlive() {
        ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
    }

    @SuppressLint("BatteryLife")
    private fun askIgnoreBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
    }

    // Importante: NÃO chamamos webView.onPause() aqui, para o jogo continuar rodando.
    override fun onPause() { super.onPause(); webView.resumeTimers() }
    override fun onStop() { super.onStop(); webView.resumeTimers() }
    override fun onResume() { super.onResume(); webView.onResume(); webView.resumeTimers() }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        // Só destrói a WebView se o usuário realmente fechou o app
        if (isFinishing) webView.destroy()
        super.onDestroy()
    }

    companion object { const val GAME_URL = "https://pokeidle.io/app" }
}

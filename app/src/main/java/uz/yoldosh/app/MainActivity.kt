package uz.yoldosh.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var bridge: Bridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WebView.setWebContentsDebuggingEnabled(true)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        web.setBackgroundColor(0xFF0B1220.toInt())
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = false
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
        }

        bridge = Bridge(this)
        web.addJavascriptInterface(bridge, "Android")
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
        handleIntent(intent)
    }

    fun emit(event: String, data: Any) {
        val payload = when (data) {
            is JSONObject -> data.toString()
            is String -> JSONObject.quote(data)
            else -> data.toString()
        }
        runOnUiThread {
            web.evaluateJavascript("window.onNative && window.onNative(${JSONObject.quote(event)}, $payload)", null)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.getBooleanExtra("listen", false) == true) {
            i.removeExtra("listen")
            bridge.requestListenFromOutside()
        }
    }

    override fun onStart() {
        super.onStart()
        stopService(Intent(this, BubbleService::class.java))
    }

    override fun onResume() {
        super.onResume()
        bridge.onResume()
    }

    override fun onStop() {
        super.onStop()
        bridge.onStop()
        if (!isFinishing && Settings.canDrawOverlays(this) && Prefs.bool(this, "bubble", true)) {
            try { startForegroundService(Intent(this, BubbleService::class.java)) } catch (_: Exception) {}
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("window.onBack && window.onBack()", null)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        bridge.pushPerms()
    }

    override fun onDestroy() {
        bridge.release()
        web.destroy()
        super.onDestroy()
    }
}

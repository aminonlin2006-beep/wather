package com.amin.weather

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bridge: WeatherBridge
    private lateinit var btnRefresh: Button
    private lateinit var progress: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        bridge = WeatherBridge(this)
        btnRefresh = findViewById(R.id.btnRefresh)
        progress = findViewById(R.id.progress)

        btnRefresh.setOnClickListener { performRefresh() }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // فقط URLهای داخلی (assets) در WebView باز می‌شوند.
                return url?.startsWith("file:///android_asset/") == false
            }
        }
        webView.webChromeClient = WebChromeClient()

        // پل به‌روزرسانی زنده از اندروید به جاوااسکریپت
        webView.addJavascriptInterface(bridge, "AndroidWeather")

        webView.loadUrl("file:///android_asset/dashboard/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * اجرای به‌روزرسانی زنده: محاسبات در Kotlin اجرا می‌شود، نتیجه در
     * localStorage ذخیره و صفحه دوباره بارگذاری می‌شود.
     */
    private fun performRefresh() {
        btnRefresh.isEnabled = false
        progress.visibility = View.VISIBLE
        bridge.refreshData { json ->
            runOnUiThread {
                val quoted = JSONObject.quote(json)
                webView.evaluateJavascript(
                    "localStorage.setItem('fc_data', $quoted);",
                    null
                )
                webView.evaluateJavascript("location.reload();", null)
                progress.visibility = View.GONE
                btnRefresh.isEnabled = true
            }
        }
    }
}

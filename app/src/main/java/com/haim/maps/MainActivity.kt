package com.haim.maps

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.net.HttpURLConnection
import java.net.URL

/** Google Maps WebView with the original floating toolbar behavior restored. */
class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private lateinit var floatingBar: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val hideBarRunnable = Runnable { hideFloatingBar() }

    private var isDesktopMode = false
    private var startY = 0f
    private var startX = 0f
    private var isSwipingDown = false
    private lateinit var mobileUserAgent: String

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mobileUserAgent = WebSettings.getDefaultUserAgent(this)
        isDesktopMode = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).getBoolean(KEY_DESKTOP_MODE, false)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowContentAccess = true
                allowFileAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else mobileUserAgent
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                    interceptDesktopMapsRequest(request) ?: super.shouldInterceptRequest(view, request)

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (url != null && !BrowserConfig.isUrlAllowed(url)) {
                        view?.stopLoading()
                        showBlockedPage(url)
                        return
                    }
                    view?.evaluateJavascript(buildDesktopOverrideScript(isDesktopMode), null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(buildDesktopOverrideScript(isDesktopMode), null)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    val destination = uri.toString()
                    if (destination.startsWith("http://") || destination.startsWith("https://")) {
                        if (!BrowserConfig.isUrlAllowed(destination)) {
                            showBlockedPage(destination)
                            return true
                        }
                        return false
                    }
                    return !BrowserConfig.isUrlAllowed(destination)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress >= 15) view?.evaluateJavascript(buildDesktopOverrideScript(isDesktopMode), null)
                }
            }

            setOnTouchListener { view, event ->
                val target = view as? WebView
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.y
                        startX = event.x
                        isSwipingDown = true
                        hideFloatingBar()
                    }
                    MotionEvent.ACTION_MOVE -> if (isSwipingDown && target != null) {
                        val deltaY = event.y - startY
                        val deltaX = event.x - startX
                        val isAtTop = !target.canScrollVertically(-1) || target.scrollY <= 2
                        if (deltaY > 120f && kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) * 1.5f && isAtTop) {
                            showFloatingBar()
                            isSwipingDown = false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isSwipingDown = false
                }
                false
            }
        }

        root = FrameLayout(this)
        root.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        createFloatingBar()
        setContentView(root)

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else loadUrlWithHeaders(MAPS_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (floatingBar.visibility == View.VISIBLE) hideFloatingBar()
                else if (webView.canGoBack()) webView.goBack()
                else finish()
            }
        })
    }

    /** Same desktop/mobile client-hint headers as the original BrowserScreen. */
    private fun loadUrlWithHeaders(url: String) {
        val headers = HashMap<String, String>()
        if (isDesktopMode) {
            headers["Sec-CH-UA-Mobile"] = "?0"
            headers["Sec-CH-UA-Platform"] = "\"Windows\""
        } else {
            headers["Sec-CH-UA-Mobile"] = "?1"
            headers["Sec-CH-UA-Platform"] = "\"Android\""
        }
        webView.loadUrl(url, headers)
    }

    /** Original desktop-mode Maps request handling, including cookies and response headers. */
    private fun interceptDesktopMapsRequest(request: WebResourceRequest?): WebResourceResponse? {
        if (!isDesktopMode || request == null || request.method != "GET") return null
        val requestUrl = request.url.toString()
        val isMapsRequest = requestUrl.contains("google.com/maps") ||
            requestUrl.contains("google.co.il/maps") || requestUrl.contains("maps.google.")
        if (!isMapsRequest) return null

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                request.requestHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
                setRequestProperty("Sec-CH-UA-Mobile", "?0")
                setRequestProperty("Sec-CH-UA-Platform", "\"Windows\"")
                setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
                CookieManager.getInstance().getCookie(requestUrl)?.let { setRequestProperty("Cookie", it) }
            }

            val responseCode = connection.responseCode
            val mimeType = (connection.contentType ?: "text/html").substringBefore(";")
            val encoding = connection.contentEncoding ?: "UTF-8"
            val responseHeaders = mutableMapOf<String, String>()
            connection.headerFields.forEach { (key, values) ->
                if (key != null && !values.isNullOrEmpty()) responseHeaders[key] = values.joinToString(",")
            }
            connection.headerFields["Set-Cookie"]?.forEach {
                CookieManager.getInstance().setCookie(requestUrl, it)
            }
            val stream = if (responseCode >= 400) connection.errorStream else connection.inputStream
            WebResourceResponse(mimeType, encoding, responseCode,
                connection.responseMessage ?: "OK", responseHeaders, stream)
        } catch (error: Exception) {
            error.printStackTrace()
            try { connection?.disconnect() } catch (_: Exception) { }
            null
        }
    }

    /** The original JavaScript device/viewport overrides used by the toolbar's desktop toggle. */
    private fun buildDesktopOverrideScript(desktop: Boolean): String = """
        (function() {
            var meta = document.querySelector('meta[name="viewport"]');
            if (!meta) {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                document.getElementsByTagName('head')[0].appendChild(meta);
            }
            if ($desktop) {
                meta.setAttribute('content', 'width=1366, initial-scale=0.25, maximum-scale=5.0, user-scalable=yes');
            } else {
                meta.setAttribute('content', 'width=device-width, user-scalable=yes, initial-scale=1.0');
            }
            if (navigator.userAgentData) {
                Object.defineProperty(navigator.userAgentData, 'mobile', { get: () => ${!desktop} });
                if ($desktop) Object.defineProperty(navigator.userAgentData, 'platform', { get: () => 'Windows' });
            }
            Object.defineProperty(navigator, 'maxTouchPoints', { get: () => ${if (desktop) 0 else 5} });
            if ($desktop) {
                Object.defineProperty(window.screen, 'width', { get: () => 1366 });
                Object.defineProperty(window.screen, 'availWidth', { get: () => 1366 });
                Object.defineProperty(window.screen, 'height', { get: () => 768 });
                Object.defineProperty(window.screen, 'availHeight', { get: () => 768 });
                Object.defineProperty(window, 'devicePixelRatio', { get: () => 1 });
                if (!window.__originalMatchMedia) {
                    window.__originalMatchMedia = window.matchMedia;
                    window.matchMedia = function(query) {
                        if (typeof query === 'string' && query.includes('(pointer: coarse)'))
                            return { matches: false, media: query, onchange: null, addListener: () => {}, removeListener: () => {}, addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false };
                        if (typeof query === 'string' && query.includes('(pointer: fine)'))
                            return { matches: true, media: query, onchange: null, addListener: () => {}, removeListener: () => {}, addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false };
                        return window.__originalMatchMedia(query);
                    };
                }
            }
        })();
    """.trimIndent()

    private fun createFloatingBar() {
        floatingBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(24).toFloat()
            }
            elevation = dp(8).toFloat()
            visibility = View.GONE
            alpha = 0f
            contentDescription = "סרגל כלים"
        }

        addTool("‹", "הדף הקודם") { if (webView.canGoBack()) webView.goBack() }
        addTool("›", "הדף הבא") { if (webView.canGoForward()) webView.goForward() }
        addTool("↻", "רענון") { webView.reload(); hideFloatingBar() }
        addTool(if (isDesktopMode) "▤" else "▣", if (isDesktopMode) "תצוגת מחשב פעילה — מעבר לתצוגת נייד" else "הפעלת תצוגת מחשב") { toggleDesktopMode() }
        addDivider()
        addTool("◉", "כניסה ובחירת חשבון לגוגל מפות") {
            hideFloatingBar()
            loadUrlWithHeaders(ACCOUNT_CHOOSER_URL)
        }
        addTool("×", "סגור סרגל") { hideFloatingBar() }

        val statusBarHeight = resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }?.let { resources.getDimensionPixelSize(it) } ?: dp(24)
        root.addView(floatingBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = statusBarHeight + dp(16)
            leftMargin = dp(16)
            rightMargin = dp(16)
        })
    }

    private fun addTool(symbol: String, description: String, action: () -> Unit) {
        val button = TextView(this).apply {
            text = symbol
            textSize = 25f
            setTextColor(Color.rgb(45, 45, 45))
            gravity = Gravity.CENTER
            contentDescription = description
            isClickable = true
            isFocusable = true
            background = selectableItemBackground()
            setOnClickListener { action() }
        }
        floatingBar.addView(button, LinearLayout.LayoutParams(dp(48), dp(48)))
    }

    private fun addDivider() {
        val divider = View(this).apply { setBackgroundColor(Color.rgb(220, 220, 220)) }
        floatingBar.addView(divider, LinearLayout.LayoutParams(dp(1), dp(24)).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        })
    }

    private fun selectableItemBackground(): android.graphics.drawable.Drawable? {
        val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
        val typedArray = obtainStyledAttributes(attrs)
        val drawable = typedArray.getDrawable(0)
        typedArray.recycle()
        return drawable
    }

    /** Original desktop toggle: change UA, reload with client hints, then apply JS overrides. */
    private fun toggleDesktopMode() {
        isDesktopMode = !isDesktopMode
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit().putBoolean(KEY_DESKTOP_MODE, isDesktopMode).apply()
        webView.settings.userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else mobileUserAgent
        webView.url?.let { loadUrlWithHeaders(it) } ?: webView.reload()

        val button = floatingBar.getChildAt(3) as? TextView
        button?.text = if (isDesktopMode) "▤" else "▣"
        button?.contentDescription = if (isDesktopMode) "מעבר לתצוגת נייד" else "מעבר לתצוגת מחשב"
        hideFloatingBar()
    }

    private fun showBlockedPage(blockedUrl: String) {
        val safeUrl = TextUtils.htmlEncode(blockedUrl)
        val html = "<!doctype html><html lang=\"he\" dir=\"rtl\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>האתר חסום</title></head><body style=\"font-family:sans-serif;background:#f5f5f5;color:#222;padding:28px;text-align:center\"><h2>האתר הזה אינו מורשה באפליקציה</h2><p>הכתובת שנחסמה:</p><p style=\"direction:ltr;overflow-wrap:anywhere\">$safeUrl</p><button onclick=\"history.back()\" style=\"padding:12px 24px;font-size:16px\">חזרה</button></body></html>"
        webView.stopLoading()
        webView.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
    }

    private fun showFloatingBar() {
        handler.removeCallbacks(hideBarRunnable)
        if (floatingBar.visibility != View.VISIBLE) {
            floatingBar.visibility = View.VISIBLE
            floatingBar.translationY = -dp(64).toFloat()
            floatingBar.alpha = 0f
            floatingBar.animate().translationY(0f).alpha(1f).setDuration(220).start()
        }
        floatingBar.bringToFront()
        handler.postDelayed(hideBarRunnable, BAR_TIMEOUT_MS)
    }

    private fun hideFloatingBar() {
        handler.removeCallbacks(hideBarRunnable)
        if (!::floatingBar.isInitialized || floatingBar.visibility != View.VISIBLE) return
        floatingBar.animate().cancel()
        floatingBar.animate().translationY(-dp(64).toFloat()).alpha(0f)
            .setDuration(180)
            .withEndAction { floatingBar.visibility = View.GONE }
            .start()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideBarRunnable)
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val MAPS_URL = "https://www.google.com/maps"
        private const val ACCOUNT_CHOOSER_URL =
            "https://accounts.google.com/AccountChooser?continue=https://www.google.com/maps"
        private const val BAR_TIMEOUT_MS = 6_000L
        private const val PREFERENCES_NAME = "maps_app_preferences"
        private const val KEY_DESKTOP_MODE = "desktop_mode_enabled"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}

package com.haim.maps

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/** Focused Google Maps WebView with a small, swipe-revealed floating toolbar. */
class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private lateinit var floatingBar: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var desktopMode = false
    private var startY = 0f

    private val hideBarRunnable = Runnable { floatingBar.visibility = View.GONE }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)
            settings.mediaPlaybackRequiresUserGesture = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
            }
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        hideFloatingBar()
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaY = event.rawY - startY
                        if (deltaY > dp(120) && kotlin.math.abs(deltaY) > kotlin.math.abs(event.rawX) * 0.0f) {
                            if (!canScrollVertically(-1) || scrollY <= 2) showFloatingBar()
                        }
                    }
                }
                false
            }
            loadUrl(MAPS_URL)
        }

        root = FrameLayout(this)
        root.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        createFloatingBar()
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (floatingBar.visibility == View.VISIBLE) {
                    hideFloatingBar()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun createFloatingBar() {
        floatingBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(4), dp(5), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.rgb(225, 225, 225))
            }
            elevation = dp(8).toFloat()
            visibility = View.GONE
            contentDescription = "סרגל כלים"
        }

        addTool("←", "חזרה") { if (webView.canGoBack()) webView.goBack() }
        addTool("→", "קדימה") { if (webView.canGoForward()) webView.goForward() }
        addTool("↻", "רענון") { webView.reload() }
        addTool(if (desktopMode) "▣" else "▤", "מצב מחשב או נייד") { toggleDesktopMode() }
        addTool("◎", "בחירת חשבון") {
            webView.loadUrl(ACCOUNT_CHOOSER_URL)
        }
        addTool("×", "סגירה") { hideFloatingBar() }

        val topInset = resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }?.let { resources.getDimensionPixelSize(it) } ?: dp(24)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = topInset + dp(12)
            leftMargin = dp(8)
            rightMargin = dp(8)
        }
        root.addView(floatingBar, params)
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
            setOnClickListener { action(); if (description != "סגירה") showFloatingBar() }
        }
        floatingBar.addView(button, LinearLayout.LayoutParams(dp(46), dp(46)))
    }

    private fun selectableItemBackground(): android.graphics.drawable.Drawable? {
        val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
        val typedArray = obtainStyledAttributes(attrs)
        val drawable = typedArray.getDrawable(0)
        typedArray.recycle()
        return drawable
    }

    private fun toggleDesktopMode() {
        desktopMode = !desktopMode
        webView.settings.userAgentString = if (desktopMode) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        } else {
            WebView(this).settings.userAgentString
        }
        webView.settings.useWideViewPort = desktopMode
        webView.settings.loadWithOverviewMode = desktopMode
        webView.reload()
        floatingBar.getChildAt(3).contentDescription =
            if (desktopMode) "חזרה למצב נייד" else "מעבר למצב מחשב"
        (floatingBar.getChildAt(3) as TextView).text = if (desktopMode) "▤" else "▣"
        showFloatingBar()
    }

    private fun showFloatingBar() {
        handler.removeCallbacks(hideBarRunnable)
        floatingBar.visibility = View.VISIBLE
        floatingBar.bringToFront()
        handler.postDelayed(hideBarRunnable, BAR_TIMEOUT_MS)
    }

    private fun hideFloatingBar() {
        handler.removeCallbacks(hideBarRunnable)
        if (::floatingBar.isInitialized) floatingBar.visibility = View.GONE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
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
    }
}

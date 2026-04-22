package com.tuapp.touchgrass.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.tuapp.touchgrass.R
import com.tuapp.touchgrass.storage.SettingsStore
import android.content.Intent
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var fabWrapper: FrameLayout
    private lateinit var fabBubble: LinearLayout
    private lateinit var fabText: TextView
    private lateinit var fabLogo: ImageView
    private lateinit var touchOverlay: View
    private lateinit var dmOverlay: FrameLayout
    private lateinit var settingsStore: SettingsStore

    private var isOnReels = false
    private var isFabExpanded = false


    private var lastDirectThreadUrl = ""   // Para /direct/t/
    private var lastSearchOrProfileUrl = "" // Para perfiles o resultados de búsqueda
    private var currentUrl = ""

    private val FAB_MARGIN_TOP_DP = 80
    private val FAB_MARGIN_BOTTOM_DP = 80

    private val handler = Handler(Looper.getMainLooper())
    private val fabHandler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable { if (isFabExpanded) collapseFab() }

    private val uiRunnable = object : Runnable {
        override fun run() {
            if (isFabExpanded) updateFabText()
            checkAndApplyReelsLimit()
            handler.postDelayed(this, 1000)
        }
    }

    // ── Swipe DMs ──────────────────────────────────────────────
    private var lastSwipeTime = Long.MIN_VALUE
    private val swipeCooldownMs = 2500L
    private var isSwipeLocked = false
    private var touchStartY = 0f

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instagram)

        settingsStore = SettingsStore(this)
        settingsStore.resetIfNewDay()

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        fabWrapper = findViewById(R.id.fabWrapper)
        fabBubble = findViewById(R.id.fabBubble)
        fabText = findViewById(R.id.fabText)
        fabLogo = findViewById(R.id.fabLogo)
        touchOverlay = findViewById(R.id.touchOverlay)
        dmOverlay = findViewById(R.id.dmOverlay)

        fabBubble.visibility = View.INVISIBLE
        fabBubble.alpha = 0f

        setupWebView()
        setupFab()
        setupDmSwipeControl()
        setupBackPress()

        if (settingsStore.reelsAlwaysHidden) {
            fabWrapper.visibility = View.GONE
        }

        touchOverlay.setOnClickListener {
            if (isFabExpanded) collapseFab()
        }

        webView.loadUrl("https://www.instagram.com/")
        handler.post(uiRunnable)
    }

    // ─────────────────────────────────────────────────────────────
    // Navegación — lógica de URLs
    // ─────────────────────────────────────────────────────────────

    /**
     * Extrae la sección base de una URL ignorando el ID específico.
     * /direct/t/123456/ → /direct/t/
     * /p/ABC123/ → /p/
     * /explore/search/ → /explore/search/
     */
    private fun extractSection(url: String): String {
        return when {
            url.contains("/direct/t/") -> "/direct/t/"
            url.contains("/direct/") -> "/direct/"
            url.contains("/p/") -> "/p/"
            url.contains("/reels/") || url == "https://www.instagram.com/reels/" -> "/reels/"
            url.contains("/explore/search") -> "/explore/search/"
            url.contains("/explore") -> "/explore/"
            url.contains("/stories/") -> "/stories/"
            url == "https://www.instagram.com/" || url == "https://www.instagram.com" -> "/"
            else -> url
        }
    }

    private fun isPrimitivePage(url: String): Boolean {
        val section = extractSection(url)
        return section == "/" ||
                section == "/reels/" ||
                (!url.contains("/direct/") &&
                        !url.contains("/p/") &&
                        !url.contains("/explore") &&
                        !url.contains("/stories/") &&
                        url.contains("instagram.com/"))
    }

    // ─────────────────────────────────────────────────────────────
    // FAB
    // ─────────────────────────────────────────────────────────────

    private fun setupFab() {
        updateFabText()
        makeFabDraggable()
    }

    private fun makeBubbleDrawable(cornerRadius: Float, alpha: Int = 255): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            this.cornerRadius = cornerRadius
            setStroke(dpToPx(1), Color.argb(alpha, 0, 0, 0))
        }
    }

    private fun expandFab() {
        if (isFabExpanded) return
        isFabExpanded = true

        val logoSize = dpToPx(60)
        val expandedWidth = dpToPx(160)
        val cornerRadius = logoSize / 2f

        fabText.visibility = View.INVISIBLE
        fabBubble.visibility = View.VISIBLE
        fabBubble.alpha = 1f

        applyBubbleSize(logoSize, logoSize, cornerRadius)
        fabBubble.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(dpToPx(1), Color.argb(60, 0, 0, 0))
        }

        updateFabText()

        val phase1 = ValueAnimator.ofInt(logoSize, logoSize + dpToPx(6)).apply {
            duration = 100
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Int
                fabBubble.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                    setStroke(dpToPx(1), Color.argb(60, 0, 0, 0))
                }
                applyBubbleSize(v, v, v / 2f)
            }
        }

        val phase2 = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val p = animator.animatedValue as Float
                val w = (logoSize + dpToPx(6) + (expandedWidth - logoSize - dpToPx(6)) * p).toInt()
                applyBubbleSize(w, logoSize, cornerRadius)
                fabBubble.background = makeBubbleDrawable(cornerRadius)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    fabText.visibility = View.VISIBLE
                    fabText.alpha = 0f
                    fabText.animate().alpha(1f).setDuration(150).start()
                }
            })
        }

        phase1.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { phase2.start() }
        })
        phase1.start()

        touchOverlay.visibility = View.VISIBLE
        fabHandler.removeCallbacks(autoCloseRunnable)
        fabHandler.postDelayed(autoCloseRunnable, 5000)
    }

    private fun collapseFab() {
        if (!isFabExpanded) return
        isFabExpanded = false

        fabHandler.removeCallbacks(autoCloseRunnable)
        touchOverlay.visibility = View.GONE

        val logoSize = dpToPx(60)
        val cornerRadius = logoSize / 2f
        val currentWidth = fabBubble.width

        fabText.animate().alpha(0f).setDuration(100).withEndAction {
            fabText.visibility = View.GONE
        }.start()

        val phase1 = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            interpolator = AccelerateInterpolator()
            addUpdateListener {
                val p = it.animatedValue as Float
                val w = (currentWidth - (currentWidth - logoSize) * p).toInt()
                applyBubbleSize(w, logoSize, cornerRadius)
                fabBubble.background = makeBubbleDrawable(cornerRadius)
            }
        }

        val phase2 = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 180
            interpolator = AccelerateInterpolator()
            addUpdateListener {
                val alpha = it.animatedValue as Float
                fabBubble.alpha = alpha
                fabBubble.background = makeBubbleDrawable(cornerRadius, (alpha * 60).toInt())
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    fabBubble.visibility = View.INVISIBLE
                    fabBubble.alpha = 1f
                    applyBubbleSize(logoSize, logoSize, cornerRadius)
                }
            })
        }

        phase1.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { phase2.start() }
        })
        phase1.start()
    }

    private fun applyBubbleSize(width: Int, height: Int, cornerRadius: Float) {
        val p = fabBubble.layoutParams
        p.width = width
        p.height = height
        fabBubble.layoutParams = p
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeFabDraggable() {
        var dX = 0f
        var dY = 0f
        var isDragging = false
        var downX = 0f
        var downY = 0f

        fabWrapper.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    downX = event.rawX
                    downY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val moveX = kotlin.math.abs(event.rawX - downX)
                    val moveY = kotlin.math.abs(event.rawY - downY)
                    if (moveX > 10 || moveY > 10) {
                        isDragging = true
                        if (isFabExpanded) collapseFab()
                    }
                    if (isDragging) {
                        val parent = view.parent as View
                        val marginTopPx = dpToPx(FAB_MARGIN_TOP_DP).toFloat()
                        val marginBottomPx = dpToPx(FAB_MARGIN_BOTTOM_DP).toFloat()
                        val newX = (event.rawX + dX).coerceIn(0f, (parent.width - view.width).toFloat())
                        val newY = (event.rawY + dY).coerceIn(marginTopPx, parent.height - view.height - marginBottomPx)
                        view.x = newX
                        view.y = newY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) snapToRight(view)
                    else if (isFabExpanded) collapseFab() else expandFab()
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToRight(view: View) {
        val parent = view.parent as View
        val margin = dpToPx(16).toFloat()
        val marginTopPx = dpToPx(FAB_MARGIN_TOP_DP).toFloat()
        val marginBottomPx = dpToPx(FAB_MARGIN_BOTTOM_DP).toFloat()
        val targetX = parent.width - view.width - margin
        val clampedY = view.y.coerceIn(marginTopPx, parent.height - view.height - marginBottomPx)
        view.animate().x(targetX).y(clampedY).setDuration(200).start()
    }

    private fun updateFabText() {
        val remaining = getRemainingSeconds()
        val minutes = remaining / 60
        val seconds = remaining % 60
        val timeText = String.format("%02d:%02d", minutes, seconds)
        fabText.text = when {
            remaining <= 0 -> "🚫 Límite"
            else -> timeText
        }
        fabText.setTextColor(if (remaining < 300) Color.parseColor("#E53935") else Color.parseColor("#1A1A1A"))
    }

    // ─────────────────────────────────────────────────────────────
    // WebView
    // ─────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 11; Pixel 5) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Mobile Safari/537.36"

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                // Bloqueo inmediato si la URL solicitada es explore
                if (isExploreGrid(url)) {
                    view?.loadUrl("https://www.instagram.com/explore/search/")
                    return true
                }
                return !url.contains("instagram.com")
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)

                // Si detectamos que se está empezando a cargar explore, abortamos YA
                if (isExploreGrid(url)) {
                    view?.stopLoading()
                    view?.loadUrl("https://www.instagram.com/explore/search/")
                    return
                }

                progressBar.visibility = View.VISIBLE
                Log.d("INSTA_URL", "Cargando: $url")
                url?.let { handleUrlChange(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE

                view?.evaluateJavascript("""
    (function() {
        if (window.__tgObserver) return;
        window.__tgObserver = true;
        let lastUrl = location.href;
        new MutationObserver(function() {
            if (location.href !== lastUrl) {
                lastUrl = location.href;
                Android.onUrlChanged(lastUrl);
            }
        }).observe(document, { subtree: true, childList: true });
    })();
""".trimIndent(), null)

                view?.evaluateJavascript("""
        (function() {
            // 1. Interceptar clics en el botón "Cancelar" del buscador
            document.addEventListener('click', function(e) {
                // Buscamos el botón cancelar por su texto o clase común en Instagram
                if (e.target.innerText === 'Cancelar' || e.target.innerText === 'Cancel') {
                    e.preventDefault();
                    e.stopPropagation();
                    // Redirigir a la home en lugar de a /explore/
                    window.location.href = 'https://www.instagram.com/';
                }
                
                // 2. Interceptar el botón de la Lupa (Explore)
                var anchor = e.target.closest('a');
                if (anchor && (anchor.getAttribute('href') === '/explore/' || anchor.getAttribute('href') === '/explore')) {
                    e.preventDefault();
                    window.location.href = 'https://www.instagram.com/explore/search/';
                }
            }, true);
        })();
    """.trimIndent(), null)
            }
        }

        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onUrlChanged(url: String) {
                runOnUiThread { handleUrlChange(url) }
            }
        }, "Android")
    }

    private fun handleUrlChange(url: String) {
        if (url == currentUrl) return

        if (isExploreGrid(url)) {
            webView.post { webView.loadUrl("https://www.instagram.com/explore/search/") }
            return
        }

        // Guardar si es un chat
        if (url.contains("/direct/t/")) {
            lastDirectThreadUrl = url
            lastSearchOrProfileUrl = "" // Limpiamos la otra para no confundir al /p/
        }

        // Guardar si es un perfil de usuario (pero no el propio ni secciones fijas)
        else if (isUserProfile(url)) {
            lastSearchOrProfileUrl = url
            lastDirectThreadUrl = "" // Limpiamos la otra
        }

        currentUrl = url

        val wasOnReels = isOnReels

        isOnReels = url.contains("/reels/") ||
                url.contains("/reel/") ||
                url == "https://www.instagram.com/reels/" ||
                url == "https://www.instagram.com/reels"

        when {
            !wasOnReels && isOnReels -> {
                settingsStore.reelsSessionStartMs = System.currentTimeMillis()
            }
            wasOnReels && !isOnReels -> {
                commitReelsSession()
            }
        }

        runOnUiThread {
            updateFabText()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DM Overlay — se muestra encima del WebView
    // ─────────────────────────────────────────────────────────────

    private fun showDmOverlayAndRedirect(targetUrl: String) {
        dmOverlay.alpha = 0f
        dmOverlay.visibility = View.VISIBLE
        dmOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        val destination = if (targetUrl.isNotEmpty()) targetUrl
        else "https://www.instagram.com/direct/inbox/"

        handler.postDelayed({
            // Precargar la URL destino mientras el overlay está visible
            webView.loadUrl(destination)

            handler.postDelayed({
                runOnUiThread {
                    dmOverlay.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction { dmOverlay.visibility = View.GONE }
                        .start()
                }
                isSwipeLocked = false
            }, 400)
        }, 1600)
    }

    // ─────────────────────────────────────────────────────────────
    // DM Swipe control
    // ─────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDmSwipeControl() {
        webView.setOnTouchListener { _, event ->
            val url = webView.url ?: ""
            val isOnDirect = url.contains("/direct/")

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartY = event.rawY
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isOnDirect && isSwipeLocked) {
                        return@setOnTouchListener true
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (isOnDirect && !isSwipeLocked) {
                        val deltaY = touchStartY - event.rawY

                        if (deltaY > 40) {
                            val now = System.currentTimeMillis()

                            if (now - lastSwipeTime < swipeCooldownMs) {
                                // Segundo swipe → dejar pasar el vídeo
                                // pero mostrar overlay a los 500ms
                                isSwipeLocked = true
                                val capturedUrl = url
                                handler.postDelayed({
                                    showDmOverlayAndRedirect(lastDirectThreadUrl.ifEmpty { capturedUrl })
                                }, 500)
                                return@setOnTouchListener false
                            }

                            lastSwipeTime = now
                        }
                    }

                    if (isOnDirect && isSwipeLocked) {
                        return@setOnTouchListener true
                    }
                }
            }

            if (isOnDirect && isSwipeLocked) true else false
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Back press
    // ─────────────────────────────────────────────────────────────

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val url = webView.url ?: "https://www.instagram.com/"

                when {
                    // 1. Si estamos en un post /p/ -> volver a la variable local (perfil o chat)
                    url.contains("/p/") -> {
                        val target = when {
                            lastDirectThreadUrl.isNotEmpty() -> lastDirectThreadUrl
                            lastSearchOrProfileUrl.isNotEmpty() -> lastSearchOrProfileUrl
                            else -> "https://www.instagram.com/"
                        }
                        webView.loadUrl(target)
                    }

                    // 2. Si estamos en un chat /direct/t/ -> volver al Inbox
                    url.contains("/direct/t/") -> {
                        webView.loadUrl("https://www.instagram.com/direct/inbox/")
                    }

                    // 3. Si estamos en un perfil de usuario buscado -> volver a búsqueda
                    isUserProfile(url) && lastSearchOrProfileUrl.isNotEmpty() -> {
                        webView.loadUrl("https://www.instagram.com/explore/search/")
                    }

                    // 4. Secciones que vuelven SIEMPRE a la pantalla inicial
                    url.contains("/explore/search/") ||
                            url.contains("/direct/inbox/") ||
                            url.contains("/reels/") ||
                            url.contains("/reel/") ||
                            url.contains("/accounts/") -> {
                        webView.loadUrl("https://www.instagram.com/")
                    }

                    // 5. Si ya estamos en la Home o URL base -> Salir de la app
                    url == "https://www.instagram.com/" || url == "https://www.instagram.com" -> {
                        finish()
                    }

                    else -> {
                        if (webView.canGoBack()) webView.goBack() else finish()
                    }
                }
            }
        })
    }

    // ─────────────────────────────────────────────────────────────
    // Reels limit
    // ─────────────────────────────────────────────────────────────

    private fun checkAndApplyReelsLimit(view: WebView? = null) {
        val target = view ?: webView
        val remaining = getRemainingSeconds()
        val isOnFeedReels = isOnReels && !isUserProfile(currentUrl)
        val shouldHideReels = settingsStore.reelsAlwaysHidden || remaining <= 0

        if (shouldHideReels) {
            target.evaluateJavascript(getHideReelsTabScript(), null)

            if (!settingsStore.reelsAlwaysHidden && remaining <= 0) {
                val isDirect = currentUrl.contains("/direct/")
                if (!isDirect) {
                    target.evaluateJavascript(getBlurFeedReelsScript(), null)
                }
            }

            if (isOnFeedReels && !settingsStore.reelsAlwaysHidden) {
                isOnReels = false
                commitReelsSession()
                if (isFabExpanded) collapseFab()

                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this@MainActivity, LimitReachedActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.fade_in_slow, R.anim.no_anim)
                    Handler(Looper.getMainLooper()).postDelayed({
                        webView.loadUrl("https://www.instagram.com/")
                    }, 1200)
                }, 150)
            }
        } else {
            target.evaluateJavascript(getShowReelsTabScript(), null)
            target.evaluateJavascript(getRemoveFeedBlurScript(), null)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun commitReelsSession() {
        val startMs = settingsStore.reelsSessionStartMs
        if (startMs == 0L) return
        val elapsedSeconds = (System.currentTimeMillis() - startMs) / 1000
        settingsStore.todayUsedSeconds += elapsedSeconds
        settingsStore.reelsSessionStartMs = 0L
    }

    private fun getRemainingSeconds(): Long {
        val base = settingsStore.todayRemainingSeconds
        return if (isOnReels && settingsStore.reelsSessionStartMs > 0L) {
            val activeSeconds = (System.currentTimeMillis() - settingsStore.reelsSessionStartMs) / 1000
            maxOf(0L, base - activeSeconds)
        } else base
    }

    private fun isUserProfile(url: String): Boolean {
        val systemPaths = listOf("/explore", "/reels", "/reel/", "/stories", "/search", "/direct", "/accounts", "/p/")
        val isInstagram = url.contains("instagram.com")
        val isSystemPath = systemPaths.any { url.contains(it) }
        val isHome = url == "https://www.instagram.com/" || url == "https://www.instagram.com"
        return isInstagram && !isSystemPath && !isHome
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // ─────────────────────────────────────────────────────────────
    // JavaScript scripts
    // ─────────────────────────────────────────────────────────────

    private fun getHideReelsTabScript(): String = """
        (function() {
            function hideReelsTab() {
                document.querySelectorAll('a').forEach(function(el) {
                    var href = el.getAttribute('href');
                    if (href === '/reels/' || href === '/reels') {
                        var tabItem = el.closest('div[role="link"]') || el.closest('div') || el;
                        tabItem.style.setProperty('display', 'none', 'important');
                    }
                });
                document.querySelectorAll('[aria-label*="Reels"], [aria-label*="reels"]').forEach(function(el) {
                    var parent = el.closest('a') || el.closest('div');
                    if (parent) parent.style.setProperty('display', 'none', 'important');
                });
            }
            hideReelsTab();
            window.__tgReelsObserver = new MutationObserver(function() { hideReelsTab(); });
            window.__tgReelsObserver.observe(document.body, { childList: true, subtree: true });
        })();
    """.trimIndent()

    private fun getShowReelsTabScript(): String = """
        (function() {
            if (window.__tgReelsObserver) { window.__tgReelsObserver.disconnect(); window.__tgReelsObserver = null; }
            document.querySelectorAll('a').forEach(function(el) {
                var href = el.getAttribute('href');
                if (href === '/reels/' || href === '/reels') {
                    var tabItem = el.closest('div[role="link"]') || el.closest('div') || el;
                    tabItem.style.removeProperty('display');
                }
            });
            document.querySelectorAll('[aria-label*="Reels"], [aria-label*="reels"]').forEach(function(el) {
                var parent = el.closest('a') || el.closest('div');
                if (parent) parent.style.removeProperty('display');
            });
        })();
    """.trimIndent()

    private fun getBlurFeedReelsScript(): String = """
        (function() {
            if (window.__tgFeedBlurInjected) return;
            if (window.location.href.indexOf('/direct/') !== -1) return;
            window.__tgFeedBlurInjected = true;
            function isReelElement(el) {
                if (el.getAttribute('aria-label') && el.getAttribute('aria-label').toLowerCase().includes('reel')) return true;
                if (el.querySelector('a[href*="/reel/"]')) return true;
                if (el.querySelector('[aria-label*="Reel"], [aria-label*="reel"]')) return true;
                return false;
            }
            function blurFeedReels() {
                document.querySelectorAll('article, div[role="button"]').forEach(function(el) {
                    if (el.getAttribute('data-tg-feed-blurred')) return;
                    if (!isReelElement(el)) return;
                    el.setAttribute('data-tg-feed-blurred', 'true');
                    el.style.setProperty('filter', 'blur(10px)', 'important');
                    el.style.setProperty('position', 'relative', 'important');
                    var overlay = document.createElement('div');
                    overlay.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:100%;z-index:9999;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;touch-action:pan-y;pointer-events:auto';
                    overlay.innerHTML = '<div style="background:rgba(0,0,0,0.7);border-radius:16px;padding:10px 18px;text-align:center;pointer-events:none"><div style="font-size:20px;">⏱️</div><div style="color:white;font-size:12px;font-family:sans-serif;font-weight:bold;margin-top:4px;">Límite alcanzado</div></div>';
                    overlay.addEventListener('click', function(e) { e.preventDefault(); e.stopPropagation(); }, true);
                    overlay.addEventListener('touchstart', function(e) { e.stopPropagation(); }, { passive: true });
                    el.appendChild(overlay);
                });
            }
            blurFeedReels();
            window.__tgFeedObserver = new MutationObserver(function() { blurFeedReels(); });
            window.__tgFeedObserver.observe(document.body, { childList: true, subtree: true });
        })();
    """.trimIndent()

    private fun getRemoveFeedBlurScript(): String = """
        (function() {
            window.__tgFeedBlurInjected = false;
            if (window.__tgFeedObserver) { window.__tgFeedObserver.disconnect(); window.__tgFeedObserver = null; }
            document.querySelectorAll('[data-tg-feed-blurred]').forEach(function(el) {
                el.removeAttribute('data-tg-feed-blurred');
                el.style.removeProperty('filter');
                el.style.removeProperty('pointer-events');
            });
        })();
    """.trimIndent()

    private fun getBlurScript(): String = """
        (function() {
            if (window.__tgBlurInjected) return;
            window.__tgBlurInjected = true;
            var SYSTEM_PATHS = ['/explore','/reels','/reel/','/stories','/search','/direct','/accounts','/p/'];
            function isExploreUrl() {
                var url = window.location.href;
                if (url.indexOf('/explore') === -1 && url.indexOf('/search') === -1) return false;
                var path = window.location.pathname;
                return SYSTEM_PATHS.some(function(p) { return path.indexOf(p) !== -1; });
            }
            function isSearchActive() {
                var input = document.querySelector('input[type="text"]');
                return input && input.value && input.value.length > 0;
            }
            function addOverlay() {
                if (!isExploreUrl()) return;
                if (isSearchActive()) return;
                if (document.getElementById('tg-explore-overlay')) return;
                var overlay = document.createElement('div');
                overlay.id = 'tg-explore-overlay';
                overlay.style.cssText = 'position:fixed;top:60px;left:0;right:0;bottom:50px;z-index:99999;backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px);background:rgba(0,0,0,0.4);display:flex;flex-direction:column;align-items:center;justify-content:center;pointer-events:none';
                overlay.innerHTML = '<div style="background:rgba(0,0,0,0.7);border-radius:20px;padding:20px 32px;text-align:center;pointer-events:none"><div style="font-size:32px;margin-bottom:8px;">🚫</div><div style="color:white;font-size:15px;font-weight:bold;font-family:sans-serif;">Videos bloqueados</div><div style="color:rgba(255,255,255,0.7);font-size:12px;margin-top:6px;font-family:sans-serif;">Usa el buscador para encontrar personas</div></div>';
                document.body.appendChild(overlay);
            }
            function removeOverlay() {
                var o = document.getElementById('tg-explore-overlay');
                if (o) o.remove();
            }
            function checkState() {
                var input = document.querySelector('input[type="text"]');
                var inputFocused = input === document.activeElement;
                if (!isExploreUrl() || isSearchActive() || inputFocused) removeOverlay();
                else addOverlay();
            }
            function watchInput() {
                var input = document.querySelector('input[type="text"]');
                if (!input || input.getAttribute('data-tg-watched')) return;
                input.setAttribute('data-tg-watched', 'true');
                input.addEventListener('input', function() {
                    if (input.value.length > 0) removeOverlay(); else if (isExploreUrl()) addOverlay();
                });
                input.addEventListener('focus', function() { removeOverlay(); });
                input.addEventListener('blur', function() {
                    setTimeout(function() { if (isExploreUrl() && !isSearchActive()) addOverlay(); }, 500);
                });
            }
            checkState();
            watchInput();
            window.__tgBlurObserver = new MutationObserver(function() { watchInput(); checkState(); });
            window.__tgBlurObserver.observe(document.body, { childList: true, subtree: true });
            window.__tgBlurInterval = setInterval(checkState, 500);
        })();
    """.trimIndent()

    private fun removeOverlayScript(): String = """
        (function() {
            var o = document.getElementById('tg-explore-overlay');
            if (o) o.remove();
            window.__tgBlurInjected = false;
            if (window.__tgBlurObserver) { window.__tgBlurObserver.disconnect(); window.__tgBlurObserver = null; }
            if (window.__tgBlurInterval) { clearInterval(window.__tgBlurInterval); window.__tgBlurInterval = null; }
        })();
    """.trimIndent()

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        settingsStore.resetIfNewDay()
        updateFabText()
    }

    override fun onPause() {
        super.onPause()
        if (isOnReels) {
            commitReelsSession()
            isOnReels = false
        }
    }
    // Agrega esto al final de MainActivity, antes del último '}'
    private fun isExploreGrid(url: String?): Boolean {
        if (url == null) return false
        return url == "https://www.instagram.com/explore/" ||
                url == "https://www.instagram.com/explore" ||
                url.endsWith("instagram.com/explore/") ||
                url.endsWith("instagram.com/explore")
    }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(uiRunnable)
        fabHandler.removeCallbacks(autoCloseRunnable)
    }
}
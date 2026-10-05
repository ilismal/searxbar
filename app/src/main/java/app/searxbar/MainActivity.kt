package app.searxbar

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import app.searxbar.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_FOCUS_SEARCH = "app.searxbar.action.FOCUS_SEARCH"
        private const val STATE_WEBVIEW = "webview"
        private const val STATE_SHOWING_RESULTS = "showing_results"
    }

    private lateinit var binding: ActivityMainBinding
    private val webView: WebView get() = binding.webView

    private var instance: String? = null
    private var defaultUserAgent: String = ""

    /** True mientras se navega por el flujo de login de Cloudflare Access / proveedor de identidad. */
    private var authInProgress = false

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (webView.canGoBack()) webView.goBack() else showEmptyState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        onBackPressedDispatcher.addCallback(this, backCallback)
        instance = Prefs.instanceUrl(this)

        setupToolbar()
        setupWebView()

        val webState = savedInstanceState?.getBundle(STATE_WEBVIEW)
        if (webState != null && savedInstanceState.getBoolean(STATE_SHOWING_RESULTS)) {
            webView.restoreState(webState)
            showResults()
        } else {
            showEmptyState()
        }

        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        applyUserAgent()
        val current = Prefs.instanceUrl(this)
        if (current != instance) {
            // La instancia ha cambiado en ajustes: empezamos de cero
            instance = current
            webView.clearHistory()
            showEmptyState()
        }
        updateEmptyState()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        webView.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(STATE_WEBVIEW, Bundle().also { webView.saveState(it) })
        outState.putBoolean(STATE_SHOWING_RESULTS, webView.isVisible)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    // region Intents

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEARCH, Intent.ACTION_WEB_SEARCH ->
                intent.getStringExtra(SearchManager.QUERY)?.let(::search)
            Intent.ACTION_SEND ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::search)
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.let(::search)
            ACTION_FOCUS_SEARCH -> focusSearch()
        }
    }

    // endregion

    // region UI

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_home -> { instance?.let(::load); true }
                R.id.action_refresh -> { webView.reload(); true }
                R.id.action_share -> { shareCurrentUrl(); true }
                R.id.action_open_browser -> { webView.url?.toUri()?.let(::openInBrowser); true }
                R.id.action_logout -> { instance?.let { load(CloudflareAccess.logoutUrl(it)) }; true }
                R.id.action_settings -> { openSettings(); true }
                else -> false
            }
        }

        binding.searchInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
                search(v.text.toString())
                true
            } else {
                false
            }
        }
        binding.configureButton.setOnClickListener { openSettings() }
        binding.pickPublicButton.setOnClickListener {
            startActivity(Intent(this, InstancePickerActivity::class.java))
        }
    }

    private fun showResults() {
        binding.emptyState.isVisible = false
        webView.isVisible = true
        backCallback.isEnabled = true
        updateMenu()
    }

    private fun showEmptyState() {
        webView.stopLoading()
        webView.isVisible = false
        binding.progress.hide()
        binding.emptyState.isVisible = true
        backCallback.isEnabled = false
        updateEmptyState()
        updateMenu()
    }

    private fun updateEmptyState() {
        val host = instance?.toUri()?.host
        binding.emptyTitle.setText(if (host != null) R.string.empty_title else R.string.empty_title_unconfigured)
        binding.emptyMessage.text =
            if (host != null) getString(R.string.empty_message, host) else getString(R.string.empty_message_unconfigured)
        binding.configureButton.isVisible = host == null
        binding.pickPublicButton.isVisible = host == null
    }

    private fun updateMenu() {
        val menu = binding.toolbar.menu
        val showing = webView.isVisible
        val configured = instance != null
        menu.findItem(R.id.action_refresh).isEnabled = showing
        menu.findItem(R.id.action_share).isEnabled = showing
        menu.findItem(R.id.action_open_browser).isEnabled = showing
        menu.findItem(R.id.action_home).isEnabled = configured
        menu.findItem(R.id.action_logout).isEnabled = configured
    }

    private fun focusSearch() {
        binding.searchInput.post {
            binding.searchInput.requestFocus()
            binding.searchInput.selectAll()
            WindowCompat.getInsetsController(window, binding.searchInput).show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(window, binding.searchInput).hide(WindowInsetsCompat.Type.ime())
        binding.searchInput.clearFocus()
    }

    private fun showMessage(text: CharSequence) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
    }

    // endregion

    // region Navegación

    private fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val base = instance ?: run {
            showMessage(getString(R.string.empty_title_unconfigured))
            openSettings()
            return
        }
        binding.searchInput.setText(q)
        hideKeyboard()
        load(Prefs.searchUrl(base, q))
    }

    private fun load(url: String) {
        showResults()
        webView.loadUrl(url)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun isInstance(uri: Uri): Boolean {
        val host = instance?.toUri()?.host ?: return false
        return uri.host.equals(host, ignoreCase = true)
    }

    /** Abre un enlace ajeno a la instancia. Devuelve true si se ha gestionado fuera del WebView. */
    private fun openExternal(uri: Uri): Boolean = when (Prefs.linkMode(this)) {
        Prefs.LinkMode.WEBVIEW -> false
        Prefs.LinkMode.BROWSER -> { openInBrowser(uri); true }
        Prefs.LinkMode.CUSTOM_TAB -> {
            try {
                CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
            } catch (e: ActivityNotFoundException) {
                openInBrowser(uri)
            }
            true
        }
    }

    private fun openInBrowser(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: ActivityNotFoundException) {
            showMessage(getString(R.string.error_no_app))
        }
    }

    /** Esquemas no web (intent:, mailto:, tel:, market:...). */
    private fun openNonHttp(uri: Uri) {
        try {
            val intent = if (uri.scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                }
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                intent.getStringExtra("browser_fallback_url")?.let { webView.loadUrl(it) }
                    ?: showMessage(getString(R.string.error_no_app))
            }
        } catch (e: Exception) {
            showMessage(getString(R.string.error_no_app))
        }
    }

    private fun shareCurrentUrl() {
        val url = webView.url ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(send, null))
    }

    /** Muestra un aviso con opción de volver a iniciar sesión en Cloudflare Access. */
    private fun showAccessDenied(failed: Uri) {
        val base = instance ?: return
        Snackbar.make(binding.root, R.string.access_denied, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.action_login) {
                CloudflareAccess.clearSession(base)
                // Al no haber sesión, Access redirige al login y después vuelve a esta URL
                webView.loadUrl(if (isInstance(failed)) failed.toString() else base)
            }
            .show()
    }

    // endregion

    // region WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        defaultUserAgent = webView.settings.userAgentString
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            // Los enlaces con target="_blank" y window.open() se cargan en la misma vista
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // Algunos proveedores de identidad necesitan cookies de terceros durante el login
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = Client()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progress.setProgressCompat(newProgress, true)
            }
        }
    }

    /**
     * Google y otros proveedores bloquean el login dentro de WebViews detectando
     * el marcador "; wv" y "Version/x.y" del user agent. Quitarlos permite
     * completar el inicio de sesión de Cloudflare Access con esos proveedores.
     */
    private fun applyUserAgent() {
        webView.settings.userAgentString = if (Prefs.hideWebViewUa(this)) {
            defaultUserAgent.replace("; wv", "").replace(Regex("""Version/\S+\s"""), "")
        } else {
            defaultUserAgent
        }
    }

    private fun syncQueryFromUrl(url: String?) {
        val uri = url?.toUri() ?: return
        if (!isInstance(uri) || binding.searchInput.hasFocus()) return
        uri.getQueryParameter("q")?.let { binding.searchInput.setText(it) }
    }

    private inner class Client : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when {
                uri.scheme?.lowercase() !in setOf("http", "https") -> { openNonHttp(uri); true }
                // Redirección al login de Cloudflare Access: se sigue dentro de la app
                CloudflareAccess.isAccessUrl(uri) -> { authInProgress = true; false }
                isInstance(uri) -> false
                // Durante el login, el proveedor de identidad (Google, GitHub, Okta...) también se queda dentro
                authInProgress -> false
                !request.isForMainFrame -> false
                else -> openExternal(uri)
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            val uri = url.toUri()
            if (CloudflareAccess.isAccessUrl(uri)) authInProgress = true
            else if (isInstance(uri)) authInProgress = false
            binding.progress.show()
        }

        override fun onPageFinished(view: WebView, url: String) {
            binding.progress.hide()
            syncQueryFromUrl(url)
            CookieManager.getInstance().flush()
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (!request.isForMainFrame) return
            val fromInstanceOrAccess = isInstance(request.url) || CloudflareAccess.isAccessUrl(request.url)
            if (fromInstanceOrAccess && response.statusCode in setOf(401, 403)) {
                showAccessDenied(request.url)
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                binding.progress.hide()
                showMessage(getString(R.string.error_loading, error.description))
            }
        }
    }

    // endregion
}

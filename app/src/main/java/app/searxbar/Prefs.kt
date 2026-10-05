package app.searxbar

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.preference.PreferenceManager

object Prefs {
    const val KEY_INSTANCE_URL = "instance_url"
    const val KEY_LINK_MODE = "link_mode"
    const val KEY_HIDE_WEBVIEW_UA = "hide_webview_ua"
    const val KEY_CLEAR_DATA = "clear_data"
    const val KEY_PICK_PUBLIC = "pick_public_instance"

    enum class LinkMode { CUSTOM_TAB, BROWSER, WEBVIEW }

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    /** URL base de la instancia ya normalizada (sin barra final) o null si no está configurada. */
    fun instanceUrl(context: Context): String? =
        prefs(context).getString(KEY_INSTANCE_URL, null)?.let(::normalizeUrl)

    fun setInstanceUrl(context: Context, url: String) {
        prefs(context).edit { putString(KEY_INSTANCE_URL, url) }
    }

    fun linkMode(context: Context): LinkMode =
        when (prefs(context).getString(KEY_LINK_MODE, "custom_tab")) {
            "browser" -> LinkMode.BROWSER
            "webview" -> LinkMode.WEBVIEW
            else -> LinkMode.CUSTOM_TAB
        }

    fun hideWebViewUa(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_WEBVIEW_UA, true)

    /** Añade https:// si falta y valida que sea una URL http(s) con host. */
    fun normalizeUrl(input: String): String? {
        var url = input.trim().trimEnd('/')
        if (url.isEmpty()) return null
        if (!url.contains("://")) url = "https://$url"
        val uri = Uri.parse(url)
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return url
    }

    fun searchUrl(instance: String, query: String): String =
        Uri.parse(instance).buildUpon()
            .appendEncodedPath("search")
            .appendQueryParameter("q", query)
            .build()
            .toString()
}

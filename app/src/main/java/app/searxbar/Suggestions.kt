package app.searxbar

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Sugerencias de búsqueda a través del endpoint /autocompleter de la instancia. */
object Suggestions {
    /** Usa el proveedor configurado en las preferencias de la propia instancia. */
    const val SOURCE_INSTANCE = "instance"
    const val SOURCE_OFF = "off"

    /**
     * Devuelve las sugerencias o una lista vacía ante cualquier error. Las cookies
     * del WebView (preferencias de SearXNG y sesión de Cloudflare Access) y su
     * user agent se reenvían para que la instancia trate la petición como del navegador.
     */
    suspend fun fetch(
        instance: String,
        query: String,
        source: String,
        userAgent: String,
        cookies: String?,
    ): List<String> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(instance).buildUpon()
            .appendEncodedPath("autocompleter")
            .appendQueryParameter("q", query)
            .apply { if (source != SOURCE_INSTANCE) appendQueryParameter("autocomplete", source) }
            .build()
        try {
            val conn = URL(uri.toString()).openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            // Una redirección significa login de Cloudflare Access: sin sugerencias hasta entrar
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            cookies?.let { conn.setRequestProperty("Cookie", it) }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext emptyList()
                parse(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Formato OpenSearch: `["consulta", ["sugerencia 1", "sugerencia 2", ...], ...]` */
    fun parse(body: String): List<String> {
        val items = JSONArray(body.trim()).optJSONArray(1) ?: return emptyList()
        return (0 until items.length())
            .mapNotNull { items.optString(it).trim().takeIf(String::isNotEmpty) }
            .distinct()
    }
}

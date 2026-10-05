package app.searxbar

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Instancia pública listada en searx.space. */
data class PublicInstance(
    val url: String,
    val host: String,
    val searchMedian: Double?,
    val searchSuccess: Double?,
    val uptimeMonth: Double?,
    val tlsGrade: String?,
    val version: String?,
)

/** Cliente del listado de instancias públicas de https://searx.space */
object SearxSpace {
    private const val INSTANCES_URL = "https://searx.space/data/instances.json"
    private const val CACHE_MILLIS = 10 * 60 * 1000L

    private var cache: List<PublicInstance>? = null
    private var cacheTime = 0L

    suspend fun fetch(forceRefresh: Boolean = false): List<PublicInstance> {
        val cached = cache
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cacheTime < CACHE_MILLIS) {
            return cached
        }
        val result = withContext(Dispatchers.IO) { parse(download()) }
        cache = result
        cacheTime = System.currentTimeMillis()
        return result
    }

    private fun download(): String {
        val conn = URL(INSTANCES_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Solo instancias accesibles por la red normal (sin Tor), sin errores en la
     * última comprobación. Primero las que responden búsquedas, de más rápida a más lenta.
     */
    fun parse(json: String): List<PublicInstance> {
        val instances = JSONObject(json).getJSONObject("instances")
        return instances.keys().asSequence().mapNotNull { key ->
            val o = instances.getJSONObject(key)
            if (o.optString("network_type") != "normal" || !o.isNull("error")) return@mapNotNull null
            val url = Prefs.normalizeUrl(key) ?: return@mapNotNull null
            val search = o.optJSONObject("timing")?.optJSONObject("search")
            PublicInstance(
                url = url,
                host = Uri.parse(url).host ?: url,
                searchMedian = search?.optJSONObject("all")?.doubleOrNull("median"),
                searchSuccess = search?.doubleOrNull("success_percentage"),
                uptimeMonth = o.optJSONObject("uptime")?.doubleOrNull("uptimeMonth"),
                tlsGrade = o.optJSONObject("tls")?.stringOrNull("grade"),
                version = o.stringOrNull("version")?.substringBefore('+'),
            )
        }.sortedWith(
            compareByDescending<PublicInstance> { (it.searchSuccess ?: 0.0) > 0 }
                .thenBy(nullsLast()) { it.searchMedian }
        ).toList()
    }

    private fun JSONObject.doubleOrNull(name: String): Double? =
        if (isNull(name)) null else optDouble(name).takeUnless { it.isNaN() }

    private fun JSONObject.stringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
}

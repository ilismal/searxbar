package app.searxbar

import android.net.Uri
import android.webkit.CookieManager

/**
 * Utilidades para instancias protegidas con Cloudflare Access.
 *
 * Cuando la sesión no existe o ha caducado, Access redirige la petición a
 * `https://<equipo>.cloudflareaccess.com/cdn-cgi/access/login/...` (o a
 * `/cdn-cgi/access/login` en el propio dominio). Tras autenticarse con el
 * proveedor de identidad vuelve a `/cdn-cgi/access/authorized`, que fija la
 * cookie `CF_Authorization` y redirige a la URL original.
 */
object CloudflareAccess {
    private const val TEAM_DOMAIN_SUFFIX = ".cloudflareaccess.com"
    private const val ACCESS_PATH_PREFIX = "/cdn-cgi/access/"
    private const val SESSION_COOKIE = "CF_Authorization"

    fun isAccessUrl(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return host.endsWith(TEAM_DOMAIN_SUFFIX) || uri.path.orEmpty().startsWith(ACCESS_PATH_PREFIX)
    }

    fun logoutUrl(instance: String): String =
        Uri.parse(instance).buildUpon().path(ACCESS_PATH_PREFIX + "logout").clearQuery().build().toString()

    /** Elimina la cookie de sesión de Access para forzar un nuevo inicio de sesión. */
    fun clearSession(instance: String) {
        val host = Uri.parse(instance).host ?: return
        CookieManager.getInstance().apply {
            setCookie(instance, "$SESSION_COOKIE=; Max-Age=0; Path=/")
            setCookie(instance, "$SESSION_COOKIE=; Max-Age=0; Path=/; Domain=$host")
            flush()
        }
    }
}

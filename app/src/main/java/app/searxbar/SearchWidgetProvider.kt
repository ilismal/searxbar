package app.searxbar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

/** Widget de pantalla de inicio con forma de barra de búsqueda. */
class SearchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildViews(context)
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun buildViews(context: Context): RemoteViews {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_FOCUS_SEARCH
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val host = Prefs.instanceUrl(context)?.let { Uri.parse(it).host }
            val hint = if (host != null) context.getString(R.string.search_in, host)
            else context.getString(R.string.search_hint)

            return RemoteViews(context.packageName, R.layout.widget_search).apply {
                setTextViewText(R.id.widget_hint, hint)
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }
        }

        /** Refresca todos los widgets (p. ej. tras cambiar la instancia). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SearchWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}

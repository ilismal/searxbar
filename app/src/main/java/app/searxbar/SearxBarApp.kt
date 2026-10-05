package app.searxbar

import android.app.Application
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors

class SearxBarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Material You: usa los colores del fondo de pantalla en Android 12+
        DynamicColors.applyToActivitiesIfAvailable(this)
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
    }
}

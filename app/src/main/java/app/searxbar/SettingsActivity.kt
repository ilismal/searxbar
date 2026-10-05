package app.searxbar

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import app.searxbar.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val pickInstance = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val url = result.data?.getStringExtra(InstancePickerActivity.EXTRA_URL)
            if (result.resultCode == Activity.RESULT_OK && url != null) {
                // El selector ya lo ha guardado; actualizamos lo que muestra la preferencia
                findPreference<EditTextPreference>(Prefs.KEY_INSTANCE_URL)?.text = url
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<EditTextPreference>(Prefs.KEY_INSTANCE_URL)?.apply {
                setOnBindEditTextListener { editText ->
                    editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    editText.setSingleLine()
                    editText.hint = getString(R.string.pref_instance_hint)
                    editText.setSelection(editText.text.length)
                }
                summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                    pref.text?.takeIf { it.isNotBlank() } ?: getString(R.string.pref_instance_not_set)
                }
                setOnPreferenceChangeListener { pref, newValue ->
                    val raw = newValue as String
                    val normalized = Prefs.normalizeUrl(raw)
                    if (raw.isNotBlank() && normalized == null) {
                        Toast.makeText(requireContext(), R.string.pref_instance_invalid, Toast.LENGTH_LONG).show()
                    } else {
                        // Guardamos la versión normalizada en lugar del texto tal cual
                        (pref as EditTextPreference).text = normalized.orEmpty()
                        SearchWidgetProvider.updateAll(requireContext())
                    }
                    false
                }
            }

            findPreference<Preference>(Prefs.KEY_PICK_PUBLIC)?.setOnPreferenceClickListener {
                pickInstance.launch(Intent(requireContext(), InstancePickerActivity::class.java))
                true
            }

            findPreference<Preference>(Prefs.KEY_CLEAR_DATA)?.setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.pref_clear_data_title)
                    .setMessage(R.string.pref_clear_data_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.action_clear) { _, _ -> clearWebData() }
                    .show()
                true
            }
        }

        private fun clearWebData() {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            WebView(requireContext()).apply {
                clearCache(true)
                destroy()
            }
            Toast.makeText(requireContext(), R.string.pref_clear_data_done, Toast.LENGTH_SHORT).show()
        }
    }
}

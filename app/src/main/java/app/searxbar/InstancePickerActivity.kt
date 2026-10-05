package app.searxbar

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.searxbar.databinding.ActivityInstancePickerBinding
import app.searxbar.databinding.ItemInstanceBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/** Lista las instancias públicas de searx.space y guarda la elegida. */
class InstancePickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "url"
    }

    private lateinit var binding: ActivityInstancePickerBinding
    private val adapter = InstanceAdapter(::select)
    private var instances: List<PublicInstance> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityInstancePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.list.updatePadding(bottom = maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_refresh) load(forceRefresh = true)
            true
        }
        binding.list.adapter = adapter
        adapter.current = Prefs.instanceUrl(this)
        binding.filterInput.doAfterTextChanged { applyFilter() }
        binding.retryButton.setOnClickListener { load(forceRefresh = true) }

        load(forceRefresh = false)
    }

    private fun load(forceRefresh: Boolean) {
        binding.loading.isVisible = true
        binding.errorState.isVisible = false
        binding.list.isVisible = false
        lifecycleScope.launch {
            try {
                instances = SearxSpace.fetch(forceRefresh)
                binding.list.isVisible = true
                applyFilter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                binding.errorMessage.text = getString(R.string.picker_error, e.localizedMessage ?: e.javaClass.simpleName)
                binding.errorState.isVisible = true
            } finally {
                binding.loading.isVisible = false
            }
        }
    }

    private fun applyFilter() {
        val filter = binding.filterInput.text?.toString()?.trim().orEmpty()
        adapter.submit(
            if (filter.isEmpty()) instances
            else instances.filter { it.host.contains(filter, ignoreCase = true) }
        )
    }

    private fun select(instance: PublicInstance) {
        Prefs.setInstanceUrl(this, instance.url)
        SearchWidgetProvider.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_URL, instance.url))
        finish()
    }

    private class InstanceAdapter(
        private val onClick: (PublicInstance) -> Unit,
    ) : RecyclerView.Adapter<InstanceAdapter.Holder>() {

        var current: String? = null
        private var items: List<PublicInstance> = emptyList()

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<PublicInstance>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemInstanceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val context = holder.binding.root.context
            holder.binding.host.text = item.host
            holder.binding.details.text = listOfNotNull(
                item.searchMedian?.let { context.getString(R.string.picker_search_time, String.format(Locale.getDefault(), "%.1f", it)) },
                item.uptimeMonth?.let { context.getString(R.string.picker_uptime, String.format(Locale.getDefault(), "%.0f", it)) },
                item.tlsGrade?.let { context.getString(R.string.picker_tls, it) },
                item.version,
            ).joinToString(" · ").ifEmpty { context.getString(R.string.picker_no_data) }
            holder.binding.check.isVisible = item.url == current
            holder.binding.root.setOnClickListener { onClick(item) }
        }

        class Holder(val binding: ItemInstanceBinding) : RecyclerView.ViewHolder(binding.root)
    }
}

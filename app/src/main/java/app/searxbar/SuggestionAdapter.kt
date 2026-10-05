package app.searxbar

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView

/**
 * Adaptador para el desplegable de sugerencias. No filtra por su cuenta: muestra
 * tal cual lo que devuelve la instancia (las sugerencias no siempre empiezan por
 * el texto escrito).
 */
class SuggestionAdapter(context: Context) : BaseAdapter(), Filterable {

    private val inflater = LayoutInflater.from(context)
    private var items: List<String> = emptyList()

    fun submit(list: List<String>) {
        items = list
        if (list.isEmpty()) notifyDataSetInvalidated() else notifyDataSetChanged()
    }

    override fun getCount() = items.size
    override fun getItem(position: Int): String = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_suggestion, parent, false)
        view.findViewById<TextView>(R.id.suggestion_text).text = items[position]
        return view
    }

    override fun getFilter() = object : Filter() {
        override fun performFiltering(constraint: CharSequence?) =
            FilterResults().apply { values = items; count = items.size }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            if (items.isEmpty()) notifyDataSetInvalidated() else notifyDataSetChanged()
        }

        override fun convertResultToString(resultValue: Any?) = resultValue as? String ?: ""
    }
}

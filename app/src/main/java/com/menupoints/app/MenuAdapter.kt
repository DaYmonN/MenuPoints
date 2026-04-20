package com.menupoints.app

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.menupoints.app.models.MenuItem

class MenuAdapter(
    private var items: List<MenuItem>,
    private val marks: MutableMap<String, Int>
) : RecyclerView.Adapter<MenuAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryTitle: TextView = view.findViewById(R.id.categoryTitle)
        val dishName: TextView = view.findViewById(R.id.dishName)
        val inputValue: EditText = view.findViewById(R.id.inputValue)
        val categoryDivider: View = view.findViewById(R.id.categoryDivider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val currentMark = marks[item.id] ?: 0

        holder.dishName.text = item.name

        val isNewCategory = position == 0 || items[position - 1].category != item.category

        if (isNewCategory) {
            holder.categoryTitle.visibility = View.VISIBLE
            holder.categoryDivider.visibility = View.GONE
            holder.categoryTitle.text = "📌 ${item.category}"
        } else {
            holder.categoryTitle.visibility = View.GONE
            holder.categoryDivider.visibility = View.VISIBLE
        }

        // Убираем старый TextWatcher, чтобы не было дублирования
        val oldWatcher = holder.inputValue.getTag(R.id.text_watchers) as? TextWatcher
        oldWatcher?.let { holder.inputValue.removeTextChangedListener(it) }

        if (currentMark == 0) {
            holder.inputValue.setText("")
        } else {
            holder.inputValue.setText(currentMark.toString())
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val value = s.toString().toIntOrNull() ?: 0
                marks[item.id] = value
            }
        }
        holder.inputValue.addTextChangedListener(watcher)
        holder.inputValue.setTag(R.id.text_watchers, watcher)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<MenuItem>, newMarks: MutableMap<String, Int>) {
        this.items = newItems
        marks.clear()
        marks.putAll(newMarks)
        notifyDataSetChanged()
    }

    fun getAllMarks(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        for (i in 0 until itemCount) {
            val item = items[i]
            val value = marks[item.id] ?: 0
            result[item.id] = value
        }
        return result
    }
}
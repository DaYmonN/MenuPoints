package com.menupoints.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.menupoints.app.models.Point

class PointsAdapter(
    private val points: MutableList<Point>,
    private val onItemClick: (Point) -> Unit,
    private val onEditClick: (Point) -> Unit,
    private val onDeleteClick: (Point) -> Unit,
    private val onNoteClick: (Point) -> Unit,
    private val currentPointId: String?
) : RecyclerView.Adapter<PointsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.pointCard)
        val name: TextView = view.findViewById(R.id.pointName)
        val editBtn: ImageButton = view.findViewById(R.id.editPoint)
        val deleteBtn: ImageButton = view.findViewById(R.id.deletePoint)
        val noteBtn: ImageButton = view.findViewById(R.id.notePoint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_point, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val point = points[position]
        holder.name.text = "📌 ${point.name}"

        if (point.id == currentPointId) {
            holder.card.setCardBackgroundColor(0xFF2a2a2a.toInt())
        } else {
            holder.card.setCardBackgroundColor(0xFF1c1c1c.toInt())
        }

        holder.card.setOnClickListener { onItemClick(point) }
        holder.editBtn.setOnClickListener { onEditClick(point) }
        holder.deleteBtn.setOnClickListener { onDeleteClick(point) }
        holder.noteBtn.setOnClickListener { onNoteClick(point) }
    }

    override fun getItemCount() = points.size
}
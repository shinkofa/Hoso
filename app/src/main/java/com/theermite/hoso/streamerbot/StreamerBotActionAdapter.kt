package com.theermite.hoso.streamerbot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.theermite.hoso.R

/**
 * RecyclerView adapter for the overlay actions panel (G6.1 Phase E.1).
 *
 * Displays the list of [StreamerBotAction]s published by the connected
 * Streamer.bot instance. Disabled actions are still shown but greyed out
 * — the streamer keeps an honest view of what their PC exposes, instead
 * of a list that silently changes between visits.
 *
 * Tap → [onTap] with the action id; the OverlayService forwards it to
 * [com.theermite.hoso.services.StreamerBotService.sendAction].
 */
class StreamerBotActionAdapter(
    private val onTap: (StreamerBotAction) -> Unit,
) : RecyclerView.Adapter<StreamerBotActionAdapter.VH>() {

    private val items = ArrayList<StreamerBotAction>()

    /** Replace the dataset and refresh. Called when the bridge sends a
     *  new GetActions response (or a SubAction update). */
    fun submit(list: List<StreamerBotAction>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.overlay_action_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = items[position]
        holder.name.text = a.name
        if (!a.group.isNullOrBlank()) {
            holder.group.text = a.group
            holder.group.visibility = View.VISIBLE
        } else {
            holder.group.visibility = View.GONE
        }
        holder.itemView.alpha = if (a.enabled) 1.0f else 0.45f
        holder.itemView.isEnabled = a.enabled
        holder.itemView.setOnClickListener {
            if (a.enabled) onTap(a)
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.action_name)
        val group: TextView = view.findViewById(R.id.action_group)
    }
}

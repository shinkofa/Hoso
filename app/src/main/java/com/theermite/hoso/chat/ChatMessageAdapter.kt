package com.theermite.hoso.chat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.theermite.hoso.R
import java.util.ArrayDeque

/**
 * RecyclerView adapter for the chat bubble. Holds a bounded ring of
 * messages so a long stream cannot grow the heap without limit — chat
 * is a live signal, not a transcript.
 *
 * Display-name color comes from the Twitch `color` tag when present;
 * otherwise we hash the lowercase nick to the standard Twitch fallback
 * palette so the same viewer keeps a stable identity across sessions.
 */
class ChatMessageAdapter : RecyclerView.Adapter<ChatMessageAdapter.VH>() {

    private val items = ArrayDeque<IrcMessage>(MAX_HISTORY)

    /** Appends one message, evicting the oldest if we hit the cap. */
    fun append(msg: IrcMessage) {
        if (items.size >= MAX_HISTORY) {
            items.removeFirst()
            notifyItemRemoved(0)
        }
        items.addLast(msg)
        notifyItemInserted(items.size - 1)
    }

    fun clear() {
        val n = items.size
        items.clear()
        if (n > 0) notifyItemRangeRemoved(0, n)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_message_item, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = items.elementAt(position)
        val name = msg.displayName ?: "anon"
        holder.name.text = name
        holder.name.setTextColor(parseColor(msg.color, name))
        holder.text.text = msg.text ?: ""
    }

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.msg_name)
        val text: TextView = view.findViewById(R.id.msg_text)
    }

    companion object {
        const val MAX_HISTORY = 200

        // Twitch fallback name colors (visible on dark UI). Mirrors the
        // standard web client when the viewer has not chosen a color.
        private val FALLBACK_PALETTE = intArrayOf(
            0xFFFF0000.toInt(), // Red
            0xFF0000FF.toInt(), // Blue
            0xFF008000.toInt(), // Green
            0xFFB22222.toInt(), // FireBrick
            0xFFFF7F50.toInt(), // Coral
            0xFF9ACD32.toInt(), // YellowGreen
            0xFFFF4500.toInt(), // OrangeRed
            0xFF2E8B57.toInt(), // SeaGreen
            0xFFDAA520.toInt(), // GoldenRod
            0xFFD2691E.toInt(), // Chocolate
            0xFF5F9EA0.toInt(), // CadetBlue
            0xFF1E90FF.toInt(), // DodgerBlue
            0xFFFF69B4.toInt(), // HotPink
            0xFF8A2BE2.toInt(), // BlueViolet
            0xFF00FF7F.toInt(), // SpringGreen
        )

        private fun parseColor(raw: String?, nick: String): Int {
            if (!raw.isNullOrBlank()) {
                runCatching { return Color.parseColor(raw) }
            }
            val idx = (nick.lowercase().hashCode() and 0x7fffffff) %
                FALLBACK_PALETTE.size
            return FALLBACK_PALETTE[idx]
        }
    }
}

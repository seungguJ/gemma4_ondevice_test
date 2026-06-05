package com.example.gemma4ondevicetest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatSessionAdapter(
    private val onSessionClick: (ChatSession) -> Unit,
    private val onDeleteClick: (ChatSession) -> Unit
) : RecyclerView.Adapter<ChatSessionAdapter.SessionViewHolder>() {
    private val sessions = mutableListOf<ChatSession>()
    private var activeSessionId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(sessions[position], sessions[position].id == activeSessionId)
    }

    override fun getItemCount(): Int = sessions.size

    fun submitList(nextSessions: List<ChatSession>, activeId: String?) {
        sessions.clear()
        sessions.addAll(nextSessions.sortedByDescending { it.updatedAt })
        activeSessionId = activeId
        notifyDataSetChanged()
    }

    inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.text_session_title)
        private val kind: TextView = itemView.findViewById(R.id.text_session_kind)
        private val delete: TextView = itemView.findViewById(R.id.button_delete_session)

        fun bind(session: ChatSession, active: Boolean) {
            title.text = session.title
            kind.text = session.kind.label
            itemView.alpha = if (active) 1.0f else 0.84f
            itemView.setBackgroundResource(
                if (active) R.drawable.bg_session_item_active else R.drawable.bg_session_item
            )
            itemView.setOnClickListener { onSessionClick(session) }
            delete.setOnClickListener { onDeleteClick(session) }
        }
    }
}

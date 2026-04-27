package com.example.gemma4ondevicetest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private val items: MutableList<ChatMessage>
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun addMessage(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.lastIndex)
    }

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bubble: TextView = itemView.findViewById(R.id.message_bubble)
        private val largeMargin = itemView.resources.getDimensionPixelSize(R.dimen.message_margin_large)

        fun bind(message: ChatMessage) {
            bubble.text = message.text
            val background = if (message.fromUser) {
                R.drawable.bg_chat_user
            } else {
                R.drawable.bg_chat_model
            }
            bubble.setBackgroundResource(background)
            bubble.textAlignment = if (message.fromUser) View.TEXT_ALIGNMENT_TEXT_END else View.TEXT_ALIGNMENT_TEXT_START
            (bubble.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = if (message.fromUser) android.view.Gravity.END else android.view.Gravity.START
                topMargin = 2
                bottomMargin = 2
                marginStart = if (message.fromUser) largeMargin else 40
                marginEnd = if (message.fromUser) 40 else largeMargin
            }
            bubble.requestLayout()
        }
    }
}

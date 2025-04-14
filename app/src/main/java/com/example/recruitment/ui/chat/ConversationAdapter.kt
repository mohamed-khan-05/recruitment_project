package com.example.recruitment.ui.chat

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.recruitment.databinding.ItemConversationBinding
import com.example.recruitment.model.Conversation
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.auth.FirebaseAuth

class ConversationAdapter(
    private val conversations: List<Conversation>,
    private val onConversationClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding =
            ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ConversationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(conversations[position])
    }

    override fun getItemCount(): Int = conversations.size

    inner class ConversationViewHolder(private val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(conversation: Conversation) {
            val currentUser = FirebaseAuth.getInstance().currentUser?.email
            val other = conversation.participants.firstOrNull { it != currentUser } ?: "Unknown"

            Log.d("ConversationAdapter", "Binding conversation with: $other")
            Log.d(
                "ConversationAdapter",
                "Last message: ${conversation.lastMessage}, time: ${conversation.lastTimestamp}"
            )

            binding.tvUser.text = other
            binding.tvLastMessage.text = conversation.lastMessage ?: "No message"
            binding.tvTime.text = SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(Date(conversation.lastTimestamp ?: 0L))

            binding.root.setOnClickListener {
                onConversationClick(conversation)
            }
        }

    }
}

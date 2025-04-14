package com.example.recruitment.ui.chat

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.recruitment.databinding.ItemMessageSentBinding
import com.example.recruitment.databinding.ItemMessageReceivedBinding
import com.example.recruitment.model.Message
import com.google.firebase.auth.FirebaseAuth

class MessageAdapter(private val messages: List<Message>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val TAG = "MessageAdapter"
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.senderEmail == currentUserEmail) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val binding =
                ItemMessageSentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            SentMessageViewHolder(binding)
        } else {
            val binding = ItemMessageReceivedBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            ReceivedMessageViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        Log.d(TAG, "Binding message: ${message.message} from ${message.senderEmail}")

        if (holder is SentMessageViewHolder) {
            holder.bind(message)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class SentMessageViewHolder(private val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.tvMessage.text = message.message
            binding.tvSender.text = "You"
        }
    }

    inner class ReceivedMessageViewHolder(private val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.tvMessage.text = message.message
            binding.tvSender.text = message.senderEmail
        }
    }
}

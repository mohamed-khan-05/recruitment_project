package com.example.recruitment.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.recruitment.R
import com.example.recruitment.databinding.ItemNotificationBinding
import com.example.recruitment.model.NotificationData
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private val items: MutableList<NotificationData>,
    private val onDelete: (NotificationData, Int) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        fun bind(nd: NotificationData, position: Int) {
            binding.tvTitle.text = nd.title
            binding.tvMessage.text = nd.message
            binding.tvTimestamp.text = fmt.format(Date(nd.timestamp * 1000))
            val bg = if (!nd.read) R.color.notification_unread_bg else android.R.color.white
            binding.cardNotification.setCardBackgroundColor(
                ContextCompat.getColor(binding.root.context, bg)
            )
            binding.btnDelete.setOnClickListener {
                onDelete(nd, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position], position)

    override fun getItemCount() = items.size
}

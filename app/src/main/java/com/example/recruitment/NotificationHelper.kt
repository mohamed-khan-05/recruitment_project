package com.example.recruitment

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.firestore.*
import com.example.recruitment.R

class NotificationHelper(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    companion object {
        const val CHANNEL_ID = "job_alerts"
    }

    fun startListening(userId: String) {
        createNotificationChannelIfNeeded()
        listener = db.collection("users")
            .document(userId)
            .collection("notifications")
            .whereEqualTo("read", false)
            .addSnapshotListener { snaps, error ->
                if (error != null || snaps == null) return@addSnapshotListener
                for (change in snaps.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val title = doc.getString("title") ?: "Notification"
                        val message = doc.getString("message") ?: ""
                        showNotification(doc.id, title, message)
                        doc.reference.update("read", true)
                    }
                }
            }
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }

    private fun showNotification(id: String, title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(id.hashCode(), notification)
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Job Alerts"
            val description = "Updates on job applications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

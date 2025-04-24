package com.example.recruitment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PushNotificationService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "job_alerts"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("fcmToken", token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        remoteMessage.notification?.let { notif ->

            // 1️⃣ On Android 13+, check if POST_NOTIFICATIONS was granted :contentReference[oaicite:2]{index=2}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permission = Manifest.permission.POST_NOTIFICATIONS
                if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    // Permission not granted — skip showing the notification or handle gracefully
                    return
                }
            }

            // 2️⃣ Build and dispatch the notification
            try {
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(notif.title)
                    .setContentText(notif.body)
                    .setAutoCancel(true)
                    .build()

                NotificationManagerCompat.from(this)
                    .notify(System.currentTimeMillis().toInt(), notification)

            } catch (secEx: SecurityException) {
                // 3️⃣ As a safety net, handle cases where permissions were revoked at runtime :contentReference[oaicite:3]{index=3}
                secEx.printStackTrace()
            }
        }
    }
}

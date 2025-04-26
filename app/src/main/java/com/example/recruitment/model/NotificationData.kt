package com.example.recruitment.model

data class NotificationData(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "",
    val timestamp: Long = 0L,
    val read: Boolean = false
)

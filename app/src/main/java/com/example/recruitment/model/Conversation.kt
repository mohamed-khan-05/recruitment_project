package com.example.recruitment.model

data class Conversation(
    var id: String = "",
    var participants: List<String> = listOf(),
    var lastMessage: String? = null,
    var lastTimestamp: Long? = 0L,
    var unreadMessagesCount: Map<String, Long>? = null
)

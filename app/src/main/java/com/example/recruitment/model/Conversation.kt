package com.example.recruitment.model

data class Conversation(
    var id: String = "", // Needed for document ID
    var participants: List<String> = listOf(),
    var lastMessage: String? = null,
    var lastTimestamp: Long? = 0L
)

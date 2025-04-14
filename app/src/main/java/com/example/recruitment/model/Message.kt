package com.example.recruitment.model

class Message {
    var senderEmail: String? = null
    var message: String? = null
    var timestamp: Long = 0L
    var status: String? = null

    constructor()

    constructor(
        senderEmail: String?,
        message: String?,
        timestamp: Long,
        status: String?
    ) {
        this.senderEmail = senderEmail
        this.message = message
        this.timestamp = timestamp
        this.status = status
    }
}
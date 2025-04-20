package com.example.recruitment.model

data class ApplicationData(
    val jobId: String = "",
    val jobTitle: String = "",
    val companyName: String = "",
    val status: String = "",
    val appliedAt: Long = 0L,
    val decisionAt: Long = 0L
)

// com.example.recruitment.model.Job.kt
package com.example.recruitment.model

data class Job(
    val id: String,
    val title: String,
    val description: String,
    val experienceLevel: String,
    val workArrangement: String,
    val timestamp: Long = 0L,
    val employerEmail: String = "",
    val status: String = "",
    val applicants: List<String> = listOf()
)

// com/example/recruitment/model/Applicant.kt
package com.example.recruitment.model

data class Applicant(
    val applicationId: String,
    val email: String,
    val fullName: String = "",
    val keywords: List<String> = emptyList(),
)

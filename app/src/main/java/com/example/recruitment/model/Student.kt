package com.example.recruitment.model

data class Student(
    val uid: String,
    val fullName: String,
    val email: String,
    val jobTitle: String = "",
    val keywords: List<String>
)

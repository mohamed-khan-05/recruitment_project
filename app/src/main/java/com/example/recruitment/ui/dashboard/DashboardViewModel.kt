package com.example.recruitment.ui.dashboard

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DashboardViewModel : ViewModel() {
    private val _applicationViews = MutableLiveData<Int>()
    val applicationViews: LiveData<Int> get() = _applicationViews

    private val _totalApplications = MutableLiveData<Int>()
    val totalApplications: LiveData<Int> get() = _totalApplications

    init {
        fetchApplicationViews()
        fetchTotalApplications()
    }

    private fun fetchApplicationViews() {
        val userEmail = FirebaseAuth.getInstance().currentUser?.email
        if (userEmail != null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("applicationViews") // Change to your actual Firestore collection name
                .whereEqualTo(
                    "email",
                    userEmail
                )
                .get()
                .addOnSuccessListener { querySnapshot ->
                    // Count the number of views for this user in the applicationViews collection
                    _applicationViews.value = querySnapshot.size()
                }
                .addOnFailureListener { exception ->
                    Log.e("DashboardViewModel", "Error fetching application views: ", exception)
                }
        } else {
            Log.e("DashboardViewModel", "User is not logged in.")
        }
    }

    fun fetchTotalApplications() {
        val userEmail = FirebaseAuth.getInstance().currentUser?.email
        if (userEmail != null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("jobs")
                .whereEqualTo("employerEmail", userEmail)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    _totalApplications.value = querySnapshot.size()
                }
                .addOnFailureListener { exception ->
                    Log.e("DashboardViewModel", "Error fetching applications: ", exception)
                }
        } else {
            Log.e("DashboardViewModel", "User is not logged in.")
        }
    }
}

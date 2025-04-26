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
        fetchTotApplicants()
        fetchTotalApplications()
    }

    private fun fetchTotApplicants() {
        val userEmail = FirebaseAuth.getInstance().currentUser?.email
        if (userEmail != null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("jobs")
                .whereEqualTo("employerEmail", userEmail)
                .whereEqualTo("status", "open")
                .get()
                .addOnSuccessListener { jobsSnapshot ->
                    var pendingCount = 0
                    var jobsProcessed = 0

                    if (jobsSnapshot.isEmpty) {
                        _applicationViews.value = 0
                        return@addOnSuccessListener
                    }

                    jobsSnapshot.documents.forEach { jobDoc ->
                        db.collection("jobs")
                            .document(jobDoc.id)
                            .collection("applications")
                            .whereEqualTo("status", "pending")
                            .get()
                            .addOnSuccessListener { appsSnap ->
                                pendingCount += appsSnap.size()
                                jobsProcessed++

                                // update only after all jobs processed
                                if (jobsProcessed == jobsSnapshot.size()) {
                                    _applicationViews.value = pendingCount
                                }
                            }
                            .addOnFailureListener { e ->
                                jobsProcessed++
                                if (jobsProcessed == jobsSnapshot.size()) {
                                    _applicationViews.value = pendingCount
                                }
                                Log.e("DashboardViewModel", "Error fetching applications: ", e)
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("DashboardViewModel", "Error fetching jobs: ", exception)
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
                .whereEqualTo("status", "open")
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

    fun refreshData() {
        fetchTotApplicants()
        fetchTotalApplications()
    }
}

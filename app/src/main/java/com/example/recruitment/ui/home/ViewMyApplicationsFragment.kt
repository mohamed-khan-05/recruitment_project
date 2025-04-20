package com.example.recruitment.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recruitment.databinding.FragmentViewMyApplicationsBinding
import com.example.recruitment.model.ApplicationData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class ViewMyApplicationsFragment : Fragment() {

    private var _binding: FragmentViewMyApplicationsBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val currentEmail = FirebaseAuth.getInstance().currentUser?.email
    private val applicationsList = mutableListOf<ApplicationData>()
    private lateinit var adapter: ApplicationsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewMyApplicationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ApplicationsAdapter(applicationsList)
        binding.recyclerViewApplications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewApplications.adapter = adapter

        fetchApplications()
    }

    private fun fetchApplications() {
        currentEmail?.let { email ->
            db.collection("jobs")
                .get()
                .addOnSuccessListener { jobsSnapshot ->
                    applicationsList.clear()

                    val jobs = jobsSnapshot.documents
                    if (jobs.isEmpty()) {
                        adapter.notifyDataSetChanged()
                        return@addOnSuccessListener
                    }

                    var remainingJobs = jobs.size

                    for (jobDoc in jobs) {
                        val jobId = jobDoc.id
                        val title = jobDoc.getString("title") ?: "No Title"
                        val employerEmail = jobDoc.getString("employerEmail") ?: "No Company"

                        db.collection("jobs")
                            .document(jobId)
                            .collection("applications")
                            .whereEqualTo("email", email)
                            .get()
                            .addOnSuccessListener { appSnapshot ->
                                for (appDoc in appSnapshot) {
                                    val status = appDoc.getString("status") ?: "pending"
                                    val appliedAt = appDoc.getLong("appliedAt") ?: 0L
                                    val decisionAt = appDoc.getLong("decisionAt") ?: 0L

                                    val application = ApplicationData(
                                        jobId = jobId,
                                        jobTitle = title,
                                        companyName = employerEmail,
                                        status = status,
                                        appliedAt = appliedAt,
                                        decisionAt = decisionAt
                                    )

                                    applicationsList.add(application)
                                }

                                remainingJobs--
                                if (remainingJobs == 0) {
                                    adapter.notifyDataSetChanged()
                                }
                            }
                            .addOnFailureListener {
                                remainingJobs--
                                if (remainingJobs == 0) {
                                    adapter.notifyDataSetChanged()
                                }
                            }
                    }
                }
                .addOnFailureListener {
                    adapter.notifyDataSetChanged()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

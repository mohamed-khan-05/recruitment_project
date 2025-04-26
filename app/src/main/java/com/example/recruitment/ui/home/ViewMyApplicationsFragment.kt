package com.example.recruitment.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recruitment.databinding.FragmentViewMyApplicationsBinding
import com.example.recruitment.model.ApplicationData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ViewMyApplicationsFragment : Fragment() {

    private var _binding: FragmentViewMyApplicationsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val currentEmail = FirebaseAuth.getInstance().currentUser?.email

    // raw list and filtered list
    private val allApplications = mutableListOf<ApplicationData>()
    private val visibleApplications = mutableListOf<ApplicationData>()

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

        adapter = ApplicationsAdapter(visibleApplications)
        binding.recyclerViewApplications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewApplications.adapter = adapter

        // set up checkbox listeners
        listOf(
            binding.cbPending,
            binding.cbAccepted,
            binding.cbRejected
        ).forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ ->
                applyFilter()
            }
        }

        fetchApplications()
    }

    private fun fetchApplications() {
        currentEmail?.let { email ->
            db.collection("jobs")
                .get()
                .addOnSuccessListener { jobsSnapshot ->
                    allApplications.clear()

                    val jobs = jobsSnapshot.documents
                    if (jobs.isEmpty()) {
                        applyFilter()
                        return@addOnSuccessListener
                    }

                    var remaining = jobs.size
                    for (job in jobs) {
                        val jobId = job.id
                        val title = job.getString("title") ?: "No Title"
                        val employerEmail = job.getString("employerEmail") ?: "No Company"

                        db.collection("jobs")
                            .document(jobId)
                            .collection("applications")
                            .whereEqualTo("email", email)
                            .get()
                            .addOnSuccessListener { apps ->
                                for (appDoc in apps) {
                                    val status = appDoc.getString("status") ?: "pending"
                                    val appliedAt = appDoc.getLong("appliedAt") ?: 0L
                                    val decisionAt = appDoc.getLong("decisionAt") ?: 0L

                                    allApplications.add(
                                        ApplicationData(
                                            jobId = jobId,
                                            jobTitle = title,
                                            companyName = employerEmail,
                                            status = status,
                                            appliedAt = appliedAt,
                                            decisionAt = decisionAt
                                        )
                                    )
                                }
                                if (--remaining == 0) {
                                    applyFilter()
                                }
                            }
                            .addOnFailureListener {
                                if (--remaining == 0) {
                                    applyFilter()
                                }
                            }
                    }
                }
                .addOnFailureListener {
                    applyFilter()
                }
        }
    }

    private fun applyFilter() {
        // gather which statuses are checked
        val selectedStatuses = mutableSetOf<String>().apply {
            if (binding.cbPending.isChecked) add("pending")
            if (binding.cbAccepted.isChecked) add("accepted")
            if (binding.cbRejected.isChecked) add("rejected")
        }

        // rebuild visibleApplications
        visibleApplications.clear()
        visibleApplications.addAll(
            allApplications.filter { it.status in selectedStatuses }
        )
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.example.recruitment.ui.jobs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recruitment.R
import com.example.recruitment.databinding.FragmentMyJobsBinding
import com.example.recruitment.ui.dashboard.DashboardViewModel
import com.example.recruitment.ui.dialogs.JobDescriptionDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MyJobsFragment : Fragment() {

    private val dashboardViewModel: DashboardViewModel by activityViewModels()
    private var _binding: FragmentMyJobsBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: MyJobsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyJobsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MyJobsAdapter(
            onJobClick = { _, title, description, experienceLevel, workArrangement, timestamp ->
                showJobDescriptionDialog(
                    title,
                    description,
                    experienceLevel,
                    workArrangement,
                    timestamp
                )
            },
            onDeleteClicked = { jobId ->
                confirmDelete(jobId)
            },
            onViewApplicantsClicked = { jobId ->
                val bundle = Bundle().apply {
                    putString("arg_job_id", jobId) // Must match the key in ApplicantsFragment
                }
                findNavController().navigate(R.id.navigation_applicants, bundle)

            }
        )

        binding.recyclerJobs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerJobs.adapter = adapter

        loadJobs()
    }

    private fun loadJobs() {
        val email = auth.currentUser?.email ?: return
        db.collection("jobs")
            .whereEqualTo("employerEmail", email)
            .whereEqualTo("status", "open")
            .get()
            .addOnSuccessListener { result ->
                val jobs = result.map { document ->
                    val title = document.getString("title").orEmpty()
                    val description = document.getString("description").orEmpty()
                    val experienceLevel = document.getString("experienceLevel").orEmpty()
                    val workArrangement = document.getString("workArrangement").orEmpty()
                    val timestamp = document.getLong("timestamp") ?: 0L
                    document.id to listOf(
                        title,
                        description,
                        experienceLevel,
                        workArrangement,
                        timestamp
                    )
                }
                if (jobs.isEmpty()) {
                    Toast.makeText(requireContext(), "No jobs found", Toast.LENGTH_SHORT).show()
                } else {
                    adapter.submitList(jobs)
                }
            }
    }

    private fun showJobDescriptionDialog(
        title: String,
        description: String,
        experienceLevel: String,
        workArrangement: String,
        timestamp: Long
    ) {
        val dialog = JobDescriptionDialogFragment.newInstance(
            title,
            description,
            experienceLevel,
            workArrangement,
            timestamp
        )
        dialog.show(parentFragmentManager, "JobDescriptionDialog")
    }

    private fun confirmDelete(jobId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Job")
            .setMessage("Are you sure you want to delete this job?")
            .setPositiveButton("Delete") { _, _ -> deleteJob(jobId) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteJob(jobId: String) {
        val updates = hashMapOf<String, Any>(
            "status" to "closed"
        )

        db.collection("jobs").document(jobId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Job closed", Toast.LENGTH_SHORT).show()
                loadJobs()
                dashboardViewModel.fetchTotalApplications() // Refresh dashboard count
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to close job", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

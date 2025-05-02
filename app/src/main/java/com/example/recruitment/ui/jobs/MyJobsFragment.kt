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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FragmentMyJobsBinding.inflate(inflater, container, false).also {
        _binding = it
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MyJobsAdapter(
            onJobClick = { _, title, desc, expLvl, workArr, ts ->
                JobDescriptionDialogFragment
                    .newInstance(title, desc, expLvl, workArr, ts)
                    .show(parentFragmentManager, "JobDescriptionDialog")
            },
            onDeleteClicked = { jobId -> confirmDelete(jobId) },
            onViewApplicantsClicked = { jobId ->
                findNavController()
                    .navigate(
                        R.id.navigation_applicants,
                        Bundle().apply { putString("arg_job_id", jobId) }
                    )
            }
        )

        binding.recyclerJobs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerJobs.adapter = adapter

        binding.cbOpen.setOnCheckedChangeListener { _, _ -> loadJobs() }
        binding.cbClosed.setOnCheckedChangeListener { _, _ -> loadJobs() }

        loadJobs()  // initial load
    }

    private fun loadJobs() {
        val email = auth.currentUser?.email ?: return

        val statuses = mutableListOf<String>().apply {
            if (binding.cbOpen.isChecked) add("open")
            if (binding.cbClosed.isChecked) add("closed")
        }

        if (statuses.isEmpty()) {
            adapter.submitList(emptyList())
            Toast.makeText(
                requireContext(),
                "Please select at least one status filter.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        var query = db.collection("jobs")
            .whereEqualTo("employerEmail", email)

        query = if (statuses.size == 1) {
            query.whereEqualTo("status", statuses[0])
        } else {
            query.whereIn("status", statuses)
        }

        query.get()
            .addOnSuccessListener { result ->
                val jobs = result.map { doc ->
                    val title = doc.getString("title").orEmpty()
                    val description = doc.getString("description").orEmpty()
                    val experienceLevel = doc.getString("experienceLevel").orEmpty()
                    val workArrangement = doc.getString("workArrangement").orEmpty()
                    val status = doc.getString("status").orEmpty()
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    doc.id to listOf(
                        title,
                        description,
                        experienceLevel,
                        workArrangement,
                        status,
                        timestamp
                    )
                }
                if (jobs.isEmpty()) {
                    Toast.makeText(requireContext(), "No jobs found", Toast.LENGTH_SHORT).show()
                }
                adapter.submitList(jobs)
            }
            .addOnFailureListener {
                Toast.makeText(
                    requireContext(),
                    "Failed to load jobs: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun confirmDelete(jobId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Job")
            .setMessage("Are you sure you want to close this job?")
            .setPositiveButton("Yes") { _, _ -> deleteJob(jobId) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun deleteJob(jobId: String) {
        db.collection("jobs").document(jobId)
            .update("status", "closed")
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Job closed", Toast.LENGTH_SHORT).show()
                loadJobs()
                dashboardViewModel.fetchTotalApplications()
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

package com.example.recruitment.ui.jobs

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recruitment.R
import com.example.recruitment.databinding.FragmentJobsBinding
import com.example.recruitment.model.Job
import com.example.recruitment.ui.dialogs.JobFilterDialogFragment
import com.example.recruitment.ui.dialogs.StudentJobDescriptionDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class JobsFragment : Fragment() {
    private var _binding: FragmentJobsBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid
    private lateinit var adapter: StudentJobsAdapter
    private var allSearchedJobs: List<Job> = emptyList()
    private var displayedSearchedCount = 0
    private var userKeywords: List<String> = emptyList()
    private var selectedWorkArrangement: String? = null
    private var selectedJobType: String? = null
    private var currentSearchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJobsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = StudentJobsAdapter(
            onJobClick = { job ->
                ViewedJob(job)
                StudentJobDescriptionDialogFragment.newInstance(
                    job.id,
                    job.title,
                    job.description,
                    job.experienceLevel,
                    job.workArrangement,
                    job.timestamp
                ).show(childFragmentManager, "JobDescriptionDialog")
            },
            onChatClick = { job ->
                val bundle = Bundle().apply {
                    putString("employerEmail", job.employerEmail)
                    putString("jobId", job.id)
                }
                createOrNavigateToChat(job, bundle)
            }
        )
        binding.recyclerJobs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerJobs.adapter = adapter
        binding.etSearch.addTextChangedListener {
            currentSearchQuery = it.toString().trim()
            if (currentSearchQuery.isEmpty()) loadSuggestedJobs() else loadSearchedJobs(reset = true)
        }
        binding.btnFilter.setOnClickListener {
            JobFilterDialogFragment
                .newInstance(selectedWorkArrangement, selectedJobType)
                .show(parentFragmentManager, "job_filter")
        }
        parentFragmentManager.setFragmentResultListener(
            "job_filter_result",
            viewLifecycleOwner
        ) { _, bundle ->
            selectedWorkArrangement = bundle.getString("workArrangement")
            selectedJobType = bundle.getString("jobType")
            if (currentSearchQuery.isEmpty()) {
                loadSuggestedJobs()
            } else {
                loadSearchedJobs(reset = true)
            }
        }



        binding.btnLoadMore.setOnClickListener {
            if (binding.btnLoadMore.isEnabled) loadSearchedJobs(reset = false)
        }

        setFragmentResultListener("job_filter_result") { _, bundle ->
            selectedWorkArrangement = bundle.getString("workArrangement")
            selectedJobType = bundle.getString("jobType")
            if (currentSearchQuery.isEmpty()) loadSuggestedJobs() else loadSearchedJobs(reset = true)
        }

        fetchUserKeywords()
    }

    private fun ViewedJob(job: Job) {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return

        val db = FirebaseFirestore.getInstance()
        val userDocRef = db.collection("Views")
            .document(job.id)
            .collection("Users")
            .document(currentUserEmail)

        userDocRef.get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    userDocRef.set(emptyMap<String, Any>())
                        .addOnSuccessListener {
                            Log.d(
                                "ViewedJob",
                                "User $currentUserEmail added to job ${job.id} views"
                            )
                        }
                        .addOnFailureListener { e ->
                            Log.e("ViewedJob", "Error adding view", e)
                        }
                } else {
                    Log.d("ViewedJob", "User $currentUserEmail already viewed job ${job.id}")
                }
            }
            .addOnFailureListener { e ->
                Log.e("ViewedJob", "Error checking if view exists", e)
            }
    }

    private fun createOrNavigateToChat(job: Job, bundle: Bundle) {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
        db.collection("conversations")
            .whereArrayContains("participants", currentUserEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                val existing = snapshot.documents.firstOrNull { doc ->
                    (doc.get("participants") as? List<*>)?.contains(job.employerEmail) == true
                }
                if (existing != null) {
                    bundle.putString("chatId", existing.id)
                    findNavController().navigate(R.id.action_jobs_to_chat, bundle)
                } else {
                    val convData = mapOf(
                        "participants" to listOf(currentUserEmail, job.employerEmail),
                        "lastTimestamp" to System.currentTimeMillis(),
                        "lastMessage" to "",
                        "unreadMessagesCount" to mapOf(
                            currentUserEmail to 0,
                            job.employerEmail to 0
                        )
                    )
                    db.collection("conversations")
                        .add(convData)
                        .addOnSuccessListener { newDoc ->
                            bundle.putString("chatId", newDoc.id)
                            findNavController().navigate(R.id.action_jobs_to_chat, bundle)
                        }
                        .addOnFailureListener { e -> Log.e("JobsFragment", e.message ?: "") }
                }
            }
            .addOnFailureListener { e -> Log.e("JobsFragment", e.message ?: "") }
    }

    private fun fetchUserKeywords() {
        val uid = uid ?: return
        db.collection("users").document(uid)
            .collection("keywords").document("fromCv")
            .get()
            .addOnSuccessListener { doc ->
                userKeywords = doc.get("keywords") as? List<String> ?: emptyList()
                loadSuggestedJobs()
            }
            .addOnFailureListener { e -> Log.e("JobsFragment", e.message ?: "") }
    }

    private fun loadSuggestedJobs() {
        db.collection("jobs").whereEqualTo("status", "open").get()
            .addOnSuccessListener { snapshot ->
                val suggestedJobs = snapshot.documents.mapNotNull { doc ->
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val description = doc.getString("description") ?: ""
                    val experienceLevel = doc.getString("experienceLevel") ?: ""
                    val workArrangement = doc.getString("workArrangement") ?: ""
                    val combined =
                        "$title $description $experienceLevel $workArrangement".lowercase()
                    val matches = userKeywords.any { combined.contains(it.lowercase()) }
                    if (matches) Job(
                        doc.id,
                        title,
                        description,
                        experienceLevel,
                        workArrangement,
                        doc.getLong("timestamp") ?: 0L,
                        doc.getString("employerEmail") ?: return@mapNotNull null
                    ) else null
                }.take(10)
                adapter.submitList(suggestedJobs)
                binding.btnLoadMore.isEnabled = false
                binding.btnLoadMore.alpha = 0.5f
            }
            .addOnFailureListener { e -> Log.e("JobsFragment", e.message ?: "") }
    }

    private fun loadSearchedJobs(reset: Boolean) {
        displayedSearchedCount = if (reset) {
            10
        } else {
            displayedSearchedCount + 10
        }

        val queryLower = currentSearchQuery.trim().lowercase()

        db.collection("jobs")
            .whereEqualTo("status", "open")
            .get()
            .addOnSuccessListener { snapshot ->
                if (reset) {
                    allSearchedJobs = snapshot.documents.mapNotNull { doc ->
                        val title = doc.getString("title") ?: return@mapNotNull null
                        val description = doc.getString("description") ?: ""
                        val experienceLevel = doc.getString("experienceLevel") ?: ""
                        val workArrangement = doc.getString("workArrangement") ?: ""
                        val matchesSearch = title.lowercase().contains(queryLower)
                                || description.lowercase().contains(queryLower)
                                || experienceLevel.lowercase().contains(queryLower)
                                || workArrangement.lowercase().contains(queryLower)
                        val matchesWork =
                            selectedWorkArrangement?.equals(workArrangement, ignoreCase = true)
                                ?: true
                        val matchesType =
                            selectedJobType?.equals(experienceLevel, ignoreCase = true) ?: true

                        if (matchesSearch && matchesWork && matchesType) {
                            Job(
                                id = doc.id,
                                title = title,
                                description = description,
                                experienceLevel = experienceLevel,
                                workArrangement = workArrangement,
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                employerEmail = doc.getString("employerEmail")
                                    ?: return@mapNotNull null
                            )
                        } else null
                    }
                }
                val toDisplay = allSearchedJobs.take(displayedSearchedCount)
                adapter.submitList(toDisplay)
                val canLoadMore = displayedSearchedCount < allSearchedJobs.size
                binding.btnLoadMore.isEnabled = canLoadMore
                binding.btnLoadMore.alpha = if (canLoadMore) 1f else 0.5f
            }
            .addOnFailureListener { e ->
                Log.e("JobsFragment", e.message ?: "Error loading searched jobs")
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

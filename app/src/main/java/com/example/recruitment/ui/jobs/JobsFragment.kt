package com.example.recruitment.ui.jobs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recruitment.databinding.FragmentJobsBinding
import com.example.recruitment.model.Job
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import androidx.navigation.fragment.findNavController
import com.example.recruitment.R
import com.example.recruitment.ui.chat.ChatFragment
import com.example.recruitment.model.Chat
import com.example.recruitment.ui.dialogs.StudentJobDescriptionDialogFragment
import com.google.firebase.firestore.DocumentSnapshot

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
    private var lastVisibleDocument: DocumentSnapshot? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentJobsBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = StudentJobsAdapter(
            onJobClick = { job ->
                val dialog = StudentJobDescriptionDialogFragment.newInstance(
                    jobId = job.id,
                    title = job.title,
                    description = job.description,
                    experienceLevel = job.experienceLevel,
                    workArrangement = job.workArrangement,
                    timestamp = job.timestamp
                )
                dialog.show(childFragmentManager, "JobDescriptionDialog")
            },
            onChatClick = { job ->
                val bundle = Bundle().apply {
                    putString("employerEmail", job.employerEmail)
                    putString("jobId", job.id)
                }
                createOrNavigateToChat(job, bundle)
            }
        )
        binding.recyclerJobs.adapter = adapter
        binding.recyclerJobs.layoutManager = LinearLayoutManager(requireContext())
        binding.btnLoadMore.setOnClickListener {
            if (binding.btnLoadMore.isEnabled) {
                loadSearchedJobs(reset = false)
            }
        }
        binding.etSearch.addTextChangedListener { editable ->
            currentSearchQuery = editable.toString().trim()
            if (currentSearchQuery.isEmpty()) {
                loadSuggestedJobs()
            } else {
                loadSearchedJobs(reset = true)
            }
        }
        binding.btnFilter.setOnClickListener {
            com.example.recruitment.ui.dialogs.JobFilterDialogFragment()
                .show(parentFragmentManager, "JobFilterDialog")
        }
        setFragmentResultListener("job_filter_result") { _, bundle ->
            selectedWorkArrangement = bundle.getString("workArrangement")
            selectedJobType = bundle.getString("jobType")
            if (currentSearchQuery.isEmpty()) {
                loadSuggestedJobs()
            } else {
                loadSearchedJobs(reset = true)
            }
        }

        fetchUserKeywords()
    }

    private fun createOrNavigateToChat(job: Job, bundle: Bundle) {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
            ?: run {
                Log.e("JobsFragment", "No logged‑in user email")
                return
            }
        db.collection("conversations")
            .whereArrayContains("participants", currentUserEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                val existing = snapshot.documents.firstOrNull { doc ->
                    val participants = doc.get("participants") as? List<*>
                    participants?.contains(job.employerEmail) == true
                }
                Log.d("JobsFragment", "Navigating to chat with ID: ${bundle.getString("chatId")}")

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
                        .addOnFailureListener { e ->
                            Log.e("JobsFragment", "Failed to create conversation: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("JobsFragment", "Error fetching conversations: ${e.message}")
            }
    }


    private fun fetchUserKeywords() {
        if (uid == null) return

        db.collection("users").document(uid)
            .collection("keywords").document("fromCv")
            .get()
            .addOnSuccessListener { document ->
                userKeywords = document.get("keywords") as? List<String> ?: emptyList()
                Log.d("JobsFragment", "User keywords from Firestore: $userKeywords")
                loadSuggestedJobs()
            }
            .addOnFailureListener { exception ->
                Log.e("JobsFragment", "Failed to fetch keywords: ${exception.message}")
            }
    }

    private fun loadSuggestedJobs() {
        db.collection("jobs").whereEqualTo("status", "open").get()
            .addOnSuccessListener { snapshot ->
                _binding?.let { binding ->
                    val suggestedJobs = snapshot.documents.mapNotNull { doc ->
                        val title = doc.getString("title") ?: return@mapNotNull null
                        val description = doc.getString("description") ?: ""
                        val experienceLevel = doc.getString("experienceLevel") ?: ""
                        val workArrangement = doc.getString("workArrangement") ?: ""
                        val combinedText =
                            "$title $description $experienceLevel $workArrangement".lowercase()
                        val matchesKeywords = userKeywords.any { keyword ->
                            combinedText.contains(keyword.lowercase())
                        }
                        val timestamp = doc.getLong("timestamp") ?: 0L
                        val employerEmail = doc.getString("employerEmail") ?: return@mapNotNull null

                        if (matchesKeywords) {
                            Job(
                                doc.id,
                                title,
                                description,
                                experienceLevel,
                                workArrangement,
                                timestamp,
                                employerEmail
                            )
                        } else null
                    }.take(10)

                    adapter.submitList(suggestedJobs)
                    binding.btnLoadMore.apply {
                        isEnabled = false
                        alpha = 0.5f
                    }
                } ?: Log.w(
                    "JobsFragment",
                    "View binding is null; skipping UI update in loadSuggestedJobs."
                )
            }
            .addOnFailureListener { exception ->
                Log.e("JobsFragment", "Failed to load suggested jobs: ${exception.message}")
            }
    }

    private fun loadSearchedJobs(reset: Boolean = true) {
        if (reset) {
            displayedSearchedCount = 10
        } else {
            displayedSearchedCount += 10
        }

        val searchQueryLower = currentSearchQuery.lowercase()

        db.collection("jobs").whereEqualTo("status", "open").get()
            .addOnSuccessListener { snapshot ->
                _binding?.let { binding ->
                    if (reset) {
                        allSearchedJobs = snapshot.documents.mapNotNull { doc ->
                            val title = doc.getString("title") ?: return@mapNotNull null
                            val description = doc.getString("description") ?: ""
                            val experienceLevel = doc.getString("experienceLevel") ?: ""
                            val workArrangement = doc.getString("workArrangement") ?: ""
                            val timestamp = doc.getLong("timestamp") ?: 0L
                            val employerEmail =
                                doc.getString("employerEmail") ?: return@mapNotNull null

                            val matchesSearch = title.lowercase().contains(searchQueryLower) ||
                                    description.lowercase().contains(searchQueryLower) ||
                                    experienceLevel.lowercase().contains(searchQueryLower) ||
                                    workArrangement.lowercase().contains(searchQueryLower)

                            if (matchesSearch) {
                                Job(
                                    doc.id,
                                    title,
                                    description,
                                    experienceLevel,
                                    workArrangement,
                                    timestamp,
                                    employerEmail
                                )
                            } else null
                        }
                    }

                    val jobsToDisplay = allSearchedJobs.take(displayedSearchedCount)
                    adapter.submitList(jobsToDisplay)

                    if (displayedSearchedCount < allSearchedJobs.size) {
                        binding.btnLoadMore.apply {
                            isEnabled = true
                            alpha = 1.0f
                        }
                    } else {
                        binding.btnLoadMore.apply {
                            isEnabled = false
                            alpha = 0.5f
                        }
                    }
                } ?: Log.w(
                    "JobsFragment",
                    "View binding is null; skipping UI update in loadSearchedJobs."
                )
            }
            .addOnFailureListener { exception ->
                Log.e("JobsFragment", "Error fetching jobs: ${exception.message}")
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

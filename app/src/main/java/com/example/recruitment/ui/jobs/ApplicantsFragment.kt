package com.example.recruitment.ui.jobs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recruitment.R
import com.example.recruitment.databinding.FragmentApplicantsBinding
import com.example.recruitment.model.Applicant
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ApplicantsFragment : Fragment() {
    private var _binding: FragmentApplicantsBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private var jobId: String? = null
    private lateinit var adapter: ApplicantsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        jobId = arguments?.getString("arg_job_id") // 🔧 fixed key
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApplicantsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val jid = jobId
        if (jid.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Invalid job ID", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        adapter = ApplicantsAdapter(jid) { applicant ->
            startChat(applicant)
        }

        binding.recyclerApplicants.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerApplicants.adapter = adapter

        loadApplicants(jid, listOf("pending")) // default status

        // Checkbox status filters
        val checkboxes = listOf(binding.checkPending, binding.checkAccepted, binding.checkRejected)
        checkboxes.forEach {
            it.setOnCheckedChangeListener { _, _ ->
                val selectedStatuses = mutableListOf<String>()
                if (binding.checkPending.isChecked) selectedStatuses.add("pending")
                if (binding.checkAccepted.isChecked) selectedStatuses.add("accepted")
                if (binding.checkRejected.isChecked) selectedStatuses.add("rejected")
                loadApplicants(jid, selectedStatuses)
            }
        }
    }

    private fun loadApplicants(jid: String, statuses: List<String>) {
        db.collection("jobs")
            .document(jid)
            .collection("applications")
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    Toast.makeText(requireContext(), "No applicants yet", Toast.LENGTH_SHORT).show()
                    adapter.submitList(emptyList())
                    updateStatusCounts(0, 0, 0) // Update counts to zero
                    return@addOnSuccessListener
                }

                // Filter applicants based on the selected statuses
                val filteredApps = snap.documents.filter {
                    statuses.contains(it.getString("status"))
                }

                // Count the number of applicants for each status
                val statusCounts = snap.documents.groupingBy {
                    it.getString("status") ?: "unknown"
                }.eachCount()

                updateStatusCounts(
                    statusCounts["pending"] ?: 0,
                    statusCounts["accepted"] ?: 0,
                    statusCounts["rejected"] ?: 0
                )

                val tempList = mutableListOf<Applicant>()
                var loadedCount = 0
                val totalCount = filteredApps.size

                filteredApps.forEach { appDoc ->
                    val email = appDoc.getString("email").orEmpty()
                    val appId = appDoc.id

                    db.collection("users")
                        .whereEqualTo("email", email)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { userSnap ->
                            val userDoc = userSnap.documents.firstOrNull()
                            val fullName = userDoc?.getString("fullName").orEmpty()
                            val userId = userDoc?.id

                            if (userId != null) {
                                db.collection("users")
                                    .document(userId)
                                    .collection("keywords")
                                    .document("fromCv")
                                    .get()
                                    .addOnSuccessListener { kwDoc ->
                                        val kws =
                                            kwDoc.get("keywords") as? List<String> ?: emptyList()
                                        tempList += Applicant(appId, email, fullName, kws)
                                        if (++loadedCount == totalCount) adapter.submitList(tempList)
                                    }
                                    .addOnFailureListener {
                                        tempList += Applicant(appId, email, fullName, emptyList())
                                        if (++loadedCount == totalCount) adapter.submitList(tempList)
                                    }
                            } else {
                                tempList += Applicant(appId, email, fullName, emptyList())
                                if (++loadedCount == totalCount) adapter.submitList(tempList)
                            }
                        }
                        .addOnFailureListener {
                            tempList += Applicant(appId, email, "", emptyList())
                            if (++loadedCount == totalCount) adapter.submitList(tempList)
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load applicants", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun updateStatusCounts(pending: Int, accepted: Int, rejected: Int) {
        binding.tvPendingCount.text = "Pending: $pending"
        binding.tvAcceptedCount.text = "Accepted: $accepted"
        binding.tvRejectedCount.text = "Rejected: $rejected"
    }


    private fun startChat(applicant: Applicant) {
        val employerEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
        val bundle = Bundle().apply {
            putString("applicantEmail", applicant.email)
            putString("applicantId", applicant.applicationId)
        }

        db.collection("conversations")
            .whereArrayContains("participants", employerEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                val existing = snapshot.documents.firstOrNull { doc ->
                    val participants = doc.get("participants") as? List<*>
                    participants?.contains(applicant.email) == true
                }

                if (existing != null) {
                    bundle.putString("chatId", existing.id)
                    findNavController().navigate(
                        R.id.action_my_applicants_to_navigation_chat,
                        bundle
                    )
                } else {
                    val newConversationData = mapOf(
                        "participants" to listOf(employerEmail, applicant.email),
                        "lastTimestamp" to System.currentTimeMillis(),
                        "lastMessage" to "",
                        "unreadMessagesCount" to mapOf(
                            employerEmail to 0,
                            applicant.email to 0
                        )
                    )

                    db.collection("conversations")
                        .add(newConversationData)
                        .addOnSuccessListener { newDoc ->
                            bundle.putString("chatId", newDoc.id)
                            findNavController().navigate(
                                R.id.action_my_applicants_to_navigation_chat,
                                bundle
                            )
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                context,
                                "Failed to create conversation: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    "Error fetching conversations: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

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
        jobId = arguments?.getString("arg_job_id")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
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

        adapter = ApplicantsAdapter(
            jid,
            onChatClick = { applicant -> startChat(applicant) },
            onStatusUpdated = {
                val statuses = mutableListOf<String>()
                if (binding.checkPending.isChecked) statuses += "pending"
                if (binding.checkAccepted.isChecked) statuses += "accepted"
                if (binding.checkRejected.isChecked) statuses += "rejected"
                loadApplicants(jid, statuses)
            }
        )

        binding.recyclerApplicants.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerApplicants.adapter = adapter

        loadApplicants(jid, listOf("pending"))

        listOf(binding.checkPending, binding.checkAccepted, binding.checkRejected).forEach {
            it.setOnCheckedChangeListener { _, _ ->
                val selected = mutableListOf<String>()
                if (binding.checkPending.isChecked) selected += "pending"
                if (binding.checkAccepted.isChecked) selected += "accepted"
                if (binding.checkRejected.isChecked) selected += "rejected"
                loadApplicants(jid, selected)
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
                    adapter.submitList(emptyList())
                    updateStatusCounts(0, 0, 0)
                    return@addOnSuccessListener
                }

                val statusCounts = snap.documents.groupingBy {
                    it.getString("status") ?: "unknown"
                }.eachCount()
                updateStatusCounts(
                    statusCounts["pending"] ?: 0,
                    statusCounts["accepted"] ?: 0,
                    statusCounts["rejected"] ?: 0
                )

                if (statuses.isEmpty()) {
                    adapter.submitList(emptyList())
                    return@addOnSuccessListener
                }

                val filtered = snap.documents.filter { statuses.contains(it.getString("status")) }
                if (filtered.isEmpty()) {
                    adapter.submitList(emptyList())
                    return@addOnSuccessListener
                }

                val temp = mutableListOf<Applicant>()
                var count = 0
                filtered.forEach { doc ->
                    val email = doc.getString("email").orEmpty()
                    val id = doc.id
                    db.collection("users").whereEqualTo("email", email).limit(1).get()
                        .addOnSuccessListener { users ->
                            val user = users.documents.firstOrNull()
                            val name = user?.getString("fullName").orEmpty()
                            val uid = user?.id
                            if (uid != null) {
                                db.collection("users").document(uid)
                                    .collection("keywords").document("fromCv").get()
                                    .addOnSuccessListener { kw ->
                                        val kws = kw.get("keywords") as? List<String> ?: emptyList()
                                        temp += Applicant(id, email, name, kws)
                                        if (++count == filtered.size) adapter.submitList(temp)
                                    }
                                    .addOnFailureListener {
                                        temp += Applicant(id, email, name, emptyList())
                                        if (++count == filtered.size) adapter.submitList(temp)
                                    }
                            } else {
                                temp += Applicant(id, email, name, emptyList())
                                if (++count == filtered.size) adapter.submitList(temp)
                            }
                        }
                        .addOnFailureListener {
                            temp += Applicant(id, email, "", emptyList())
                            if (++count == filtered.size) adapter.submitList(temp)
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
        val email = FirebaseAuth.getInstance().currentUser?.email ?: return
        val bundle = Bundle().apply {
            putString("employerEmail", applicant.email)
            putString("applicantId", applicant.applicationId)
        }
        db.collection("conversations").whereArrayContains("participants", email).get()
            .addOnSuccessListener { snap ->
                val existing = snap.documents.firstOrNull { d ->
                    (d.get("participants") as? List<*>)?.contains(applicant.email) == true
                }
                if (existing != null) {
                    bundle.putString("chatId", existing.id)
                    findNavController().navigate(
                        R.id.action_my_applicants_to_navigation_chat,
                        bundle
                    )
                } else {
                    val data = mapOf(
                        "participants" to listOf(email, applicant.email),
                        "lastTimestamp" to System.currentTimeMillis(),
                        "lastMessage" to "",
                        "unreadMessagesCount" to mapOf(email to 0, applicant.email to 0)
                    )
                    db.collection("conversations").add(data)
                        .addOnSuccessListener { doc ->
                            bundle.putString("chatId", doc.id)
                            findNavController().navigate(
                                R.id.action_my_applicants_to_navigation_chat,
                                bundle
                            )
                        }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
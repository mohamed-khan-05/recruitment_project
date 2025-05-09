package com.example.recruitment.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.recruitment.R
import androidx.fragment.app.DialogFragment
import com.example.recruitment.databinding.FragmentStudentJobDescriptionDialogBinding
import java.text.SimpleDateFormat
import java.util.*
import android.app.AlertDialog
import android.widget.Toast
import com.example.recruitment.model.ApplicationData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentJobDescriptionDialogFragment : DialogFragment() {

    private var _binding: FragmentStudentJobDescriptionDialogBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(
            jobId: String,
            title: String,
            description: String,
            experienceLevel: String,
            workArrangement: String,
            timestamp: Long
        ): StudentJobDescriptionDialogFragment {
            val fragment = StudentJobDescriptionDialogFragment()
            val args = Bundle().apply {
                putString("jobId", jobId)
                putString("title", title)
                putString("description", description)
                putString("experienceLevel", experienceLevel)
                putString("workArrangement", workArrangement)
                putLong("timestamp", timestamp)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentJobDescriptionDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title = arguments?.getString("title") ?: ""
        val description = arguments?.getString("description") ?: ""
        val experienceLevel = arguments?.getString("experienceLevel") ?: ""
        val workArrangement = arguments?.getString("workArrangement") ?: ""
        val timestamp = arguments?.getLong("timestamp") ?: 0L
        val formattedTimestamp = if (timestamp != 0L) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        } else {
            "N/A"
        }

        val jobId = arguments?.getString("jobId") ?: ""
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
        val db = FirebaseFirestore.getInstance()
        val jobRef = db.collection("jobs").document(jobId)
        val appsCollection = jobRef.collection("applications")

        if (currentUserEmail != null) {
            appsCollection
                .whereEqualTo("email", currentUserEmail)
                .get()
                .addOnSuccessListener { query ->
                    val existingApp = query.documents.firstOrNull()
                    val isApplied = existingApp != null
                    val status = existingApp?.getString("status") ?: "pending"

                    // Get timestamps in Long (Unix time in millis)
                    val appliedAt = existingApp?.getLong("appliedAt") ?: 0L
                    val decisionAt = existingApp?.getLong("decisionAt") ?: 0L

                    // Create ApplicationData
                    val appData = ApplicationData(
                        jobId = jobId,
                        jobTitle = title,
                        companyName = "",
                        status = status,
                        appliedAt = appliedAt,
                        decisionAt = decisionAt
                    )

                    // Debug print
                    android.util.Log.d("AppData", "ApplicationData: $appData")

                    when (status) {
                        "accepted" -> {
                            binding.applyButton.apply {
                                text = "Accepted"
                                isEnabled = false
                                setBackgroundColor(requireContext().getColor(android.R.color.holo_green_dark))
                            }
                        }

                        "rejected" -> {
                            binding.applyButton.apply {
                                text = "Rejected"
                                isEnabled = false
                                setBackgroundColor(requireContext().getColor(android.R.color.holo_red_dark))
                            }
                        }

                        else -> {
                            val buttonText = if (isApplied) "Withdraw Application" else "Apply"
                            val buttonColor =
                                if (isApplied) android.R.color.holo_orange_dark else R.color.purple_500

                            binding.applyButton.apply {
                                text = buttonText
                                setBackgroundColor(requireContext().getColor(buttonColor))
                                setOnClickListener {
                                    val action = if (isApplied) "Withdraw" else "Apply"
                                    AlertDialog.Builder(requireContext())
                                        .setTitle("$action Application?")
                                        .setMessage("Do you want to $action your application for this job?")
                                        .setPositiveButton("Yes") { _, _ ->
                                            db.runTransaction { transaction ->
                                                if (isApplied) {
                                                    val appDocRef =
                                                        appsCollection.document(existingApp!!.id)
                                                    transaction.delete(appDocRef)
                                                } else {
                                                    val newAppRef = appsCollection.document()
                                                    val appDataMap = mapOf(
                                                        "email" to currentUserEmail,
                                                        "status" to "pending",
                                                        "appliedAt" to System.currentTimeMillis()
                                                    )
                                                    transaction.set(newAppRef, appDataMap)
                                                }
                                            }.addOnSuccessListener {
                                                val toastMsg =
                                                    if (isApplied) "Application withdrawn." else "Application submitted!"
                                                Toast.makeText(
                                                    requireContext(),
                                                    toastMsg,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onViewCreated(view, savedInstanceState)
                                            }.addOnFailureListener {
                                                Toast.makeText(
                                                    requireContext(),
                                                    "Failed to $action: ${it.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .show()
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        requireContext(),
                        "Failed to check application status",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

        binding.jobTitle.text = title
        binding.jobDescription.text = description
        binding.jobExperience.text = "Experience Level: $experienceLevel"
        binding.jobWorkArrangement.text = "Work Arrangement: $workArrangement"
        binding.jobTimestamp.text = "Posted On: $formattedTimestamp"
        binding.closeButton.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

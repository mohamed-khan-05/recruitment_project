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

        if (currentUserEmail != null) {
            jobRef.get().addOnSuccessListener { snapshot ->
                val applicants = snapshot.get("applicants") as? List<String> ?: emptyList()

                val isApplied = applicants.contains(currentUserEmail)
                val buttonText = if (isApplied) "Withdraw Application" else "Apply"
                val buttonColor =
                    if (isApplied) android.R.color.holo_red_dark else R.color.purple_500

                binding.applyButton.apply {
                    text = buttonText
                    setBackgroundColor(requireContext().getColor(buttonColor))
                    setOnClickListener {
                        val actionMessage = if (isApplied) "Withdraw" else "Apply"
                        val dialogTitle = "$actionMessage Application?"
                        val dialogMessage =
                            "Do you want to $actionMessage your application for this job?"
                        AlertDialog.Builder(requireContext())
                            .setTitle(dialogTitle)
                            .setMessage(dialogMessage)
                            .setPositiveButton("Yes") { _, _ ->
                                db.runTransaction { transaction ->
                                    val jobSnapshot = transaction.get(jobRef)
                                    val currentApplicants =
                                        jobSnapshot.get("applicants") as? List<String> ?: listOf()
                                    val updatedApplicants = if (isApplied) {
                                        currentApplicants.filter { it != currentUserEmail }
                                    } else {
                                        currentApplicants + currentUserEmail
                                    }
                                    transaction.update(jobRef, "applicants", updatedApplicants)
                                }.addOnSuccessListener {
                                    val toastMessage =
                                        if (isApplied) "Application withdrawn." else "Application submitted!"
                                    Toast.makeText(
                                        requireContext(),
                                        toastMessage,
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    // Reset to the opposite state after action
                                    binding.applyButton.apply {
                                        text = if (isApplied) "Apply" else "Withdraw Application"
                                        setBackgroundColor(requireContext().getColor(if (isApplied) R.color.purple_500 else android.R.color.holo_red_dark))
                                        setOnClickListener {
                                            onViewCreated(
                                                view,
                                                savedInstanceState
                                            )
                                        } // Refresh button state
                                    }
                                }.addOnFailureListener {
                                    Toast.makeText(
                                        requireContext(),
                                        "Failed to $actionMessage: ${it.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }.addOnFailureListener {
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

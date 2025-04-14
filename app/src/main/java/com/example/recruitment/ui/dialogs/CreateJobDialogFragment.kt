package com.example.recruitment.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.example.recruitment.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

interface OnJobCreatedListener {
    fun onJobCreated()
}

class CreateJobDialogFragment : DialogFragment() {
    var jobCreatedListener: OnJobCreatedListener? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val experienceLevels = listOf(
        "Internship",
        "Entry Level",
        "Associate",
        "Mid-senior Level",
        "Director",
        "Executive"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_create_job, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val titleField = view.findViewById<EditText>(R.id.et_job_title)
        val descriptionField = view.findViewById<EditText>(R.id.et_job_description)
        val spinnerExperience = view.findViewById<Spinner>(R.id.spinner_experience_level)
        val cbOnSite = view.findViewById<CheckBox>(R.id.cb_on_site)
        val cbRemote = view.findViewById<CheckBox>(R.id.cb_remote)
        val cbHybrid = view.findViewById<CheckBox>(R.id.cb_hybrid)
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, experienceLevels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerExperience.adapter = adapter
        val workArrangementCheckBoxes = listOf(cbOnSite, cbRemote, cbHybrid)
        workArrangementCheckBoxes.forEach { checkBox ->
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    workArrangementCheckBoxes.filter { it != checkBox }
                        .forEach { it.isChecked = false }
                }
            }
        }
        view.findViewById<Button>(R.id.button_submit).setOnClickListener {
            val title = titleField.text.toString().trim()
            val description = descriptionField.text.toString().trim()
            val email = auth.currentUser?.email ?: "unknown"
            val experienceLevel = spinnerExperience.selectedItem as String
            val workArrangement = when {
                cbOnSite.isChecked -> "On-site"
                cbRemote.isChecked -> "Remote"
                cbHybrid.isChecked -> "Hybrid"
                else -> ""
            }
            if (title.isEmpty() || description.isEmpty() || workArrangement.isEmpty()) {
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val job = hashMapOf(
                "title" to title,
                "description" to description,
                "experienceLevel" to experienceLevel,
                "workArrangement" to workArrangement,
                "employerEmail" to email,
                "status" to "open",
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("jobs")
                .add(job)
                .addOnSuccessListener {
                    Toast.makeText(context, "Job posted successfully!", Toast.LENGTH_SHORT).show()
                    jobCreatedListener?.onJobCreated()
                    dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed to post job: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

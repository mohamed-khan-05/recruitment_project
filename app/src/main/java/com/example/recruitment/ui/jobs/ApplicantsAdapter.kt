package com.example.recruitment.ui.jobs

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.recruitment.R
import com.example.recruitment.databinding.ItemApplicantBinding
import com.example.recruitment.model.Applicant
import com.google.firebase.firestore.FirebaseFirestore

class ApplicantsAdapter(
    private val jobId: String,
    private val onChatClick: (Applicant) -> Unit,
    private val onStatusUpdated: () -> Unit
) : RecyclerView.Adapter<ApplicantsAdapter.ViewHolder>() {

    private val applicants = mutableListOf<Applicant>()
    private val db = FirebaseFirestore.getInstance()

    fun submitList(newList: List<Applicant>) {
        applicants.clear()
        applicants.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemApplicantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = applicants.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val applicant = applicants[position]
        val context = holder.binding.root.context
        holder.binding.apply {
            tvApplicantName.text = applicant.fullName.ifEmpty { "(no name)" }
            tvApplicantEmail.text = applicant.email
            chipGroupKeywords.removeAllViews()
            applicant.keywords.forEach { kw ->
                val tvKeyword = LayoutInflater.from(root.context)
                    .inflate(R.layout.item_keyword, chipGroupKeywords, false) as TextView
                tvKeyword.text = kw
                chipGroupKeywords.addView(tvKeyword)
            }
            btnAccept.setOnClickListener { showConfirmationDialog(context, "accept", applicant) }
            btnReject.setOnClickListener { showConfirmationDialog(context, "reject", applicant) }
            btnChat.setOnClickListener { onChatClick(applicant) }
        }
    }

    private fun showConfirmationDialog(
        context: android.content.Context,
        action: String,
        applicant: Applicant
    ) {
        val status = if (action == "accept") "accepted" else "rejected"
        AlertDialog.Builder(context)
            .setTitle("Confirm $status")
            .setMessage("Are you sure you want to $action this applicant?")
            .setPositiveButton("Yes") { _, _ -> updateStatus(applicant, status, context) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateStatus(
        applicant: Applicant,
        newStatus: String,
        context: android.content.Context
    ) {
        val decisionTime = System.currentTimeMillis() / 1000
        val appRef = db.collection("jobs")
            .document(jobId)
            .collection("applications")
            .document(applicant.applicationId)

        // 1️⃣ Update application status
        appRef.update(
            mapOf(
                "status" to newStatus,
                "decisionAt" to decisionTime
            )
        ).addOnSuccessListener {
            // 2️⃣ Fetch the job document to get its title
            db.collection("jobs")
                .document(jobId)
                .get()
                .addOnSuccessListener { jobSnapshot ->
                    val jobTitle = jobSnapshot.getString("title") ?: "your application"

                    // 3️⃣ Query user by email
                    db.collection("users")
                        .whereEqualTo("email", applicant.email)
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            querySnapshot.documents.forEach { userDoc ->
                                val userId = userDoc.id
                                // 4️⃣ Build and add notification with jobTitle
                                val notification = mapOf(
                                    "title" to if (newStatus == "accepted") "Application Accepted" else "Application Rejected",
                                    "message" to "Your application for \"$jobTitle\" was $newStatus.",
                                    "type" to "application_$newStatus",
                                    "timestamp" to decisionTime,
                                    "read" to false
                                )
                                db.collection("users")
                                    .document(userId)
                                    .collection("notifications")
                                    .add(notification)
                            }
                            // 5️⃣ Refresh UI
                            Toast.makeText(context, "Applicant $newStatus", Toast.LENGTH_SHORT)
                                .show()
                            applicants.remove(applicant)
                            notifyDataSetChanged()
                            onStatusUpdated()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                context,
                                "Failed to notify: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        context,
                        "Failed to fetch job title: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to update status", Toast.LENGTH_SHORT).show()
        }
    }


    class ViewHolder(val binding: ItemApplicantBinding) : RecyclerView.ViewHolder(binding.root)
}

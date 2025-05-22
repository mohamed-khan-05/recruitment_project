package com.example.recruitment.ui.jobs

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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

            root.setOnClickListener {
                openApplicantCV(context, applicant)
            }
        }
    }

    private fun openApplicantCV(context: Context, applicant: Applicant) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("email", applicant.email)
            .limit(1)
            .get()
            .addOnSuccessListener { qs ->
                val doc = qs.documents.firstOrNull()
                if (doc == null) {
                    Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val uid = doc.id
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("uploadedCVs").document("currentCv")
                    .get()
                    .addOnSuccessListener { snap ->
                        val driveFileId = snap.getString("driveFileId")
                        if (driveFileId.isNullOrEmpty()) {
                            Toast.makeText(context, "No CV uploaded", Toast.LENGTH_SHORT).show()
                        } else {
                            askToOpenInBrowser(context, driveFileId)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ApplicantsAdapter", "CV lookup failed", e)
                        Toast.makeText(context, "Error fetching CV", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ApplicantsAdapter", "User lookup failed", e)
                Toast.makeText(context, "Error finding user", Toast.LENGTH_SHORT).show()
            }
    }

    private fun askToOpenInBrowser(context: Context, driveFileId: String) {
        val fileUrl = "https://drive.google.com/file/d/$driveFileId/view?usp=sharing"
        AlertDialog.Builder(context)
            .setTitle("Open CV")
            .setMessage("Do you want to open this CV in your browser?")
            .setPositiveButton("Yes") { _, _ ->
                openInBrowser(context, fileUrl)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun openInBrowser(context: Context, fileUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl))
        intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
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

        appRef.update(
            mapOf(
                "status" to newStatus,
                "decisionAt" to decisionTime
            )
        ).addOnSuccessListener {
            db.collection("jobs")
                .document(jobId)
                .get()
                .addOnSuccessListener { jobSnapshot ->
                    val jobTitle = jobSnapshot.getString("title") ?: "your application"
                    db.collection("users")
                        .whereEqualTo("email", applicant.email)
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            querySnapshot.documents.forEach { userDoc ->
                                val userId = userDoc.id
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

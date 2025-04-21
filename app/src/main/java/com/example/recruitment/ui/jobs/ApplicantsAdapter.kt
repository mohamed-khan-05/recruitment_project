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

class ApplicantsAdapter(private val jobId: String, private val onChatClick: (Applicant) -> Unit) :
    RecyclerView.Adapter<ApplicantsAdapter.ViewHolder>() {

    private val applicants = mutableListOf<Applicant>()
    private val db = FirebaseFirestore.getInstance()

    fun submitList(newList: List<Applicant>) {
        applicants.clear()
        applicants.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemApplicantBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
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

            btnAccept.setOnClickListener {
                showConfirmationDialog(context, "accept", applicant)
            }

            btnReject.setOnClickListener {
                showConfirmationDialog(context, "reject", applicant)
            }

            btnChat.setOnClickListener {
                onChatClick(applicant) // ✅ Use the callback
            }

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
            .setPositiveButton("Yes") { _, _ ->
                updateStatus(applicant, status, context) // ✅ Pass context here
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateStatus(
        applicant: Applicant,
        newStatus: String,
        context: android.content.Context
    ) {
        val decisionTime = System.currentTimeMillis() / 1000

        db.collection("jobs")
            .document(jobId)
            .collection("applications")
            .document(applicant.applicationId)
            .update(
                mapOf(
                    "status" to newStatus,
                    "decisionAt" to decisionTime
                )
            )
            .addOnSuccessListener {
                applicants.remove(applicant)
                notifyDataSetChanged()

                Toast.makeText(
                    context,
                    "Applicant $newStatus",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                Toast.makeText(
                    context,
                    "Failed to update status",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    class ViewHolder(val binding: ItemApplicantBinding) :
        RecyclerView.ViewHolder(binding.root)
}

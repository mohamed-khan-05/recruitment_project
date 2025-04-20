package com.example.recruitment.ui.home

import android.app.AlertDialog
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.recruitment.databinding.ItemApplicationBinding
import com.example.recruitment.model.ApplicationData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ApplicationsAdapter(
    private val applications: MutableList<ApplicationData>
) : RecyclerView.Adapter<ApplicationsAdapter.ApplicationViewHolder>() {

    inner class ApplicationViewHolder(val binding: ItemApplicationBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplicationViewHolder {
        val binding =
            ItemApplicationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ApplicationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ApplicationViewHolder, position: Int) {
        val application = applications[position]
        val context = holder.itemView.context

        holder.binding.tvJobTitle.text = application.jobTitle
        holder.binding.tvCompanyName.text = application.companyName
        holder.binding.tvStatus.text = application.status.capitalize()

        // Color config (KEEPING YOUR ORIGINAL COLORS)
        when (application.status.lowercase()) {
            "pending" -> {
                holder.binding.tvStatus.setBackgroundColor(Color.parseColor("#FFF59D")) // light yellow
                holder.binding.tvStatus.setTextColor(Color.parseColor("#795548")) // brown
            }

            "accepted" -> {
                holder.binding.tvStatus.setBackgroundColor(Color.parseColor("#C8E6C9")) // light green
                holder.binding.tvStatus.setTextColor(Color.parseColor("#2E7D32")) // dark green
            }

            "rejected" -> {
                holder.binding.tvStatus.setBackgroundColor(Color.parseColor("#FFCDD2")) // light red
                holder.binding.tvStatus.setTextColor(Color.parseColor("#B71C1C")) // maroon
            }

            else -> {
                holder.binding.tvStatus.setBackgroundColor(Color.LTGRAY)
                holder.binding.tvStatus.setTextColor(Color.BLACK)
            }
        }

        // Withdraw click logic only if status is pending
        holder.itemView.setOnClickListener {
            if (application.status.lowercase() != "pending") {
                Toast.makeText(
                    context,
                    "You cannot withdraw an application that is ${application.status}.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(context)
                .setTitle("Withdraw Application?")
                .setMessage("Are you sure you want to withdraw your application for '${application.jobTitle}'?")
                .setPositiveButton("Yes") { _, _ ->
                    val currentUserEmail =
                        FirebaseAuth.getInstance().currentUser?.email ?: return@setPositiveButton
                    val db = FirebaseFirestore.getInstance()

                    // Correct reference to the job document
                    val jobRef = db.collection("jobs")
                        .document(application.jobId) // Correctly reference the job document by its ID
                    val appsCollection =
                        jobRef.collection("applications") // Reference to the applications subcollection

                    appsCollection
                        .whereEqualTo("email", currentUserEmail)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            val doc = snapshot.documents.firstOrNull()
                            if (doc != null) {
                                // Valid document found, proceed with withdrawal
                                appsCollection.document(doc.id) // Reference to the specific application document
                                    .delete()
                                    .addOnSuccessListener {
                                        Toast.makeText(
                                            context,
                                            "Application withdrawn.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        applications.removeAt(position)
                                        notifyItemRemoved(position)
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(
                                            context,
                                            "Failed to withdraw: ${it.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Application not found.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                context,
                                "Failed to find application.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun getItemCount(): Int = applications.size
}

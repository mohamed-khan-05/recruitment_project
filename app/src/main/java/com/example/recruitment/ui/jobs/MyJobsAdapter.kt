package com.example.recruitment.ui.jobs

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.recruitment.R
import com.google.firebase.firestore.FirebaseFirestore

class MyJobsAdapter(
    private val onJobClick: (String, String, String, String, String, Long) -> Unit,
    private val onDeleteClicked: (String) -> Unit,
    private val onViewApplicantsClicked: (String) -> Unit
) : RecyclerView.Adapter<MyJobsAdapter.JobViewHolder>() {
    private val jobs = mutableListOf<Pair<String, List<Any>>>()
    private val db = FirebaseFirestore.getInstance()

    fun submitList(newJobs: List<Pair<String, List<Any>>>) {
        jobs.clear()
        jobs.addAll(newJobs)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_job, parent, false)
        return JobViewHolder(view)
    }

    override fun getItemCount() = jobs.size

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val (jobId, jobData) = jobs[position]
        val title = jobData[0] as String
        val description = jobData[1] as String
        val experienceLevel = jobData[2] as String
        val workArrangement = jobData[3] as String
        val status = jobData[4] as String
        val timestamp = jobData[5] as Long

        holder.title.text = title
        holder.textPending.text = "Pending: 0"

        db.collection("jobs")
            .document(jobId)
            .collection("applications")
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { snap ->
                holder.textPending.text = "Pending: ${snap.size()}"
            }
            .addOnFailureListener {
                holder.textPending.text = "Pending: error"
                Log.e("MyJobsAdapter", "failed to fetch pending for $jobId", it)
            }

        val bgColorRes = if (status == "closed")
            R.color.job_closed_bg
        else
            R.color.job_open_bg

        holder.itemView.setBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, bgColorRes)
        )
        holder.itemView.setOnClickListener {
            onJobClick(jobId, title, description, experienceLevel, workArrangement, timestamp)
        }
        holder.deleteButton.setOnClickListener {
            onDeleteClicked(jobId)
        }
        holder.viewApplicantsButton.setOnClickListener {
            onViewApplicantsClicked(jobId)
        }
    }

    class JobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.jobTitle)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        val viewApplicantsButton: Button =
            itemView.findViewById(R.id.viewApplicantsButton)
        val textPending: TextView = itemView.findViewById(R.id.textPending)
    }
}

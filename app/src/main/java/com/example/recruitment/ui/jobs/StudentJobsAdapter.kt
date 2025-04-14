package com.example.recruitment.ui.jobs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.recruitment.R
import com.example.recruitment.model.Job

class StudentJobsAdapter(
    private val onJobClick: (Job) -> Unit,
    private val onChatClick: (Job) -> Unit
) : RecyclerView.Adapter<StudentJobsAdapter.JobViewHolder>() {

    private val jobs = mutableListOf<Job>()

    fun submitList(newJobs: List<Job>) {
        jobs.clear()
        jobs.addAll(newJobs)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_job, parent, false)
        return JobViewHolder(view)
    }

    override fun getItemCount(): Int = jobs.size

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val job = jobs[position]
        holder.title.text = job.title
        holder.info.text = "${job.experienceLevel} | ${job.workArrangement}"

        holder.itemView.setOnClickListener { onJobClick(job) }
        holder.chatButton.setOnClickListener { onChatClick(job) }
    }

    class JobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tvTitle)
        val info: TextView = itemView.findViewById(R.id.tvInfo)
        val chatButton: ImageButton = itemView.findViewById(R.id.btnChat)
    }
}
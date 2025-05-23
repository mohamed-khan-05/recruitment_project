package com.example.recruitment.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.recruitment.databinding.ItemStudentBinding
import com.example.recruitment.model.Student

class StudentAdapter(
    private val onStudentClick: (Student) -> Unit,
    private val onChatClick: (Student) -> Unit
) : RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {

    private val students = mutableListOf<Student>()

    inner class StudentViewHolder(
        private val binding: ItemStudentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(student: Student) {
            binding.tvStudentName.text = student.fullName
            binding.tvStudentEmail.text = student.email
            binding.tvJobTitle.text = student.jobTitle.ifEmpty { "N/A" }
            binding.root.setOnClickListener {
                onStudentClick(student)
            }
            binding.btnChat.setOnClickListener {
                onChatClick(student)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val binding = ItemStudentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StudentViewHolder(binding)
    }

    override fun getItemCount() = students.size

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        holder.bind(students[position])
    }

    fun submitList(list: List<Student>) {
        students.clear()
        students.addAll(list)
        notifyDataSetChanged()
    }
}

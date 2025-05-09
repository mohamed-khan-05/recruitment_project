package com.example.recruitment.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.recruitment.databinding.FragmentJobDescriptionDialogBinding
import java.text.SimpleDateFormat
import java.util.*

class JobDescriptionDialogFragment : DialogFragment() {
    private var _binding: FragmentJobDescriptionDialogBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(
            title: String,
            description: String,
            experienceLevel: String,
            workArrangement: String,
            timestamp: Long
        ): JobDescriptionDialogFragment {
            val fragment = JobDescriptionDialogFragment()
            val args = Bundle().apply {
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
        _binding = FragmentJobDescriptionDialogBinding.inflate(inflater, container, false)
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
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.format(Date(timestamp))
        } else {
            "N/A"
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

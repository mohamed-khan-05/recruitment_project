package com.example.recruitment.ui.dialogs

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.example.recruitment.R

class JobFilterDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_CURRENT_WORK = "currentWork"
        private const val ARG_CURRENT_TYPE = "currentType"

        fun newInstance(currentWork: String?, currentType: String?): JobFilterDialogFragment {
            return JobFilterDialogFragment().also {
                it.arguments = Bundle().apply {
                    putString(ARG_CURRENT_WORK, currentWork)
                    putString(ARG_CURRENT_TYPE, currentType)
                }
            }
        }
    }

    private val workOptions = listOf("Please select", "On-site", "Remote", "Hybrid")
    private val jobTypes =
        listOf("Please select", "Internship", "Entry Level", "Associate", "Mid-senior Level")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_job_filter, container, false)

    // In JobFilterDialogFragment.kt
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val workSpinner = view.findViewById<Spinner>(R.id.spinner_work)
        val typeSpinner = view.findViewById<Spinner>(R.id.spinner_type)
        val buttonApply = view.findViewById<Button>(R.id.button_apply)
        val buttonReset = view.findViewById<Button>(R.id.button_reset)

        workSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            workOptions
        )
        typeSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            jobTypes
        )

        // Restore previous selections, case-insensitive
        arguments?.getString(ARG_CURRENT_WORK)?.let { current ->
            workOptions.indexOfFirst { it.equals(current, ignoreCase = true) }
                .takeIf { it >= 0 }
                ?.let { workSpinner.setSelection(it) }
        }
        arguments?.getString(ARG_CURRENT_TYPE)?.let { current ->
            jobTypes.indexOfFirst { it.equals(current, ignoreCase = true) }
                .takeIf { it >= 0 }
                ?.let { typeSpinner.setSelection(it) }
        }

        buttonApply.setOnClickListener {
            val selectedWork = workOptions
                .getOrNull(workSpinner.selectedItemPosition)
                .takeIf { workSpinner.selectedItemPosition != 0 }
            val selectedType = jobTypes
                .getOrNull(typeSpinner.selectedItemPosition)
                .takeIf { typeSpinner.selectedItemPosition != 0 }

            // Send result through parentFragmentManager
            parentFragmentManager.setFragmentResult(
                "job_filter_result",
                bundleOf(
                    "workArrangement" to selectedWork,
                    "jobType" to selectedType
                )
            )
            dismiss()
        }

        buttonReset.setOnClickListener {
            workSpinner.setSelection(0)
            typeSpinner.setSelection(0)
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

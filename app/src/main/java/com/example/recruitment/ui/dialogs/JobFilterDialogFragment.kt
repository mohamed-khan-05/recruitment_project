package com.example.recruitment.ui.dialogs

import android.os.Bundle
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

    private val workOptions = listOf("On-site", "Remote", "Hybrid")
    private val jobTypes = listOf("Internship", "Entry Level", "Associate", "Mid-senior Level")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_job_filter, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val workSpinner = view.findViewById<Spinner>(R.id.spinner_work)
        val typeSpinner = view.findViewById<Spinner>(R.id.spinner_type)
        val buttonApply = view.findViewById<Button>(R.id.button_apply)

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

        buttonApply.setOnClickListener {
            val selectedWork = workOptions.getOrNull(workSpinner.selectedItemPosition)
            val selectedType = jobTypes.getOrNull(typeSpinner.selectedItemPosition)

            setFragmentResult(
                "job_filter_result",
                bundleOf(
                    "workArrangement" to selectedWork,
                    "jobType" to selectedType
                )
            )

            dismiss()
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

package com.example.recruitment.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.recruitment.R
import com.example.recruitment.databinding.FragmentDashboardBinding
import com.example.recruitment.ui.dialogs.CreateJobDialogFragment
import com.example.recruitment.ui.dialogs.OnJobCreatedListener

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by activityViewModels() // ✅ shared ViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonViewMyJobs.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_dashboard_to_myJobsFragment)
        }
        binding.buttonCreateJob.setOnClickListener {
            showCreateJobDialog()
        }
        viewModel.applicationViews.observe(viewLifecycleOwner) { count ->
            binding.tvApplicants.text = count.toString()
        }
        viewModel.totalApplications.observe(viewLifecycleOwner) { count ->
            binding.tvTotalApplications.text = count.toString()
        }
        binding.cardApplicants.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_applicationViewsFragment)
        }
        binding.cardApplications.setOnClickListener {
            // Optional: navigate to a new screen or show something
        }
    }

    private fun showCreateJobDialog() {
        val dialog = CreateJobDialogFragment()
        dialog.jobCreatedListener = object : OnJobCreatedListener {
            override fun onJobCreated() {
                viewModel.fetchTotalApplications() // 🔁 Refresh count after job is created
            }
        }
        dialog.show(parentFragmentManager, "CreateJobDialogFragment")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
    }
}
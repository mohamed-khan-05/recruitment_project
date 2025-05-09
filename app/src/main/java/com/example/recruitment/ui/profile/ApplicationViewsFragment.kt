package com.example.recruitment.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.recruitment.databinding.FragmentApplicationViewsBinding

class ApplicationViewsFragment : Fragment() {

    private var _binding: FragmentApplicationViewsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApplicationViewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: Load and display application views data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

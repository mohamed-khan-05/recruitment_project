package com.example.recruitment.ui.search

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recruitment.databinding.FragmentSearchBinding
import com.example.recruitment.model.Student
import com.google.firebase.firestore.FirebaseFirestore

class EmployerSearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val allMatched = mutableListOf<Student>()
    private var displayedCount = 0

    private val adapter by lazy { StudentAdapter { /* on‑click if needed */ } }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FragmentSearchBinding.inflate(inflater, container, false)
        .also { _binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerStudents.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerStudents.adapter = adapter

        // Search-as-you-type, no initial load
        binding.etStudentSearch.addTextChangedListener { editable ->
            val query = editable.toString().trim()
                .lowercase()  // capture locally :contentReference[oaicite:4]{index=4}

            if (query.isEmpty()) {
                // clear when empty
                allMatched.clear()
                displayedCount = 0
                adapter.submitList(emptyList())
                setLoadMoreEnabled(false)
            } else {
                fetchAndFilter(query)
            }
        }

        binding.btnLoadMoreStudents.setOnClickListener {
            showNextPage()
        }
    }

    private fun fetchAndFilter(query: String) {
        allMatched.clear()
        displayedCount = 0
        adapter.submitList(emptyList())
        setLoadMoreEnabled(false)

        db.collection("users")
            .whereEqualTo("userType", "Student")
            .get()
            .addOnSuccessListener { snapshot ->
                val docs = snapshot.documents
                if (docs.isEmpty()) return@addOnSuccessListener

                var remaining = docs.size
                for (doc in docs) {
                    val uid = doc.id
                    val fullName = doc.getString("fullName").orEmpty()
                    val email = doc.getString("email").orEmpty()

                    // fetch keywords sub-doc
                    db.collection("users")
                        .document(uid)
                        .collection("keywords")
                        .document("fromCv")
                        .get()
                        .addOnSuccessListener { kwDoc ->
                            val keywords = kwDoc.get("keywords") as? List<String> ?: emptyList()
                            val jobTitle = kwDoc.getString("jobTitle").orEmpty()

                            // match on keywords OR jobTitle :contentReference[oaicite:5]{index=5}
                            if (keywords.any { it.lowercase().contains(query) }
                                || jobTitle.lowercase().contains(query)
                            ) {
                                allMatched.add(Student(uid, fullName, email, jobTitle, keywords))
                            }
                        }
                        .addOnCompleteListener {
                            remaining--
                            if (remaining == 0) {
                                // remove duplicates by uid :contentReference[oaicite:6]{index=6}
                                val unique = allMatched.distinctBy { it.uid }
                                allMatched.clear()
                                allMatched.addAll(unique)

                                showNextPage()
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("EmployerSearch", "Error fetching students", e)
            }
    }

    private fun showNextPage() {
        // page in batches of 10 :contentReference[oaicite:7]{index=7}
        val next = (displayedCount + 10).coerceAtMost(allMatched.size)
        adapter.submitList(allMatched.take(next))
        displayedCount = next
        setLoadMoreEnabled(displayedCount < allMatched.size)
    }

    private fun setLoadMoreEnabled(enabled: Boolean) {
        binding.btnLoadMoreStudents.isEnabled = enabled
        binding.btnLoadMoreStudents.alpha = if (enabled) 1f else 0.5f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

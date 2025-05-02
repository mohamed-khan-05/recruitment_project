package com.example.recruitment.ui.search

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recruitment.R
import com.example.recruitment.databinding.FragmentSearchBinding
import com.example.recruitment.model.Student
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import androidx.appcompat.app.AlertDialog

class EmployerSearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser
    private lateinit var adapter: StudentAdapter
    private var allMatchedStudents: MutableList<Student> = mutableListOf()
    private var displayedCount = 0
    private var lastVisibleDoc: DocumentSnapshot? = null
    private var searchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = StudentAdapter(
            onStudentClick = { student -> openStudentCV(student) },
            onChatClick = { student -> startChat(student) }
        )
        binding.recyclerStudents.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerStudents.adapter = adapter
        binding.btnLoadMoreStudents.setOnClickListener {
            loadStudents(reset = false)
        }
        binding.etStudentSearch.addTextChangedListener { editable ->
            searchQuery = editable.toString().trim().lowercase()
            if (searchQuery.isNotEmpty()) {
                loadStudents(reset = true)
            } else {

                adapter.submitList(emptyList())
                allMatchedStudents.clear()
                displayedCount = 0
                lastVisibleDoc = null
                setLoadMoreVisible(false)
            }
        }
        if (searchQuery.isNotEmpty()) {
            loadStudents(reset = true)
        }
    }

    private fun openStudentCV(student: Student) {
        val db = Firebase.firestore
        val userRef =
            db.collection("users").document(student.uid)
        userRef.collection("uploadedCVs").document("currentCv").get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val driveFileId = documentSnapshot.getString("driveFileId")
                    if (driveFileId != null) {

                        askToOpenInBrowser(driveFileId)
                    } else {
                        Toast.makeText(context, "CV file not found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "CV document not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("OpenCV", "Error getting CV document", exception)
            }
    }

    private fun askToOpenInBrowser(driveFileId: String) {
        val fileUrl = "https://drive.google.com/file/d/$driveFileId/view?usp=sharing"
        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Open CV")
            .setMessage("Do you want to open this CV in your browser?")
            .setPositiveButton("Yes") { _, _ ->
                openInBrowser(fileUrl)
            }
            .setNegativeButton("No", null)
        builder.show()
    }

    private fun openInBrowser(fileUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl))
        intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
        startActivity(intent)
    }

    private fun loadStudents(reset: Boolean) {
        if (searchQuery.isEmpty()) return

        if (reset) {
            allMatchedStudents.clear()
            displayedCount = 0
            lastVisibleDoc = null
            adapter.submitList(emptyList())
        }

        var query = db.collection("users")
            .whereEqualTo("userType", "Student")
            .limit(10)

        lastVisibleDoc?.let { query = query.startAfter(it) }

        query.get().addOnSuccessListener { snapshot ->
            if (snapshot.isEmpty) {
                setLoadMoreVisible(false)
                return@addOnSuccessListener
            }

            lastVisibleDoc = snapshot.documents.last()
            var remaining = snapshot.documents.size

            for (doc in snapshot.documents) {
                val uid = doc.id
                val fullName = doc.getString("fullName").orEmpty()
                val email = doc.getString("email").orEmpty()

                db.collection("users").document(uid)
                    .collection("keywords").document("fromCv")
                    .get()
                    .addOnSuccessListener { kwDoc ->
                        val keywords = kwDoc.get("keywords") as? List<String> ?: emptyList()
                        val jobTitle = kwDoc.getString("jobTitle").orEmpty()

                        val matches = keywords.any { it.lowercase().contains(searchQuery) } ||
                                jobTitle.lowercase().contains(searchQuery) ||
                                fullName.lowercase().contains(searchQuery) ||
                                email.lowercase().contains(searchQuery)

                        if (matches) {
                            allMatchedStudents.add(
                                Student(
                                    uid,
                                    fullName,
                                    email,
                                    jobTitle,
                                    keywords
                                )
                            )
                        }
                    }
                    .addOnCompleteListener {
                        remaining--
                        if (remaining == 0) {
                            val unique = allMatchedStudents.distinctBy { it.uid }
                            allMatchedStudents.clear()
                            allMatchedStudents.addAll(unique)
                            displayNextPage()
                        }
                    }
            }
        }.addOnFailureListener {
            Log.e("EmployerSearch", "Failed to load students: ${it.message}")
        }
    }

    private fun displayNextPage() {
        val next = (displayedCount + 10).coerceAtMost(allMatchedStudents.size)
        val nextPage = allMatchedStudents.take(next)
        adapter.submitList(nextPage)
        displayedCount = nextPage.size
        setLoadMoreVisible(displayedCount < allMatchedStudents.size)
    }

    private fun setLoadMoreVisible(visible: Boolean) {
        binding.btnLoadMoreStudents.isEnabled = visible
        binding.btnLoadMoreStudents.alpha = if (visible) 1f else 0.5f
    }

    private fun startChat(student: Student) {
        val employerEmail = currentUser?.email ?: return
        val bundle = Bundle().apply {
            putString("studentEmail", student.email)
            putString("studentId", student.uid)
        }

        db.collection("conversations")
            .whereArrayContains("participants", employerEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                val existing = snapshot.documents.firstOrNull { doc ->
                    val participants = doc.get("participants") as? List<*>
                    participants?.contains(student.email) == true
                }
                Log.d("EmployerSearch", "Navigating to chat with ID: ${bundle.getString("chatId")}")
                if (existing != null) {

                    bundle.putString("chatId", existing.id)
                    findNavController().navigate(R.id.action_employerSearch_to_chat, bundle)
                } else {

                    val newConversationData = mapOf(
                        "participants" to listOf(employerEmail, student.email),
                        "lastTimestamp" to System.currentTimeMillis(),
                        "lastMessage" to "",
                        "unreadMessagesCount" to mapOf(
                            employerEmail to 0,
                            student.email to 0
                        )
                    )
                    db.collection("conversations")
                        .add(newConversationData)
                        .addOnSuccessListener { newDoc ->

                            bundle.putString("chatId", newDoc.id)
                            findNavController().navigate(R.id.action_employerSearch_to_chat, bundle)
                        }
                        .addOnFailureListener { e ->
                            Log.e("EmployerSearch", "Failed to create conversation: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("EmployerSearch", "Error fetching conversations: ${e.message}")
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

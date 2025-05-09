package com.example.recruitment.ui.notifications

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recruitment.databinding.FragmentNotificationsBinding
import com.example.recruitment.model.NotificationData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    private val notifications = mutableListOf<NotificationData>()
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = NotificationAdapter(notifications) { nd, pos ->
            AlertDialog.Builder(requireContext())
                .setTitle("Delete notification")
                .setMessage("Are you sure you want to delete this notification?")
                .setPositiveButton("Delete") { _, _ ->
                    currentUid?.let { uid ->
                        db.collection("users")
                            .document(uid)
                            .collection("notifications")
                            .document(nd.id)
                            .delete()
                            .addOnSuccessListener {
                                notifications.removeAt(pos)
                                adapter.notifyItemRemoved(pos)
                                toggleEmptyView()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to delete: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifications.adapter = adapter

        loadNotifications()
    }

    private fun loadNotifications() {
        currentUid?.let { uid ->
            db.collection("users")
                .document(uid)
                .collection("notifications")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { snap ->
                    notifications.clear()
                    for (doc in snap.documents) {
                        doc.toObject(NotificationData::class.java)
                            ?.takeIf {
                                it.type == "application_accepted"
                                        || it.type == "application_rejected"
                            }
                            ?.copy(id = doc.id)
                            ?.let { notifications.add(it) }
                    }
                    adapter.notifyDataSetChanged()
                    toggleEmptyView()
                }
                .addOnFailureListener {
                    toggleEmptyView()
                }
        }
    }


    private fun toggleEmptyView() {
        val empty = notifications.isEmpty()
        binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvNotifications.visibility = if (empty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

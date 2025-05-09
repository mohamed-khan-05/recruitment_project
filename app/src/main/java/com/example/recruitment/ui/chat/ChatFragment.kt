package com.example.recruitment.ui.chat

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recruitment.R
import com.example.recruitment.databinding.FragmentChatBinding
import com.example.recruitment.model.Conversation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ChatFragment : Fragment() {
    private var binding: FragmentChatBinding? = null
    private lateinit var db: FirebaseFirestore
    private var messagesListener: ListenerRegistration? = null
    private val conversationsList: MutableList<Conversation> = ArrayList()
    private lateinit var adapter: ConversationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentChatBinding.inflate(inflater, container, false)
        db = FirebaseFirestore.getInstance()

        adapter = ConversationAdapter(conversationsList) { conversation ->
            val currentUser = FirebaseAuth.getInstance().currentUser?.email
            val other = conversation.participants.firstOrNull { it != currentUser }

            val bundle = Bundle().apply {
                putString("employerEmail", other)
                putString("chatId", conversation.id)
            }
            findNavController().navigate(R.id.action_chat_to_chat_page, bundle)
        }

        binding!!.recyclerChatList.layoutManager = LinearLayoutManager(context)
        binding!!.recyclerChatList.adapter = adapter

        fetchConversations()

        return binding!!.root
    }

    private fun fetchConversations() {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
        messagesListener = db.collection("conversations")
            .whereArrayContains("participants", currentUserEmail)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("ChatFragment", "Firestore error: ", e)
                    return@addSnapshotListener
                }

                snapshot?.let {
                    conversationsList.clear()
                    for (doc in it.documents) {
                        val conversation = doc.toObject(Conversation::class.java)
                        if (conversation != null) {
                            conversation.id = doc.id
                            conversationsList.add(conversation)
                        } else {
                            Log.w("ChatFragment", "Conversation is null for doc ID: ${doc.id}")
                        }
                    }
                    adapter.notifyDataSetChanged()
                }
            }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        messagesListener?.remove()
        binding = null
    }
}

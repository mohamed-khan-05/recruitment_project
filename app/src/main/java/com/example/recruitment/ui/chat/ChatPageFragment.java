package com.example.recruitment.ui.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.recruitment.databinding.FragmentChatPageBinding;
import com.example.recruitment.model.Message;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.*;

public class ChatPageFragment extends Fragment {
    private FragmentChatPageBinding binding;
    private FirebaseFirestore db;
    private String chatId;
    private String otherUserEmail;
    private ListenerRegistration messagesListener;
    private final List<Message> messagesList = new ArrayList<>();
    private MessageAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentChatPageBinding.inflate(inflater, container, false);

        chatId = getArguments().getString("chatId", "");
        otherUserEmail = getArguments().getString("employerEmail", "");

        db = FirebaseFirestore.getInstance();
        adapter = new MessageAdapter(messagesList);

        binding.recyclerChatMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerChatMessages.setAdapter(adapter);

        markChatAsRead();
        fetchMessages();

        binding.sendButton.setOnClickListener(v -> {
            String messageText = binding.messageInput.getText().toString().trim();
            if (!messageText.isEmpty()) {
                sendMessage(messageText);
                binding.messageInput.setText("");
            }
        });

        return binding.getRoot();
    }

    private void markChatAsRead() {
        String currentUser = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (currentUser == null || chatId.isEmpty()) return;

        db.collection("conversations")
                .document(chatId)
                .update(FieldPath.of("unreadMessagesCount", currentUser), 0);
    }


    private void fetchMessages() {
        messagesListener = db.collection("conversations")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null || e != null) return;

                    messagesList.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String sender = doc.getString("senderEmail");
                        String text = doc.getString("message");
                        String status = doc.getString("status");
                        Long timestamp = doc.getLong("timestamp");

                        if (sender != null && text != null && status != null && timestamp != null) {
                            messagesList.add(new Message(sender, text, timestamp, status));
                        }
                    }
                    adapter.notifyDataSetChanged();
                    binding.recyclerChatMessages.scrollToPosition(messagesList.size() - 1);
                });
    }

    private void sendMessage(String messageText) {
        String senderEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (senderEmail == null || chatId.isEmpty() || otherUserEmail.isEmpty()) return;

        long now = System.currentTimeMillis();
        Message newMessage = new Message(senderEmail, messageText, now, "sent");

        db.collection("conversations")
                .document(chatId)
                .collection("messages")
                .add(newMessage)
                .addOnSuccessListener(ref -> updateConversationMetadata(messageText, now));
    }

    private void updateConversationMetadata(String messageText, long timestamp) {
        db.collection("conversations")
                .document(chatId)
                .update(
                        FieldPath.of("lastMessage"), messageText,
                        FieldPath.of("lastTimestamp"), timestamp,
                        FieldPath.of("unreadMessagesCount", otherUserEmail), FieldValue.increment(1)
                );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (messagesListener != null) messagesListener.remove();
        binding = null;
    }
}

package com.example.recruitment.ui.chat;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.recruitment.databinding.FragmentChatPageBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.ListenerRegistration;
import com.example.recruitment.model.Message;

import java.util.ArrayList;
import java.util.List;

public class ChatPageFragment extends Fragment {

    private FragmentChatPageBinding binding;
    private FirebaseFirestore db;
    private String chatId;
    private ListenerRegistration messagesListener;
    private List<Message> messagesList = new ArrayList<>();
    private MessageAdapter adapter;

    private String employerEmail;
    private String jobId;

    private static final String TAG = "ChatPageFragment";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentChatPageBinding.inflate(inflater, container, false);

        // Retrieve passed arguments
        chatId = getArguments().getString("chatId", "");
        employerEmail = getArguments().getString("employerEmail", "");
        jobId = getArguments().getString("jobId", "");

        Log.d(TAG, "Chat ID: " + chatId);
        Log.d(TAG, "Employer Email: " + employerEmail);
        Log.d(TAG, "Job ID: " + jobId);

        db = FirebaseFirestore.getInstance();

        adapter = new MessageAdapter(messagesList);
        binding.recyclerChatMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerChatMessages.setAdapter(adapter);

        fetchMessages();

        binding.sendButton.setOnClickListener(v -> {
            String messageText = binding.messageInput.getText().toString();
            if (!messageText.isEmpty()) {
                sendMessage(messageText);
                binding.messageInput.setText(""); // Clear input after sending
            }
        });

        return binding.getRoot();
    }

    private void fetchMessages() {
        messagesListener = db.collection("conversations")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error fetching messages: ", e);
                        return;
                    }

                    if (snapshot != null && !snapshot.isEmpty()) {
                        messagesList.clear();
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            String senderEmail = document.getString("senderEmail");
                            String messageText = document.getString("message");
                            String status = document.getString("status");
                            Object rawTimestamp = document.get("timestamp");
                            long timestampVal = 0L;
                            if (rawTimestamp instanceof Long) {
                                timestampVal = (Long) rawTimestamp;
                            } else if (rawTimestamp instanceof Double) {
                                timestampVal = ((Double) rawTimestamp).longValue();
                            } else if (rawTimestamp instanceof com.google.firebase.Timestamp) {
                                timestampVal = ((com.google.firebase.Timestamp) rawTimestamp).toDate().getTime();
                            } else {
                                Log.w(TAG, "Unexpected timestamp type for doc ID: " + document.getId());
                            }

                            if (senderEmail != null && messageText != null && rawTimestamp != null && status != null) {
                                Message message = new Message(
                                        senderEmail,
                                        messageText,
                                        timestampVal,
                                        status
                                );
                                messagesList.add(message);
                            } else {
                                Log.w(TAG, "Incomplete message data for doc ID: " + document.getId());
                            }
                        }
                        adapter.notifyDataSetChanged();
                        binding.recyclerChatMessages.scrollToPosition(messagesList.size() - 1);
                    }
                });
    }


    private void sendMessage(String messageText) {
        String senderEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        if (senderEmail == null) return;

        Message newMessage = new Message(senderEmail, messageText, System.currentTimeMillis(), "sent");

        db.collection("conversations") // ✅ updated collection
                .document(chatId)
                .collection("messages")
                .add(newMessage)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Message sent successfully: " + messageText);
                    // Optionally update last message timestamp
                    db.collection("conversations")
                            .document(chatId)
                            .update("lastMessageTimestamp", newMessage.getTimestamp());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending message: ", e);
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (messagesListener != null) {
            messagesListener.remove(); // Remove listener on fragment destruction
        }
        binding = null; // Ensure binding is nullified to avoid memory leaks
    }
}

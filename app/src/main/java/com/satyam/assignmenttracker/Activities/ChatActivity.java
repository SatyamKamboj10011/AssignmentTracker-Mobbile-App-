package com.satyam.assignmenttracker.Activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.satyam.assignmenttracker.models.Assignment;
import com.satyam.assignmenttracker.AssignmentRepository;
import com.satyam.assignmenttracker.Adapters.ChatAdapter;
import com.satyam.assignmenttracker.models.ChatMessage;
import com.satyam.assignmenttracker.R;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView rvChatMessages;
    private EditText etMessage;
    private ImageButton btnSend;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages = new ArrayList<>();

    private boolean dataReady = false;
    private List<String> completedIds = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);

        // Use chatRoot from your XML
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chatRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        rvChatMessages = findViewById(R.id.rvChatMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        chatAdapter = new ChatAdapter(messages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChatMessages.setLayoutManager(layoutManager);
        rvChatMessages.setAdapter(chatAdapter);

        addBotMessage("Hi! I'm your assignment assistant 🤖\n" +
                "Loading your assignments from the database...");

        btnSend.setOnClickListener(v -> sendUserMessage());

        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendUserMessage();
                return true;
            }
            return false;
        });

        loadAssignmentsForChat();
    }

    private void loadAssignmentsForChat() {
        String uid = null;
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        if (uid == null) {
            addBotMessage("I couldn't find your account. Please sign in again.");
            dataReady = false;
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        AssignmentRepository.initForStudent(db, uid, new AssignmentRepository.InitCallback() {
            @Override
            public void onReady() {
                dataReady = true;
                completedIds = AssignmentRepository.getCompletedIds();
                addBotMessage(
                        "👋 Hey! I'm your **Assignment Assistant**.\n" +
                                "I can help you stay on top of your work.\n\n" +
                                "**You can ask things like:**\n" +
                                "• **What’s my next assignment?**\n" +
                                "• **Show overdue assignments**\n" +
                                "• **How many assignments are pending?**\n" +
                                "• **Assignments for course CS101**\n" +
                                "• **When is <assignment name> due?** (coming soon)\n\n" +
                                "Just type your question below ⬇️"
                );

            }

            @Override
            public void onError(String message) {
                dataReady = false;
                addBotMessage("I couldn't load your assignments: " + message);
            }
        });
    }

    private void sendUserMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        ChatMessage userMessage = new ChatMessage(text, true);
        chatAdapter.addMessage(userMessage);
        scrollToBottom();
        etMessage.setText("");

        if (!dataReady) {
            addBotMessage("I'm still loading your assignments. Try again in a moment…");
            return;
        }

        String reply = getBotReply(text);
        addBotMessage(reply);
    }

    private void addBotMessage(String text) {
        ChatMessage botMessage = new ChatMessage(text, false);
        chatAdapter.addMessage(botMessage);
        scrollToBottom();
    }

    private void scrollToBottom() {
        rvChatMessages.post(() ->
                rvChatMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1)
        );
    }

    // ---------- BOT USING REAL DATA NOW ----------

    private String getBotReply(String userText) {
        String lower = userText.toLowerCase();
        long now = System.currentTimeMillis();

        // HELP
        if (lower.contains("help") || lower.contains("what can") || lower.contains("how do")) {
            return "Here’s what I can help you with:\n\n" +
                    "📌 **Assignment Tracking**\n" +
                    "• \"What's my next assignment?\"\n" +
                    "• \"Show overdue assignments\"\n" +
                    "• \"How many assignments are left?\"\n\n" +
                    "📚 **Course-specific Info**\n" +
                    "• \"Assignments for course <course code>\"\n" +
                    "  (Example: \"Assignments for CS101\")\n\n" +
                    "🪄 **Quick Tips**\n" +
                    "• Keep your courses updated for accurate results\n" +
                    "• Submit assignments to mark them as completed\n\n" +
                    "Ask away — I'm here to help! 🤖";
        }



        // NEXT DUE
        if (lower.contains("next") && lower.contains("assignment")) {
            Assignment next = AssignmentRepository.getNextDue(now, completedIds);
            if (next == null) return "I don't see any upcoming assignments 🎉";
            return "Your next assignment is:\n\n" +
                    "• " + safe(next.getTitle()) + "\n" +
                    "Due on: " + safe(next.getDueDate()) + "\n" +
                    "Course: " + safe(next.getCourseId());
        }

        // OVERDUE
        if (lower.contains("overdue")) {
            List<Assignment> list = AssignmentRepository.getOverdue(now, completedIds);
            if (list.isEmpty()) return "You have no overdue assignments. Nice work! 🎉";
            StringBuilder sb = new StringBuilder("Overdue assignments:\n");
            for (Assignment a : list) {
                sb.append("• ")
                        .append(safe(a.getTitle()))
                        .append(" (due ")
                        .append(safe(a.getDueDate()))
                        .append(")\n");
            }
            return sb.toString();
        }

        // COUNT PENDING
        if (lower.contains("left") || lower.contains("how many")) {
            List<Assignment> list = AssignmentRepository.getPendingAssignments(now, completedIds);
            int count = list.size();
            if (count == 0) return "You have no pending assignments 😎";
            return "You have " + count + " pending assignment" + (count == 1 ? "" : "s") + ".";
        }

        // BY COURSE
        if (lower.contains("course")) {
            // naive: treat the last word as course id / code
            String[] words = lower.split(" ");
            String courseId = words[words.length - 1];

            List<Assignment> list = AssignmentRepository.getByCourse(courseId);
            if (list.isEmpty()) return "I couldn't find assignments for course \"" + courseId + "\".";
            StringBuilder sb = new StringBuilder("Assignments for " + courseId + ":\n");
            for (Assignment a : list) {
                sb.append("• ")
                        .append(safe(a.getTitle()))
                        .append(" (due ")
                        .append(safe(a.getDueDate()))
                        .append(")\n");
            }
            return sb.toString();
        }

        // DEFAULT
        return "I'm using your real assignments now, but I didn't catch that 😅\n" +
                "Try asking things like:\n" +
                "• \"What's my next assignment?\"\n" +
                "• \"Show overdue assignments\"\n" +
                "• \"How many assignments are left?\"";
    }

    private String safe(String s) {
        return s == null ? "N/A" : s;
    }
}

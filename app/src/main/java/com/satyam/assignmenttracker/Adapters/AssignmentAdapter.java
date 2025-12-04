package com.satyam.assignmenttracker.Adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.satyam.assignmenttracker.Activities.AssignmentDetailsActivity;
import com.satyam.assignmenttracker.models.Assignment;
import com.satyam.assignmenttracker.R;

import java.util.List;

public class AssignmentAdapter extends RecyclerView.Adapter<AssignmentAdapter.VH> {

    Context ctx;
    List<Assignment> items;
    FirebaseAuth mAuth = FirebaseAuth.getInstance();
    FirebaseFirestore db = FirebaseFirestore.getInstance();

    public AssignmentAdapter(Context ctx, List<Assignment> items) {
        this.ctx = ctx;
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_assignment, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {

        Assignment a = items.get(position);
        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        holder.tvTitle.setText(a.getTitle());
        holder.tvDesc.setText(a.getDescription());
        holder.tvDue.setText("Due: " + a.getDueDate());

        // --- LOAD COMPLETION STATE ---
        db.collection("assignments")
                .document(a.getId())
                .collection("completions")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    boolean isCompleted = doc.exists();
                    setCompletedUI(holder, isCompleted);
                });

        // --- MARK COMPLETE BUTTON ---
        holder.btnMarkComplete.setOnClickListener(v -> {

            db.collection("assignments")
                    .document(a.getId())
                    .collection("completions")
                    .document(uid)
                    .set(new CompletionRecord(System.currentTimeMillis()))
                    .addOnSuccessListener(r -> {

                        setCompletedUI(holder, true);
                        Toast.makeText(ctx, "Marked as completed", Toast.LENGTH_SHORT).show();

                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(ctx, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        });

        // OPEN DETAILS
        holder.btnViewDetails.setOnClickListener(v -> {
            Intent i = new Intent(ctx, AssignmentDetailsActivity.class);
            i.putExtra("id", a.getId());
            ctx.startActivity(i);
        });
    }

    // helper to change UI
    private void setCompletedUI(VH holder, boolean completed) {

        if (completed) {
            holder.btnMarkComplete.setText("Completed");
            holder.btnMarkComplete.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#10B981"))); // green
            holder.btnMarkComplete.setTextColor(android.graphics.Color.WHITE);
            holder.btnMarkComplete.setEnabled(false); // prevent clicking again

        } else {
            holder.btnMarkComplete.setText("Mark Completed");
            holder.btnMarkComplete.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2563EB"))); // blue
            holder.btnMarkComplete.setTextColor(android.graphics.Color.WHITE);
            holder.btnMarkComplete.setEnabled(true);
        }
    }


    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDue, tvDesc;
        Button btnMarkComplete, btnViewDetails;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDue = itemView.findViewById(R.id.tvDue);
            tvDesc = itemView.findViewById(R.id.tvDesc);
            btnMarkComplete = itemView.findViewById(R.id.btnMarkComplete);
            btnViewDetails = itemView.findViewById(R.id.btnViewDetails);
        }
    }

    // small helper POJO stored in completion doc
    static class CompletionRecord {
        long completedAt;
        CompletionRecord() {}
        CompletionRecord(long ts) { this.completedAt = ts; }
        public long getCompletedAt() { return completedAt; }
        public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
    }
}

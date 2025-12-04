package com.satyam.assignmenttracker.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.satyam.assignmenttracker.R;
import com.satyam.assignmenttracker.models.User;

import java.util.List;

/**
 * Simple RecyclerView adapter to show students in a course.
 * Exposes two callbacks via Listener:
 *  - onClickStudent(User student)  -> user tapped the row
 *  - onRemoveStudent(User student) -> user tapped the remove button
 */
public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.VH> {

    public interface Listener {
        void onRemoveStudent(User student);
        void onClickStudent(User student);
    }

    private final Context ctx;
    private final List<User> students;
    private final Listener listener;

    public StudentAdapter(Context ctx, List<User> students, Listener listener) {
        this.ctx = ctx;
        this.students = students;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_student_participant, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        User u = students.get(position);
        holder.tvName.setText(u.getDisplayName() != null ? u.getDisplayName() : "(no name)");
        holder.tvEmail.setText(u.getEmail() != null ? u.getEmail() : "");
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClickStudent(u);
        });
        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) listener.onRemoveStudent(u);
        });
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvEmail;
        Button btnRemove;           // <-- changed to Button to match your XML
        public VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvStudentName);
            tvEmail = itemView.findViewById(R.id.tvStudentEmail);
            btnRemove = itemView.findViewById(R.id.btnRemoveStudent);
        }
    }
}

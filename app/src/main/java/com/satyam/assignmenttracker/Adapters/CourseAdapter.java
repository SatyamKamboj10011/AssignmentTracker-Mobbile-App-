package com.satyam.assignmenttracker.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.satyam.assignmenttracker.models.Course;
import com.satyam.assignmenttracker.R;

import java.util.List;

/**
 * CourseAdapter for listing courses.
 * Listener now exposes:
 *  - onItemClick(Course)  -> clicking whole item
 *  - onEdit(Course)       -> edit button
 *  - onDelete(Course)     -> delete button
 */
public class CourseAdapter extends RecyclerView.Adapter<CourseAdapter.VH> {

    public interface Listener {
        void onItemClick(Course course);
        void onEdit(Course course);
        void onDelete(Course course);
    }

    private final Context ctx;
    private final List<Course> items;
    private final Listener listener;

    public CourseAdapter(Context ctx, List<Course> items, Listener listener) {
        this.ctx = ctx;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_course, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Course c = items.get(position);
        holder.bind(c);
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    public Course getItem(int pos) {
        return (items != null && pos >= 0 && pos < items.size()) ? items.get(pos) : null;
    }

    class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvCode, tvTeacher;
        Button btnEdit, btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvCourseTitle);
            tvCode = itemView.findViewById(R.id.tvCourseCode);
            tvTeacher = itemView.findViewById(R.id.tvCourseTeacher);
            btnEdit = itemView.findViewById(R.id.btnEditCourse);
            btnDelete = itemView.findViewById(R.id.btnDeleteCourse);
        }

        void bind(final Course c) {
            if (c == null) return;

            tvTitle.setText(c.getTitle() != null ? c.getTitle() : "(no title)");
            tvCode.setText(c.getCode() != null ? c.getCode() : "");
            tvTeacher.setText(c.getTeacherId() != null && !c.getTeacherId().isEmpty() ? "Teacher: " + c.getTeacherId() : "No teacher");

            // whole item click
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(c);
            });

            // edit button
            if (btnEdit != null) {
                btnEdit.setOnClickListener(v -> {
                    if (listener != null) listener.onEdit(c);
                });
            }

            // delete button
            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> {
                    if (listener != null) listener.onDelete(c);
                });
            }
        }
    }
}

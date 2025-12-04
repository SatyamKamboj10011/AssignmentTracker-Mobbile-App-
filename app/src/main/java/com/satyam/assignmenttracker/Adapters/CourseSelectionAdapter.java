package com.satyam.assignmenttracker.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.satyam.assignmenttracker.models.CourseSummary;
import com.satyam.assignmenttracker.R;

import java.util.List;

public class CourseSelectionAdapter extends RecyclerView.Adapter<CourseSelectionAdapter.VH> {

    public interface OnCourseClick {
        void onClick(CourseSummary course);
    }

    private final List<CourseSummary> list;
    private final OnCourseClick callback;

    public CourseSelectionAdapter(List<CourseSummary> list, OnCourseClick callback) {
        this.list = list;
        this.callback = callback;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.course_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        CourseSummary cs = list.get(pos);

        h.tvTitle.setText(cs.title);
        h.tvSummary.setText("Assignments: " + cs.count +
                "   Next due: " + cs.nextDueText);

        h.itemView.setOnClickListener(v -> callback.onClick(cs));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSummary;

        VH(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvCourseTitle);     // from course_item.xml
            tvSummary = v.findViewById(R.id.tvCourseSummary); // from course_item.xml
        }
    }
}

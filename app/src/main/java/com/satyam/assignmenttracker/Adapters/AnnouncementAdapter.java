package com.satyam.assignmenttracker.Adapters;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.satyam.assignmenttracker.models.Announcement;
import com.satyam.assignmenttracker.R;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AnnouncementAdapter extends RecyclerView.Adapter<AnnouncementAdapter.VH> {

    public interface Listener {
        void onItemClick(Announcement a);
    }

    private final List<Announcement> list = new ArrayList<>();
    private final Listener listener;

    public AnnouncementAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Announcement> items) {
        list.clear();
        if (items != null) list.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_announcement, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Announcement a = list.get(position);
        holder.bind(a, listener);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvAnnTitle, tvAnnMessage, tvAnnTime, tvAnnCourse;

        VH(@NonNull View itemView) {
            super(itemView);
            tvAnnTitle = itemView.findViewById(R.id.tvAnnTitle);
            tvAnnMessage = itemView.findViewById(R.id.tvAnnMessage);
            tvAnnTime = itemView.findViewById(R.id.tvAnnTime);
            tvAnnCourse = itemView.findViewById(R.id.tvAnnCourse);
        }

        void bind(Announcement a, Listener listener) {
            tvAnnTitle.setText(a.getTitle() != null ? a.getTitle() : "(No title)");
            tvAnnMessage.setText(a.getMessage() != null ? a.getMessage() : "");

            Long ts = a.getCreatedAt();
            if (ts != null) {
                String dateStr = DateFormat.format("dd MMM, hh:mm a", new Date(ts)).toString();
                tvAnnTime.setText(dateStr);
            } else {
                tvAnnTime.setText("");
            }

            if (a.getCourseTitle() != null && !a.getCourseTitle().isEmpty()) {
                tvAnnCourse.setVisibility(View.VISIBLE);
                tvAnnCourse.setText("Course: " + a.getCourseTitle());
            } else {
                tvAnnCourse.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(a);
            });
        }
    }
}

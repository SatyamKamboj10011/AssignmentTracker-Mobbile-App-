package com.satyam.assignmenttracker.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.satyam.assignmenttracker.Activities.ProgressLogEntry;
import com.satyam.assignmenttracker.R;

import java.util.List;

public class ProgressTimelineAdapter extends RecyclerView.Adapter<ProgressTimelineAdapter.VH> {

    private final List<ProgressLogEntry> items;

    public ProgressTimelineAdapter(List<ProgressLogEntry> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_progress_entry, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ProgressLogEntry e = items.get(position);
        holder.tvDate.setText(e.getDateFormatted() != null ? e.getDateFormatted() : "--");
        holder.tvNotes.setText(
                (e.getNotes() != null && !e.getNotes().trim().isEmpty())
                        ? e.getNotes()
                        : "(No note text)"
        );

        holder.cbRead.setChecked(e.isStepRead());
        holder.cbDraft.setChecked(e.isStepDraft());
        holder.cbFinal.setChecked(e.isStepFinal());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvDate, tvNotes;
        CheckBox cbRead, cbDraft, cbFinal;

        VH(@NonNull View itemView) {
            super(itemView);
            tvDate  = itemView.findViewById(R.id.tvEntryDate);
            tvNotes = itemView.findViewById(R.id.tvEntryNotes);
            cbRead  = itemView.findViewById(R.id.cbEntryRead);
            cbDraft = itemView.findViewById(R.id.cbEntryDraft);
            cbFinal = itemView.findViewById(R.id.cbEntryFinal);

            // Just indicators, not editable here
            cbRead.setEnabled(false);
            cbDraft.setEnabled(false);
            cbFinal.setEnabled(false);
        }
    }
}

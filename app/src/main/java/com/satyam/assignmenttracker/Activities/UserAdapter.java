package com.satyam.assignmenttracker.Activities;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.satyam.assignmenttracker.R;
import com.satyam.assignmenttracker.models.User;


import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {

  interface Listener {
      void  onEdit(User user);
      void  onDelete(User user);
  }

  Context ctx;
  List<User> items;
  Listener listener;

  public  UserAdapter(Context ctx, List<User> items, Listener listener ){
      this.ctx = ctx;
      this.items = items;
      this.listener = listener;
  }


    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        User u = items.get(position);
        holder.tvName.setText(u.getDisplayName() != null ? u.getDisplayName() : "(no name)");
        holder.tvEmail.setText(u.getEmail() != null ? u.getEmail() : "");
        holder.tvRole.setText(u.getRole() != null ? u.getRole() : "student");

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(u);
        });
        holder.btnDelete.setOnClickListener(v -> {
            // confirm delete
            new AlertDialog.Builder(ctx)
                    .setTitle("Delete user")
                    .setMessage("Delete user " + (u.getDisplayName() != null ? u.getDisplayName() : u.getEmail()) + " ?")
                    .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                    .setPositiveButton("Delete", (d, w) -> {
                        // delete from firestore
                        FirebaseFirestore.getInstance().collection("users").document(u.getId())
                                .delete()
                                .addOnSuccessListener(a -> {
                                    Toast.makeText(ctx, "Deleted", Toast.LENGTH_SHORT).show();
                                    if (listener != null) listener.onDelete(u);
                                })
                                .addOnFailureListener(e -> Toast.makeText(ctx, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }).show();
        });
    }

    @Override
    public int getItemCount() { return items == null ? 0 : items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvEmail, tvRole;
        Button btnEdit, btnDelete;
        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvUserName);
            tvEmail = itemView.findViewById(R.id.tvUserEmail);
            tvRole = itemView.findViewById(R.id.tvUserRole);
            btnEdit = itemView.findViewById(R.id.btnEditUser);
            btnDelete = itemView.findViewById(R.id.btnDeleteUser);
        }
    }


}


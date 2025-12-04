package com.satyam.assignmenttracker.Activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.satyam.assignmenttracker.Adapters.CloudinaryUploader;
import com.satyam.assignmenttracker.R;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ProfileEditActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQ = 3001;

    private ImageView ivProfile;
    private Button btnPick, btnSave;
    private ProgressBar progress;

    private Uri pickedUri = null;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile_edit);

        View root = findViewById(R.id.main);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }

        ivProfile = findViewById(R.id.iv_profile);
        btnPick = findViewById(R.id.btn_pick_photo);
        btnSave = findViewById(R.id.btn_save_photo);
        progress = findViewById(R.id.progress);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setProgressVisible(false);

        // 🔹 Hide Save until user chooses a photo
        if (btnSave != null) {
            btnSave.setVisibility(View.GONE);
        }

        // Load current user's photoUrl from Firestore
        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            db.collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        if (doc != null && doc.exists()) {
                            String photo = doc.getString("photoUrl");
                            if (photo != null && !photo.isEmpty()) {
                                try {
                                    Glide.with(ProfileEditActivity.this)
                                            .load(photo)
                                            .placeholder(R.drawable.ic_social_placeholder)
                                            .error(R.drawable.ic_social_placeholder)
                                            .into(ivProfile);
                                } catch (Exception e) {
                                    ivProfile.setImageResource(R.drawable.ic_social_placeholder);
                                }
                            } else {
                                ivProfile.setImageResource(R.drawable.ic_social_placeholder);
                            }
                        } else {
                            ivProfile.setImageResource(R.drawable.ic_social_placeholder);
                        }
                    })
                    .addOnFailureListener(e -> ivProfile.setImageResource(R.drawable.ic_social_placeholder));
        } else {
            ivProfile.setImageResource(R.drawable.ic_social_placeholder);
        }

        btnPick.setOnClickListener(v -> openImagePicker());

        btnSave.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() == null) {
                Toast.makeText(ProfileEditActivity.this, "Please sign in first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (pickedUri == null) {
                Toast.makeText(ProfileEditActivity.this, "Please pick a photo first", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadAndSaveToCloudinary(pickedUri);
        });
    }

    private void openImagePicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, "Choose photo"), PICK_IMAGE_REQ);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQ && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                pickedUri = uri;
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
                    if (is != null) is.close();
                    if (bmp != null) ivProfile.setImageBitmap(bmp);
                    else ivProfile.setImageURI(uri);
                } catch (Exception e) {
                    try { ivProfile.setImageURI(uri); } catch (Exception ignored) {}
                }

                // 🔹 After preview is shown, make Save visible
                if (btnSave != null) {
                    btnSave.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    /**
     * Upload the selected image to Cloudinary using CloudinaryUploader
     * and then save the secure_url into Firestore users/{uid}.photoUrl
     */
    private void uploadAndSaveToCloudinary(Uri imageUri) {
        String uid = (mAuth.getCurrentUser() != null) ? mAuth.getCurrentUser().getUid() : null;
        if (uid == null) {
            Toast.makeText(this, "User id missing", Toast.LENGTH_SHORT).show();
            return;
        }

        setProgressVisible(true);

        // Get mime type if possible
        String mime = getContentResolver().getType(imageUri);
        if (mime == null || mime.trim().isEmpty()) {
            mime = "image/jpeg";
        }

        final String fileName = uid + "_profile.jpg";
        final String mimeFinal = mime;
        final String uidFinal = uid;

        // Run upload off the UI thread
        new Thread(() -> {
            try {
                // Upload to Cloudinary (this is your existing helper)
                String secureUrl = CloudinaryUploader.uploadUriStreaming(
                        getContentResolver(),
                        imageUri,
                        fileName,
                        mimeFinal
                );

                // Save URL into Firestore on the main thread
                runOnUiThread(() -> {
                    Map<String, Object> upd = new HashMap<>();
                    upd.put("photoUrl", secureUrl);

                    db.collection("users").document(uidFinal)
                            .set(upd, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                setProgressVisible(false);
                                Toast.makeText(ProfileEditActivity.this, "Profile photo updated", Toast.LENGTH_SHORT).show();
                                // 🔹 Close and return to previous screen (dashboard will refresh in onResume)
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                setProgressVisible(false);
                                Toast.makeText(ProfileEditActivity.this, "Upload ok but Firestore update failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    setProgressVisible(false);
                    Toast.makeText(ProfileEditActivity.this, "Cloudinary upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void setProgressVisible(boolean visible) {
        if (progress != null) {
            progress.setIndeterminate(visible);
            progress.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (btnPick != null) btnPick.setEnabled(!visible);
        if (btnSave != null) btnSave.setEnabled(!visible);
    }
}

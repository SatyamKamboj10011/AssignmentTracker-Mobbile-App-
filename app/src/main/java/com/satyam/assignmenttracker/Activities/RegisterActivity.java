package com.satyam.assignmenttracker.Activities;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.satyam.assignmenttracker.Adapters.CloudinaryUploader;
import com.satyam.assignmenttracker.R;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQ = 2001;

    EditText txtName, txtEmail, txtPassword, txtConfirm,
            txtStudentId,  // uses existing id txt_r_roll in XML
            txtDept, txtCourse;
    Button btnRegister;
    Button btnPickPhoto;          // optional: open gallery
    ImageView ivProfilePreview;   // optional: show selected preview

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    ProgressDialog progress;

    // selected image uri (nullable)
    Uri pickedImageUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // find views (IDs must match your XML)
        txtName = findViewById(R.id.txt_r_name);
        txtEmail = findViewById(R.id.txt_r_email);
        txtPassword = findViewById(R.id.txt_r_password);
        txtConfirm = findViewById(R.id.txt_r_confirm);
        // IMPORTANT: keep XML id as txt_r_roll, use as Student ID field
        txtStudentId = findViewById(R.id.txt_r_roll);
        txtDept = findViewById(R.id.txt_r_dept);
        txtCourse = findViewById(R.id.txt_r_course);
        btnRegister = findViewById(R.id.btn_Register);

        // optional views (may be null if layout doesn't include them)
        btnPickPhoto = findViewById(R.id.btnPickPhoto);
        ivProfilePreview = findViewById(R.id.iv_profile_preview);

        // initialize firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        progress = new ProgressDialog(this);
        progress.setCancelable(false);
        progress.setMessage("Please wait...");

        // wire pick photo button or preview tap for picking
        if (btnPickPhoto != null) {
            btnPickPhoto.setOnClickListener(v -> openImagePicker());
        } else if (ivProfilePreview != null) {
            ivProfilePreview.setOnClickListener(v -> openImagePicker());
        }

        btnRegister.setOnClickListener(v -> startRegistration());
    }

    // ---------------- IMAGE PICKING ----------------

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Choose profile photo"), PICK_IMAGE_REQ);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQ && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                pickedImageUri = uri;
                // show preview if available
                if (ivProfilePreview != null) {
                    try {
                        InputStream is = getContentResolver().openInputStream(uri);
                        Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
                        if (is != null) is.close();
                        ivProfilePreview.setImageBitmap(bmp);
                    } catch (Exception e) {
                        try {
                            ivProfilePreview.setImageURI(uri);
                        } catch (Exception ignored) {}
                    }
                }
                Toast.makeText(this, "Photo selected", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ---------------- REGISTRATION FLOW ----------------

    private void startRegistration() {
        final String name = txtName.getText() != null ? txtName.getText().toString().trim() : "";
        final String email = txtEmail.getText() != null ? txtEmail.getText().toString().trim() : "";
        final String password = txtPassword.getText() != null ? txtPassword.getText().toString() : "";
        final String confirm = txtConfirm.getText() != null ? txtConfirm.getText().toString() : "";
        final String studentId = txtStudentId.getText() != null ? txtStudentId.getText().toString().trim() : "";
        final String dept = txtDept.getText() != null ? txtDept.getText().toString().trim() : "";
        final String course = txtCourse.getText() != null ? txtCourse.getText().toString().trim() : "";

        // basic validation
        if (TextUtils.isEmpty(email)) {
            txtEmail.setError("Email required");
            txtEmail.requestFocus();
            Toast.makeText(this, "Email can't be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            txtPassword.setError("Minimum 6 characters");
            txtPassword.requestFocus();
            Toast.makeText(this, "Password should be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirm)) {
            txtConfirm.setError("Passwords do not match");
            txtConfirm.requestFocus();
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        final String defaultRole = "student";

        new AlertDialog.Builder(this)
                .setTitle("Invite code")
                .setMessage("Do you have an invite code to register as Teacher or Admin? (choose No for a standard Student account)")
                .setPositiveButton("Yes", (dialog, which) ->
                        promptInviteCodeThenCreate(email, password, name, studentId, dept, course))
                .setNegativeButton("No", (dialog, which) ->
                        createUser(email, password, defaultRole, name, studentId, dept, course, null))
                .show();
    }

    private void promptInviteCodeThenCreate(String email, String password,
                                            String name, String studentId, String dept, String course) {
        final EditText input = new EditText(this);
        input.setHint("Enter invite code");

        new AlertDialog.Builder(this)
                .setTitle("Enter invite code")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String code = input.getText() != null ? input.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(code)) {
                        Toast.makeText(this, "Invite code required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    verifyInviteCodeThenCreate(email, password, name, studentId, dept, course, code);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void verifyInviteCodeThenCreate(String email, String password,
                                            String name, String studentId, String dept,
                                            String course, String code) {
        progress.setMessage("Checking invite...");
        progress.show();

        db.collection("inviteCodes")
                .document(code)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) {
                        // fallback: maybe inviteDocs are stored with autoId and a "code" field
                        db.collection("inviteCodes")
                                .whereEqualTo("code", code)
                                .limit(1)
                                .get()
                                .addOnSuccessListener(qs -> {
                                    progress.dismiss();
                                    if (qs.isEmpty()) {
                                        Toast.makeText(this, "Invalid invite code", Toast.LENGTH_LONG).show();
                                        new AlertDialog.Builder(this)
                                                .setTitle("Invalid code")
                                                .setMessage("Invite code invalid. Register as Student instead?")
                                                .setPositiveButton("Yes", (a, b) ->
                                                        createUser(email, password, "student",
                                                                name, studentId, dept, course, null))
                                                .setNegativeButton("No", null)
                                                .show();
                                        return;
                                    }
                                    DocumentSnapshot d = qs.getDocuments().get(0);
                                    processInviteDocAndCreate(d, email, password, name, studentId, dept, course);
                                })
                                .addOnFailureListener(e -> {
                                    progress.dismiss();
                                    Toast.makeText(this, "Invite check failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                        return;
                    }
                    progress.dismiss();
                    processInviteDocAndCreate(doc, email, password, name, studentId, dept, course);
                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    Toast.makeText(this, "Invite check failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void processInviteDocAndCreate(DocumentSnapshot inviteDoc,
                                           String email, String password, String name,
                                           String studentId, String dept, String course) {
        if (inviteDoc == null || !inviteDoc.exists()) {
            Toast.makeText(this, "Invite code not found", Toast.LENGTH_LONG).show();
            return;
        }
        Boolean used = inviteDoc.getBoolean("used");
        String role = inviteDoc.getString("role");
        if (used != null && used) {
            Toast.makeText(this, "Invite code already used", Toast.LENGTH_LONG).show();
            return;
        }
        if (role == null || role.isEmpty()) {
            Toast.makeText(this, "Invite code invalid (no role)", Toast.LENGTH_LONG).show();
            return;
        }

        createUser(email, password, role.toLowerCase(), name, studentId, dept, course, inviteDoc.getReference());
    }

    // ---------------- CREATE USER + PHOTO HANDLING ----------------

    private void createUser(String email, String password, String role,
                            String name, String studentId, String dept, String course,
                            DocumentReference inviteRef) {

        progress.setMessage("Creating account...");
        progress.show();
        btnRegister.setEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        progress.dismiss();
                        btnRegister.setEnabled(true);
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Registration failed";
                        Toast.makeText(RegisterActivity.this, msg, Toast.LENGTH_LONG).show();
                        return;
                    }

                    String uid = (mAuth.getCurrentUser() != null)
                            ? mAuth.getCurrentUser().getUid()
                            : null;
                    if (uid == null) {
                        progress.dismiss();
                        btnRegister.setEnabled(true);
                        Toast.makeText(RegisterActivity.this,
                                "Registration succeeded but uid missing",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // If a profile photo was chosen, upload to Cloudinary first
                    if (pickedImageUri != null) {
                        uploadProfileImageToCloudinaryAndSave(uid, pickedImageUri,
                                email, role, name, studentId, dept, course, inviteRef);
                    } else {
                        // No photo: just save user doc
                        saveUserDoc(uid, null, email, role, name, studentId, dept, course, inviteRef);
                    }
                });
    }

    /**
     * Uploads profile image to Cloudinary (like ProfileEditActivity) then calls saveUserDoc().
     */
    private void uploadProfileImageToCloudinaryAndSave(String uid,
                                                       Uri imageUri,
                                                       String email, String role,
                                                       String name, String studentId,
                                                       String dept, String course,
                                                       DocumentReference inviteRef) {

        progress.setMessage("Uploading profile photo...");

        // Get mime type if possible
        String mime = getContentResolver().getType(imageUri);
        if (mime == null || mime.trim().isEmpty()) {
            mime = "image/jpeg";
        }

        final String fileName = uid + "_profile.jpg";
        final String mimeFinal = mime;
        final String uidFinal = uid;

        // Do upload off the UI thread
        new Thread(() -> {
            try {
                // Use your Cloudinary helper like in ProfileEditActivity
                String secureUrl = CloudinaryUploader.uploadUriStreaming(
                        getContentResolver(),
                        imageUri,
                        fileName,
                        mimeFinal
                );

                // Success: now save Firestore user with photoUrl
                runOnUiThread(() ->
                        saveUserDoc(uidFinal, secureUrl, email, role, name, studentId, dept, course, inviteRef)
                );

            } catch (Exception e) {
                e.printStackTrace();
                // Upload failed → still create user doc without photoUrl
                runOnUiThread(() -> {
                    Toast.makeText(RegisterActivity.this,
                            "Photo upload failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    saveUserDoc(uidFinal, null, email, role, name, studentId, dept, course, inviteRef);
                });
            }
        }).start();
    }

    // ---------------- SAVE USER DOC ----------------

    private void saveUserDoc(String uid, String photoUrl, String email, String role,
                             String name, String studentId, String dept, String course,
                             DocumentReference inviteRef) {

        long now = System.currentTimeMillis();
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("email", email);
        userData.put("role", role != null ? role : "student");
        userData.put("displayName", name != null ? name : "");
        userData.put("studentId", studentId != null ? studentId : "");
        userData.put("department", dept != null ? dept : "");
        userData.put("course", course != null ? course : "");
        userData.put("createdAt", now);
        if (photoUrl != null) userData.put("photoUrl", photoUrl);

        db.collection("users").document(uid)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    // mark invite as used if present
                    if (inviteRef != null) {
                        Map<String, Object> upd = new HashMap<>();
                        upd.put("used", true);
                        upd.put("usedBy", uid);
                        upd.put("usedAt", now);
                        inviteRef.update(upd)
                                .addOnSuccessListener(x -> {})
                                .addOnFailureListener(e -> {});
                    }

                    progress.dismiss();
                    btnRegister.setEnabled(true);
                    Toast.makeText(RegisterActivity.this,
                            "Registered as " + (role != null ? role : "student"),
                            Toast.LENGTH_LONG).show();

                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    btnRegister.setEnabled(true);
                    Toast.makeText(RegisterActivity.this,
                            "User saved but profile update failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                    finish();
                });
    }
}

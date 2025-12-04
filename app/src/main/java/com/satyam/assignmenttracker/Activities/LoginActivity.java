package com.satyam.assignmenttracker.Activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;   // <-- added
import android.widget.TextView;
import android.widget.Toast;

//for notifications
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.satyam.assignmenttracker.R;

public class LoginActivity extends AppCompatActivity {

    private EditText txtEmail, txtPassword;
    private TextView tVregister;
    private Button btnLogin;
    private ImageButton btnGoogle, btnFacebook, btnInstagram, btnGmail; // <-- added

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private static final String TAG = "LoginActivity";

    private static final int REQ_POST_NOTIF = 9001;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        createNotificationChannel();
        ensureNotificationPermission();
        // apply system window insets if root has id "main"
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });



        // find views (IDs must match activity_login.xml)
        txtEmail = findViewById(R.id.txt_email);
        txtPassword = findViewById(R.id.txt_password);
        btnLogin = findViewById(R.id.btn_login);
        tVregister = findViewById(R.id.tv_register);

        // social buttons
        btnGoogle = findViewById(R.id.btn_google);
        btnFacebook = findViewById(R.id.btn_facebook);
        btnInstagram = findViewById(R.id.btn_instagram);
        btnGmail = findViewById(R.id.btn_gmail);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // go to register screen
        tVregister.setOnClickListener(view -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // login button
        btnLogin.setOnClickListener(view -> attemptLogin());

        // ---------- SOCIAL BUTTON CLICK HANDLERS ----------

        // Google - open Google app or website
        btnGoogle.setOnClickListener(v ->
                openAppOrWebsite(
                        "com.google.android.googlequicksearchbox",   // Google app
                        "https://www.google.com"
                )
        );

        // Facebook - open app or website
        btnFacebook.setOnClickListener(v ->
                openAppOrWebsite(
                        "com.facebook.katana",
                        "https://www.facebook.com"
                )
        );

        // Instagram - open app or website
        btnInstagram.setOnClickListener(v ->
                openAppOrWebsite(
                        "com.instagram.android",
                        "https://www.instagram.com"
                )
        );

        // Gmail - open Gmail app or web mail
        btnGmail.setOnClickListener(v ->
                openAppOrWebsite(
                        "com.google.android.gm",
                        "https://mail.google.com"
                )
        );
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "assignment_reminders";
            String channelName = "Assignment Reminders";
            String channelDesc = "Reminders for upcoming assignment due dates";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
            channel.setDescription(channelDesc);

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void ensureNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIF
                );
            }
        }
    }
    /**
     * Tries to launch an external app by package name.
     * If not installed, opens the fallback URL in a browser.
     */
    private void openAppOrWebsite(String packageName, String fallbackUrl) {
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                startActivity(launchIntent);
            } else {
                // app not installed → open website
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl));
                startActivity(browserIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // if something goes wrong, at least open the website
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl));
                startActivity(browserIntent);
            } catch (Exception ex) {
                ex.printStackTrace();
                Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Validate inputs and perform sign in
    private void attemptLogin() {
        final String email = txtEmail.getText() != null ? txtEmail.getText().toString().trim() : "";
        final String password = txtPassword.getText() != null ? txtPassword.getText().toString().trim() : "";

        if (email.isEmpty()) {
            txtEmail.setError("Email required");
            txtEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            txtEmail.setError("Enter a valid email");
            txtEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            txtPassword.setError("Password required");
            txtPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            txtPassword.setError("Password must be at least 6 characters");
            txtPassword.requestFocus();
            return;
        }

        // disable button while authenticating
        btnLogin.setEnabled(false);
        btnLogin.setAlpha(0.6f);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(LoginActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        // re-enable button
                        btnLogin.setEnabled(true);
                        btnLogin.setAlpha(1f);

                        if (task.isSuccessful()) {
                            // Read user profile by auth UID
                            final String uid = mAuth.getCurrentUser().getUid();
                            db.collection("users").document(uid)
                                    .get()
                                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                        @Override
                                        public void onSuccess(DocumentSnapshot doc) {
                                            String roleFromDoc = null;
                                            if (doc != null && doc.exists()) {
                                                roleFromDoc = doc.getString("role");
                                            }

                                            Log.d(TAG, "authUid=" + uid + ", role=" + roleFromDoc
                                                    + ", docId=" + (doc != null ? doc.getId() : "null"));

                                            if (roleFromDoc != null && roleFromDoc.trim().length() > 0) {
                                                openDashboard(roleFromDoc.trim());
                                                return;
                                            }

                                            // fallback: query by email
                                            final String emailBeingUsed = mAuth.getCurrentUser().getEmail();
                                            if (emailBeingUsed == null) {
                                                openDashboard("Student");
                                                return;
                                            }

                                            db.collection("users")
                                                    .whereEqualTo("email", emailBeingUsed)
                                                    .get()
                                                    .addOnSuccessListener(querySnapshot -> {
                                                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                                                            DocumentSnapshot first = querySnapshot.getDocuments().get(0);
                                                            String roleByEmail = first.getString("role");
                                                            Log.d(TAG, "roleByEmail=" + roleByEmail + ", docId=" + first.getId());
                                                            if (roleByEmail != null && roleByEmail.trim().length() > 0) {
                                                                openDashboard(roleByEmail.trim());
                                                            } else {
                                                                openDashboard("Student");
                                                            }
                                                        } else {
                                                            openDashboard("Student");
                                                        }
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Log.e(TAG, "Error finding user by email: " + e.getMessage());
                                                        openDashboard("Student");
                                                    });
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Log.e(TAG, "Error reading user doc: " + e.getMessage());
                                            openDashboard("Student");
                                        }
                                    });

                        } else {
                            String msg = "Authentication Failed";
                            if (task.getException() != null && task.getException().getMessage() != null) {
                                msg = "Authentication Failed: " + task.getException().getMessage();
                            }
                            Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // Open appropriate dashboard (case-insensitive, trims whitespace)
    private void openDashboard(String role) {
        if (role == null) role = "Student";
        String r = role.trim();

        Intent intent;
        if ("teacher".equalsIgnoreCase(r)) {
            intent = new Intent(LoginActivity.this, TeacherDashboardActivity.class);
        } else if ("admin".equalsIgnoreCase(r)) {
            intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
        } else {
            intent = new Intent(LoginActivity.this, StudentDashboardActivity.class);
        }

        startActivity(intent);
        finish();
    }
}

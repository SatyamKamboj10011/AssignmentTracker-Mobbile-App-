package com.satyam.assignmenttracker.Activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.satyam.assignmenttracker.R;

import java.util.HashMap;
import java.util.Map;

public class ManageInvitesActivity extends AppCompatActivity {

    EditText edtCode;
    Spinner spinnerRole;
    Button btnCreate;

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_manage_invites);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        edtCode = findViewById(R.id.edt_invite_code);
        spinnerRole = findViewById(R.id.spinner_role);
        btnCreate = findViewById(R.id.btn_create_invite);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        //Spinennr options teacher and admin
        String[] roles = new  String[]{"Teacher", "Admin"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(adapter);

        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createInvite();
            }
        });
    }

    private  void  createInvite(){
        String code = edtCode.getText().toString().trim();
        String role = spinnerRole.getSelectedItem().toString();

        if (TextUtils.isEmpty(code)) {
            edtCode.setError("Code Required");
            return;
        }

        //preparing the documentatioin

        Map<String, Object> doc = new HashMap<>();
        doc.put("code", code);
        doc.put("role", role);
        doc.put("used", false);
        if ((mAuth.getCurrentUser() != null)) {

            doc.put("ceatedBy", mAuth.getCurrentUser().getUid());
        }
        doc.put("createdAt", FieldValue.serverTimestamp());

        db.collection("inviteCodes")
                .add(doc).addOnSuccessListener(documentReference -> {
                    Toast.makeText(ManageInvitesActivity.this,"Invite created: " + code, Toast.LENGTH_SHORT).show();
                    edtCode.setText("");
                }).addOnFailureListener(e->{
                    Toast.makeText(ManageInvitesActivity.this, "Error:" + e.getMessage(), Toast.LENGTH_LONG
                        ).show();


    });
}
}
package com.satyam.assignmenttracker.Activities;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.SeekBar;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.satyam.assignmenttracker.models.DrawingView;
import com.satyam.assignmenttracker.R;

import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;

public class DrawingActivity extends AppCompatActivity {

    private static final int REQ_WRITE_PERMISSION = 5001;

    private DrawingView drawingView;
    private MaterialButton btnClear, btnSave, btnEraser;
    private SeekBar seekBrushSize;
    private View btnColorBlack, btnColorRed, btnColorBlue, btnColorGreen;

    private boolean eraserOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawing);

        drawingView = findViewById(R.id.drawingView);
        btnClear = findViewById(R.id.btnClear);
        btnSave = findViewById(R.id.btnSave);
        btnEraser = findViewById(R.id.btnEraser);
        seekBrushSize = findViewById(R.id.seekBrushSize);

        btnColorBlack = findViewById(R.id.btnColorBlack);
        btnColorRed = findViewById(R.id.btnColorRed);
        btnColorBlue = findViewById(R.id.btnColorBlue);
        btnColorGreen = findViewById(R.id.btnColorGreen);

        // initial brush size matches SeekBar progress
        drawingView.setStrokeWidth(seekBrushSize.getProgress());

        btnClear.setOnClickListener(v -> drawingView.clearCanvas());

        btnSave.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            REQ_WRITE_PERMISSION);
                } else {
                    saveDrawing();
                }
            } else {
                saveDrawing();
            }
        });

        seekBrushSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float width = progress <= 0 ? 1f : progress;
                drawingView.setStrokeWidth(width);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // color selectors
        btnColorBlack.setOnClickListener(v -> setPenModeAndColor(0xFF000000));
        btnColorRed.setOnClickListener(v -> setPenModeAndColor(0xFFEF4444));
        btnColorBlue.setOnClickListener(v -> setPenModeAndColor(0xFF3B82F6));
        btnColorGreen.setOnClickListener(v -> setPenModeAndColor(0xFF10B981));

        // eraser toggle
        btnEraser.setOnClickListener(v -> {
            eraserOn = !eraserOn;
            drawingView.setEraser(eraserOn);
            updateEraserButtonUI();
        });
    }

    private void setPenModeAndColor(int color) {
        eraserOn = false;
        drawingView.setEraser(false);
        drawingView.setPaintColor(color);
        updateEraserButtonUI();
    }

    private void updateEraserButtonUI() {
        if (eraserOn) {
            btnEraser.setText("Eraser (On)");
            btnEraser.setStrokeWidth(4);
        } else {
            btnEraser.setText("Eraser");
            btnEraser.setStrokeWidth(2);
        }
    }

    private void saveDrawing() {
        Bitmap bitmap = drawingView.getBitmap();
        if (bitmap == null) {
            Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show();
            return;
        }

        String filename = "Sketch_" + System.currentTimeMillis() + ".png";
        OutputStream out = null;
        Uri imageUri;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AssignmentSketches");

                imageUri = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            } else {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                imageUri = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            }

            if (imageUri == null) {
                Toast.makeText(this, "Unable to save drawing", Toast.LENGTH_SHORT).show();
                return;
            }

            out = getContentResolver().openOutputStream(imageUri);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);

            Toast.makeText(this, "Saved to gallery", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (out != null) {
                try { out.close(); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveDrawing();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

package com.example.quickfix;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImageDetectionActivity extends AppCompatActivity {

    private static final String TAG = "ImageDetectionActivity";
    private static final int CAMERA_PERMISSION_REQ = 1001;
    private static final int STORAGE_PERMISSION_REQ = 1002;
    private static final int CAMERA_CAPTURE_REQ = 1003;
    private static final int GALLERY_PICK_REQ = 1004;

    private MaterialToolbar toolbar;
    private ImageView ivSelectedImage;
    private Button btnTakePhoto, btnChooseGallery;
    private MaterialCardView resultCard;
    private ProgressBar progressBar;
    private TextView tvResultTitle, tvResultSubtitle, tvDetectedService, tvConfidence;
    private Button btnBookService, btnManualPlumbing, btnManualElectrical, btnManualCleaning, btnManualRepair;
    private View layoutManualSelection;

    private ImageLabeler imageLabeler;
    private Uri selectedImageUri;
    private Bitmap selectedBitmap;
    private static final float HIGH_CONFIDENCE_THRESHOLD = 0.68f;
    private static final float LOW_CONFIDENCE_THRESHOLD = 0.45f;

    // Service mapping for ML Kit labels
    private final Map<String, String> labelToServiceMap = new HashMap<String, String>() {{
        put("pipe", "Plumbing");
        put("water", "Plumbing");
        put("leak", "Plumbing");
        put("faucet", "Plumbing");
        put("drain", "Plumbing");
        put("toilet", "Plumbing");
        put("sink", "Plumbing");
        
        put("wire", "Electrical");
        put("switch", "Electrical");
        put("switchboard", "Electrical");
        put("switch board", "Electrical");
        put("socket", "Electrical");
        put("plug", "Electrical");
        put("electrical panel", "Electrical");
        put("panel", "Electrical");
        put("wiring", "Electrical");
        put("distribution board", "Electrical");
        put("outlet", "Electrical");
        put("circuit", "Electrical");
        put("breaker", "Electrical");
        put("electric", "Electrical");
        put("power", "Electrical");
        put("light", "Electrical");
        
        put("wall", "Painting");
        put("paint", "Painting");
        put("color", "Painting");
        put("surface", "Painting");
        put("texture", "Painting");
        
        put("dust", "Cleaning");
        put("dirty", "Cleaning");
        put("mess", "Cleaning");
        put("stain", "Cleaning");
        put("floor", "Cleaning");
        put("window", "Cleaning");
        put("carpet", "Cleaning");
        
        put("wood", "Carpentry");
        put("furniture", "Carpentry");
        put("chair", "Carpentry");
        put("table", "Carpentry");
        put("cabinet", "Carpentry");
        put("door", "Carpentry");
        
        put("fan", "AC Service");
        put("air", "AC Service");
        put("conditioner", "AC Service");
        put("ventilation", "AC Service");
        put("hvac", "AC Service");
        
        put("broken", "Repair");
        put("damage", "Repair");
        put("crack", "Repair");
        put("fix", "Repair");
        put("maintenance", "Repair");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_detection);

        // Initialize ML Kit Image Labeler
        ImageLabelerOptions options = new ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.35f)
                .build();
        imageLabeler = ImageLabeling.getClient(options);

        initViews();
        setupToolbar();
        setupClickListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        ivSelectedImage = findViewById(R.id.ivSelectedImage);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnChooseGallery = findViewById(R.id.btnChooseGallery);
        resultCard = findViewById(R.id.resultCard);
        progressBar = findViewById(R.id.progressBar);
        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvResultSubtitle = findViewById(R.id.tvResultSubtitle);
        tvDetectedService = findViewById(R.id.tvDetectedService);
        tvConfidence = findViewById(R.id.tvConfidence);
        btnBookService = findViewById(R.id.btnBookService);
        layoutManualSelection = findViewById(R.id.layoutManualSelection);
        btnManualPlumbing = findViewById(R.id.btnManualPlumbing);
        btnManualElectrical = findViewById(R.id.btnManualElectrical);
        btnManualCleaning = findViewById(R.id.btnManualCleaning);
        btnManualRepair = findViewById(R.id.btnManualRepair);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("AI Service Detection");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupClickListeners() {
        btnTakePhoto.setOnClickListener(v -> checkCameraPermission());
        btnChooseGallery.setOnClickListener(v -> checkStoragePermission());
        btnBookService.setOnClickListener(v -> bookDetectedService());
        btnManualPlumbing.setOnClickListener(v -> openService("Plumbing"));
        btnManualElectrical.setOnClickListener(v -> openService("Electrical"));
        btnManualCleaning.setOnClickListener(v -> openService("Cleaning"));
        btnManualRepair.setOnClickListener(v -> openService("Repair"));
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQ);
        } else {
            openCamera();
        }
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES}, STORAGE_PERMISSION_REQ);
        } else {
            openGallery();
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_CAPTURE_REQ);
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK);
        galleryIntent.setType("image/*");
        startActivityForResult(galleryIntent, GALLERY_PICK_REQ);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == CAMERA_PERMISSION_REQ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                showPermissionDeniedDialog("Camera permission is required to take photos");
            }
        } else if (requestCode == STORAGE_PERMISSION_REQ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                showPermissionDeniedDialog("Storage permission is required to select images");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == CAMERA_CAPTURE_REQ) {
                Bundle extras = data.getExtras();
                selectedBitmap = (Bitmap) extras.get("data");
                ivSelectedImage.setImageBitmap(selectedBitmap);
                analyzeImage(selectedBitmap);
            } else if (requestCode == GALLERY_PICK_REQ) {
                selectedImageUri = data.getData();
                try {
                    selectedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                    ivSelectedImage.setImageBitmap(selectedBitmap);
                    analyzeImage(selectedBitmap);
                } catch (IOException e) {
                    Log.e(TAG, "Error loading image from gallery", e);
                    Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void analyzeImage(Bitmap bitmap) {
        showLoading(true);
        
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        imageLabeler.process(image)
                .addOnSuccessListener(labels -> {
                    hideLoading();
                    if (!labels.isEmpty()) {
                        processLabels(labels);
                    } else {
                        showNoResults();
                    }
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    Log.e(TAG, "Image labeling failed", e);
                    showError("Failed to analyze image. Please try again.");
                });
    }

    private void processLabels(List<ImageLabel> labels) {
        StringBuilder detectedLabels = new StringBuilder();
        Map<String, Float> serviceScores = new HashMap<>();

        for (ImageLabel label : labels) {
            String labelText = label.getText().toLowerCase();
            float confidence = label.getConfidence();

            detectedLabels.append(label.getText())
                    .append(" (")
                    .append(String.format("%.0f%%", confidence * 100))
                    .append("), ");

            for (Map.Entry<String, String> entry : labelToServiceMap.entrySet()) {
                if (labelText.contains(entry.getKey())) {
                    float weightedScore = confidence * keywordWeight(entry.getKey());
                    String service = entry.getValue();
                    Float existing = serviceScores.get(service);
                    if (existing == null || weightedScore > existing) {
                        serviceScores.put(service, weightedScore);
                    }
                }
            }
        }

        if (serviceScores.isEmpty()) {
            showNoServiceDetected(trimLabelOutput(detectedLabels.toString()), topSuggestions(labels));
            return;
        }

        List<Map.Entry<String, Float>> ranked = new ArrayList<>(serviceScores.entrySet());
        ranked.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        String bestService = ranked.get(0).getKey();
        float bestScore = ranked.get(0).getValue();

        if (bestScore >= HIGH_CONFIDENCE_THRESHOLD) {
            showResult(bestService, bestScore, topServiceSuggestions(ranked));
        } else if (bestScore >= LOW_CONFIDENCE_THRESHOLD) {
            showLowConfidence(bestService, bestScore, topServiceSuggestions(ranked));
        } else {
            showNoServiceDetected(trimLabelOutput(detectedLabels.toString()), topServiceSuggestions(ranked));
        }
    }

    private void showResult(String service, float confidence, String suggestions) {
        resultCard.setVisibility(View.VISIBLE);
        tvResultTitle.setText("✅ Service Detected!");
        tvResultSubtitle.setText("AI has identified the service you need");
        tvDetectedService.setText(service);
        tvConfidence.setText(String.format("Confidence: %.0f%%", confidence * 100));
        btnBookService.setVisibility(View.VISIBLE);
        showManualSelection(false);

        // Store detected service for booking
        btnBookService.setTag(service);
    }

    private void showLowConfidence(String service, float confidence, String suggestions) {
        resultCard.setVisibility(View.VISIBLE);
        tvResultTitle.setText("🤔 Low confidence detection");
        tvResultSubtitle.setText("Top match: " + service + " (" + String.format("%.0f%%", confidence * 100) + ")");
        tvDetectedService.setText("Suggestions: " + suggestions);
        tvConfidence.setText("You can continue or choose manually below");
        btnBookService.setTag(service);
        btnBookService.setVisibility(View.VISIBLE);
        showManualSelection(true);
    }

    private void showNoServiceDetected(String labels, String suggestions) {
        resultCard.setVisibility(View.VISIBLE);
        tvResultTitle.setText("🤔 Could not map service");
        tvResultSubtitle.setText(labels.isEmpty() ? "Try a clearer image with the issue in focus"
                : "Detected labels: " + labels);
        tvDetectedService.setText("Suggestions: " + suggestions);
        tvConfidence.setText("Manual selection recommended");
        btnBookService.setVisibility(View.GONE);
        showManualSelection(true);
    }

    private void showNoResults() {
        resultCard.setVisibility(View.VISIBLE);
        tvResultTitle.setText("❌ No Results");
        tvResultSubtitle.setText("Could not identify any objects in the image");
        tvDetectedService.setText("Try again with a clearer photo");
        tvConfidence.setText("");
        btnBookService.setVisibility(View.GONE);
        showManualSelection(true);
    }

    private void showError(String message) {
        resultCard.setVisibility(View.VISIBLE);
        tvResultTitle.setText("⚠️ Error");
        tvResultSubtitle.setText(message);
        tvDetectedService.setText("");
        tvConfidence.setText("");
        btnBookService.setVisibility(View.GONE);
        showManualSelection(true);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnTakePhoto.setEnabled(!show);
        btnChooseGallery.setEnabled(!show);
    }

    private void hideLoading() {
        showLoading(false);
    }

    private void bookDetectedService() {
        String service = (String) btnBookService.getTag();
        if (service != null) {
            openService(service);
        }
    }

    private void openService(String service) {
        Intent intent = new Intent(this, ProviderListActivity.class);
        intent.putExtra("category", service);
        intent.putExtra("query", service);
        startActivity(intent);
        finish();
    }

    private void showManualSelection(boolean show) {
        if (layoutManualSelection != null) {
            layoutManualSelection.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private float keywordWeight(String keyword) {
        if ("switchboard".equals(keyword)
                || "electrical panel".equals(keyword)
                || "distribution board".equals(keyword)
                || "wiring".equals(keyword)
                || "socket".equals(keyword)) {
            return 1.2f;
        }
        return 1.0f;
    }

    private String trimLabelOutput(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim();
        if (cleaned.endsWith(",")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.length() > 120) {
            cleaned = cleaned.substring(0, 120) + "...";
        }
        return cleaned;
    }

    private String topSuggestions(List<ImageLabel> labels) {
        if (labels == null || labels.isEmpty()) return "Plumbing, Electrical, Cleaning";
        List<ImageLabel> sorted = new ArrayList<>(labels);
        sorted.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        Set<String> picks = new LinkedHashSet<>();
        for (int i = 0; i < sorted.size() && picks.size() < 3; i++) {
            picks.add(sorted.get(i).getText());
        }
        if (picks.isEmpty()) return "Plumbing, Electrical, Cleaning";
        return String.join(", ", picks);
    }

    private String topServiceSuggestions(List<Map.Entry<String, Float>> ranked) {
        if (ranked == null || ranked.isEmpty()) return "Plumbing, Electrical, Cleaning";
        List<String> names = new ArrayList<>();
        for (int i = 0; i < ranked.size() && names.size() < 3; i++) {
            names.add(ranked.get(i).getKey());
        }
        return String.join(", ", names);
    }

    private void showPermissionDeniedDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage(message)
                .setPositiveButton("Grant", (dialog, which) -> {
                    // Retry permission request
                    if (message.contains("Camera")) {
                        checkCameraPermission();
                    } else {
                        checkStoragePermission();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (imageLabeler != null) {
            imageLabeler.close();
        }
    }
}

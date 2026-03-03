package com.example.quickfix;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
public class BusinessProfileActivity extends AppCompatActivity {
    private static final String TAG = "BusinessProfile";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private ImageView ivBusinessImage;
    private TextInputEditText etBusinessName, etDescription, etExperience, 
                              etAddress, etCity, etPhone;
    private AutoCompleteTextView acCategory;
    private SwitchCompat switchAvailability;
    private MaterialButton btnSelectImage, btnSave;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private String providerId;
    private Uri selectedImageUri;
    private String currentImageUrl = "";
    private final String[] categories = {
            "Plumbing", "Electrical", "Carpentry", "Painting", 
            "Cleaning", "AC Repair", "Appliance Repair", "Pest Control"
    };
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String> imagePickerLauncher;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_profile);
        // Initialize ActivityResultLauncher here (must be after super.onCreate())
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        if (ivBusinessImage != null) {
                            ivBusinessImage.setImageURI(uri);
                        }
                    }
                });
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        providerId = user.getUid();
        setupToolbar();
        initViews();
        loadProfile();
    }
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Business Profile");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }
    private void initViews() {
        ivBusinessImage = findViewById(R.id.ivBusinessImage);
        etBusinessName = findViewById(R.id.etBusinessName);
        etDescription = findViewById(R.id.etDescription);
        acCategory = findViewById(R.id.acCategory);
        etExperience = findViewById(R.id.etExperience);
        etAddress = findViewById(R.id.etAddress);
        etCity = findViewById(R.id.etCity);
        etPhone = findViewById(R.id.etPhone);
        switchAvailability = findViewById(R.id.switchAvailability);
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnSave = findViewById(R.id.btnSave);
        progressBar = findViewById(R.id.progressBar);
        if (acCategory != null) {
            ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, categories);
            acCategory.setAdapter(categoryAdapter);
        }
        if (btnSelectImage != null) {
            btnSelectImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        }
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveProfile());
        }
    }
    private void loadProfile() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        String url = SUPABASE_URL + "/rest/v1/provider_profiles" +
                "?firebase_uid=eq." + providerId +
                "&select=*" +
                "&limit=1";
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    Toast.makeText(BusinessProfileActivity.this,
                            "Failed to load profile: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                });
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                int code = response.code();
                response.close();
                mainHandler.post(() -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    if (code == 200) {
                        parseProfile(body);
                    } else {
                        Toast.makeText(BusinessProfileActivity.this, 
                                "Error: HTTP " + code, 
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    private void parseProfile(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() > 0) {
                JSONObject profile = arr.getJSONObject(0);
                String businessName = profile.optString("business_name", "");
                String description = profile.optString("description", "");
                String category = profile.optString("category", "");
                String experience = profile.optString("experience", "");
                String address = profile.optString("address", "");
                String city = profile.optString("city", "");
                String phone = profile.optString("phone", "");
                currentImageUrl = profile.optString("business_image_url", "");
                boolean isAvailable = profile.optBoolean("is_available", true);
                if (etBusinessName != null) etBusinessName.setText(businessName);
                if (etDescription != null) etDescription.setText(description);
                if (acCategory != null) acCategory.setText(category, false);
                if (etExperience != null) etExperience.setText(experience);
                if (etAddress != null) etAddress.setText(address);
                if (etCity != null) etCity.setText(city);
                if (etPhone != null) etPhone.setText(phone);
                if (switchAvailability != null) switchAvailability.setChecked(isAvailable);
                if (!currentImageUrl.isEmpty() && ivBusinessImage != null) {
                    Glide.with(BusinessProfileActivity.this)
                            .load(currentImageUrl)
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .into(ivBusinessImage);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
            Toast.makeText(this, "Error parsing profile", Toast.LENGTH_SHORT).show();
        }
    }
    private void saveProfile() {
        String businessName = getText(etBusinessName);
        String description = getText(etDescription);
        String category = acCategory != null ? acCategory.getText().toString().trim() : "";
        String experience = getText(etExperience);
        String address = getText(etAddress);
        String city = getText(etCity);
        String phone = getText(etPhone);
        boolean isAvailable = switchAvailability != null && switchAvailability.isChecked();
        if (businessName.isEmpty()) {
            if (etBusinessName != null) {
                etBusinessName.setError("Business name required");
                etBusinessName.requestFocus();
            }
            return;
        }
        if (category.isEmpty()) {
            if (acCategory != null) {
                acCategory.setError("Category required");
                acCategory.requestFocus();
            }
            return;
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (btnSave != null) {
            btnSave.setEnabled(false);
        }
        if (selectedImageUri != null) {
            uploadImageAndSave(businessName, description, category, experience, 
                              address, city, phone, isAvailable);
        } else {
            updateProfile(businessName, description, category, experience, 
                         address, city, phone, isAvailable, currentImageUrl);
        }
    }
    private void uploadImageAndSave(String businessName, String description, String category,
                                    String experience, String address, String city, String phone,
                                    boolean isAvailable) {
        SupabaseStorageHelper.uploadFile(
                this,
                selectedImageUri,
                SupabaseStorageHelper.BUCKET_BUSINESS_IMAGES,
                "provider_" + providerId,
                new SupabaseStorageHelper.UploadCallback() {
                    @Override
                    public void onSuccess(String publicUrl) {
                        updateProfile(businessName, description, category, experience,
                                     address, city, phone, isAvailable, publicUrl);
                    }
                    @Override
                    public void onError(String error) {
                        mainHandler.post(() -> {
                            if (progressBar != null) {
                                progressBar.setVisibility(View.GONE);
                            }
                            if (btnSave != null) {
                                btnSave.setEnabled(true);
                            }
                            Toast.makeText(BusinessProfileActivity.this,
                                    "Image upload failed: " + error,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }
    private void updateProfile(String businessName, String description, String category,
                              String experience, String address, String city, String phone,
                              boolean isAvailable, String imageUrl) {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", providerId);
            body.put("business_name", businessName != null ? businessName : "");
            body.put("description", description != null ? description : "");
            body.put("category", category != null ? category : "");
            body.put("experience", experience != null ? experience : "");
            body.put("address", address != null ? address : "");
            body.put("city", city != null ? city : "");
            body.put("phone", phone != null ? phone : "");
            body.put("is_available", isAvailable);
            if (imageUrl != null && !imageUrl.isEmpty()) {
                body.put("business_image_url", imageUrl);
            }
            String url = SUPABASE_URL + "/rest/v1/provider_profiles";
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                    .build();
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> {
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                        if (btnSave != null) {
                            btnSave.setEnabled(true);
                        }
                        Toast.makeText(BusinessProfileActivity.this,
                                "Update failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    response.close();
                    mainHandler.post(() -> {
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                        if (btnSave != null) {
                            btnSave.setEnabled(true);
                        }
                        if (code == 201 || code == 200 || code == 204) {
                            Toast.makeText(BusinessProfileActivity.this,
                                    "Profile updated successfully!",
                                    Toast.LENGTH_SHORT).show();
                            selectedImageUri = null;
                            loadProfile();
                        } else {
                            Toast.makeText(BusinessProfileActivity.this,
                                    "Update failed: HTTP " + code,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Update error: " + e.getMessage());
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            if (btnSave != null) {
                btnSave.setEnabled(true);
            }
            Toast.makeText(this, "Error updating profile", Toast.LENGTH_SHORT).show();
        }
    }
    private String getText(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) return "";
        return editText.getText().toString().trim();
    }
}
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
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.quickfix.adapters.BusinessMediaAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
public class BusinessProfileActivity extends BaseActivity {
    private static final String TAG = "BusinessProfile";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_MEDIA_ITEMS = 10;

    private ImageView ivBusinessImage;
    private TextInputEditText etBusinessName, etDescription, etExperience, 
                              etAddress, etCity, etPhone;
    private AutoCompleteTextView acCategory;
    private SwitchCompat switchAvailability;
    private MaterialButton btnSelectImage, btnSave, btnAddMedia, btnEditProfile;
    private TextView tvMediaCount;
    private RecyclerView rvMediaGallery;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private String providerId;
    private Uri selectedImageUri;
    private String currentImageUrl = "";

    private BusinessMediaAdapter mediaAdapter;
    private List<BusinessMediaItem> uploadedMedia = new ArrayList<>();

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
    private ActivityResultLauncher<String> mediaPickerLauncher;

    private boolean isUploadingMedia = false;
    private boolean isEditMode = false;
    private String lockedCategory = "";

    private interface PendingMediaUploadCallback {
        void onComplete(int successCount, int failedCount, String firstError);
    }

    private interface MediaDbSaveCallback {
        void onComplete(boolean success, String error);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_profile);

        // Initialize ActivityResultLauncher for profile image
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

        // Initialize ActivityResultLauncher for media gallery
        mediaPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && mediaAdapter != null) {
                        if (getTotalMediaCount() < MAX_MEDIA_ITEMS) {
                            mediaAdapter.addLocalMedia(uri);
                            updateMediaCount();
                        } else {
                            Toast.makeText(this, "Maximum " + MAX_MEDIA_ITEMS + " media items allowed",
                                    Toast.LENGTH_SHORT).show();
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
        setupMediaGallery();
        loadProfile();
        loadBusinessMedia();
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
        btnAddMedia = findViewById(R.id.btnAddMedia);
        btnSave = findViewById(R.id.btnSave);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        tvMediaCount = findViewById(R.id.tvMediaCount);
        rvMediaGallery = findViewById(R.id.rvMediaGallery);
        progressBar = findViewById(R.id.progressBar);

        if (acCategory != null) {
            ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, categories);
            acCategory.setAdapter(categoryAdapter);
            acCategory.setKeyListener(null);
            acCategory.setEnabled(false);
            acCategory.setFocusable(false);
            acCategory.setFocusableInTouchMode(false);
            acCategory.setClickable(false);
        }
        if (btnSelectImage != null) {
            btnSelectImage.setOnClickListener(v -> {
                if (!isEditMode) {
                    return;
                }
                imagePickerLauncher.launch("image/*");
            });
        }
        if (btnAddMedia != null) {
            btnAddMedia.setOnClickListener(v -> {
                if (!isEditMode) {
                    return;
                }
                showMediaPickerDialog();
            });
        }
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveProfile());
        }
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> setEditMode(true));
        }

        setEditMode(false);
    }

    private void setEditMode(boolean enabled) {
        isEditMode = enabled;

        if (etBusinessName != null) etBusinessName.setEnabled(enabled);
        if (etDescription != null) etDescription.setEnabled(enabled);
        if (etExperience != null) etExperience.setEnabled(enabled);
        if (etAddress != null) etAddress.setEnabled(enabled);
        if (etCity != null) etCity.setEnabled(enabled);
        if (etPhone != null) etPhone.setEnabled(enabled);
        if (switchAvailability != null) switchAvailability.setEnabled(enabled);
        if (btnSelectImage != null) btnSelectImage.setEnabled(enabled);
        if (btnAddMedia != null) btnAddMedia.setEnabled(enabled);

        // Category is immutable after onboarding.
        if (acCategory != null) {
            acCategory.setEnabled(false);
            acCategory.setFocusable(false);
            acCategory.setFocusableInTouchMode(false);
            acCategory.setClickable(false);
        }

        if (btnEditProfile != null) {
            btnEditProfile.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
        if (btnSave != null) {
            btnSave.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (mediaAdapter != null) {
            mediaAdapter.setEditMode(enabled);
        }
    }

    private void setupMediaGallery() {
        mediaAdapter = new BusinessMediaAdapter(this, new BusinessMediaAdapter.MediaActionListener() {
            @Override
            public void onDelete(Object item, int position) {
                if (!isEditMode) {
                    return;
                }
                confirmDeleteMedia(item, position);
            }

            @Override
            public void onView(Object item) {
                // TODO: Implement fullscreen media viewer
                if (item instanceof BusinessMediaItem) {
                    BusinessMediaItem media = (BusinessMediaItem) item;
                    Toast.makeText(BusinessProfileActivity.this,
                            "View: " + media.mediaType, Toast.LENGTH_SHORT).show();
                }
            }
        });

        rvMediaGallery.setLayoutManager(new GridLayoutManager(this, 3));
        rvMediaGallery.setAdapter(mediaAdapter);
        mediaAdapter.setEditMode(isEditMode);
    }

    private void showMediaPickerDialog() {
        if (!isEditMode) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Add Media")
                .setItems(new String[]{"Photo", "Video"}, (dialog, which) -> {
                    if (which == 0) {
                        mediaPickerLauncher.launch("image/*");
                    } else {
                        mediaPickerLauncher.launch("video/*");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateMediaCount() {
        int total = getTotalMediaCount();
        if (tvMediaCount != null) {
            tvMediaCount.setText(total + "/" + MAX_MEDIA_ITEMS);
        }
    }

    private int getTotalMediaCount() {
        return (mediaAdapter != null ? mediaAdapter.getItemCount() : 0);
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
                lockedCategory = category;
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

    private void loadBusinessMedia() {
        String url = SUPABASE_URL + "/rest/v1/business_media" +
                "?provider_id=eq." + providerId +
                "&order=display_order.asc,created_at.asc" +
                "&select=*";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to load media: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                int code = response.code();
                response.close();

                if (code == 200) {
                    mainHandler.post(() -> parseBusinessMedia(body));
                }
            }
        });
    }

    private void parseBusinessMedia(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            uploadedMedia.clear();

            for (int i = 0; i < arr.length(); i++) {
                BusinessMediaItem item = BusinessMediaItem.fromJson(arr.getJSONObject(i));
                if (item != null) {
                    uploadedMedia.add(item);
                }
            }

            if (mediaAdapter != null) {
                mediaAdapter.setUploadedMedia(uploadedMedia);
                updateMediaCount();
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse media error: " + e.getMessage());
        }
    }

    private void confirmDeleteMedia(Object item, int position) {
        if (!isEditMode) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete Media")
                .setMessage("Are you sure you want to delete this media?")
                .setPositiveButton("Delete", (d, w) -> deleteMedia(item, position))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteMedia(Object item, int position) {
        if (item instanceof BusinessMediaItem) {
            BusinessMediaItem media = (BusinessMediaItem) item;
            deleteMediaFromServer(media, position);
        } else if (item instanceof Uri) {
            mediaAdapter.removeLocalMedia((Uri) item);
            updateMediaCount();
        }
    }

    private void deleteMediaFromServer(BusinessMediaItem media, int position) {
        String url = SUPABASE_URL + "/rest/v1/business_media?id=eq." + media.id;

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> Toast.makeText(BusinessProfileActivity.this,
                        "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                response.close();

                mainHandler.post(() -> {
                    if (code == 200 || code == 204) {
                        uploadedMedia.remove(media);
                        mediaAdapter.setUploadedMedia(uploadedMedia);
                        updateMediaCount();
                        Toast.makeText(BusinessProfileActivity.this,
                                "Media deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(BusinessProfileActivity.this,
                                "Delete failed: HTTP " + code, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void saveProfile() {
        if (!isEditMode) {
            return;
        }

        String businessName = getText(etBusinessName);
        String description = getText(etDescription);
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

        // Category is immutable after onboarding and must already exist.
        if (lockedCategory == null || lockedCategory.trim().isEmpty()) {
            Toast.makeText(this, "Category is locked and cannot be changed here.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (btnSave != null) {
            btnSave.setEnabled(false);
        }

        if (isUploadingMedia) {
            Toast.makeText(this, "Upload already in progress, please wait...", Toast.LENGTH_SHORT).show();
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (btnSave != null) btnSave.setEnabled(true);
            return;
        }

        if (btnEditProfile != null) {
            btnEditProfile.setEnabled(false);
        }

        isUploadingMedia = true;

        // Upload pending media first, then update profile
        uploadPendingMedia((successCount, failedCount, firstError) -> {
            isUploadingMedia = false;

            if (failedCount > 0) {
                Toast.makeText(BusinessProfileActivity.this,
                        "Uploaded " + successCount + " media, failed " + failedCount +
                                (firstError != null ? ". " + firstError : ""),
                        Toast.LENGTH_LONG).show();
            }

            if (selectedImageUri != null) {
                uploadImageAndSave(businessName, description, experience,
                                  address, city, phone, isAvailable);
            } else {
                updateProfile(businessName, description, experience,
                             address, city, phone, isAvailable, currentImageUrl);
            }
        });
    }

    private void uploadPendingMedia(PendingMediaUploadCallback onComplete) {
        List<Uri> pendingUploads = mediaAdapter != null ? mediaAdapter.getPendingUploads() : new ArrayList<>();

        if (pendingUploads.isEmpty()) {
            onComplete.onComplete(0, 0, null);
            return;
        }

        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        final String[] firstError = {null};
        int total = pendingUploads.size();

        for (Uri uri : pendingUploads) {
            String mediaType = isVideo(uri) ? "video" : "image";
            String bucket = SupabaseStorageHelper.BUCKET_QUICKFIX;
            String prefix = "provider_" + providerId + "_gallery";

            SupabaseStorageHelper.uploadFile(this, uri, bucket, prefix,
                    new SupabaseStorageHelper.UploadCallback() {
                        @Override
                        public void onSuccess(String publicUrl) {
                            saveMediaToDatabase(publicUrl, mediaType, (dbSuccess, dbError) -> {
                                if (dbSuccess) {
                                    success.incrementAndGet();
                                    if (mediaAdapter != null) {
                                        mediaAdapter.removeLocalMedia(uri);
                                        updateMediaCount();
                                    }
                                } else {
                                    failed.incrementAndGet();
                                    if (firstError[0] == null) {
                                        firstError[0] = dbError;
                                    }
                                }

                                if (completed.incrementAndGet() >= total) {
                                    loadBusinessMedia();
                                    onComplete.onComplete(success.get(), failed.get(), firstError[0]);
                                }
                            });
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Media upload failed: " + error);
                            failed.incrementAndGet();
                            if (firstError[0] == null) {
                                firstError[0] = error;
                            }
                            if (completed.incrementAndGet() >= total) {
                                loadBusinessMedia();
                                onComplete.onComplete(success.get(), failed.get(), firstError[0]);
                            }
                        }
                    });
        }
    }

    private boolean isVideo(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        return mimeType != null && mimeType.startsWith("video/");
    }

    private void saveMediaToDatabase(String mediaUrl, String mediaType, MediaDbSaveCallback onComplete) {
        try {
            JSONObject body = new JSONObject();
            body.put("provider_id", providerId);
            body.put("media_url", mediaUrl);
            body.put("media_type", mediaType);
            body.put("display_order", uploadedMedia.size());

            String url = SUPABASE_URL + "/rest/v1/business_media";

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=ignore-duplicates,return=minimal")
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Save media DB failed: " + e.getMessage());
                    mainHandler.post(() -> onComplete.onComplete(false, "Failed to save media record."));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    String resp = response.body() != null ? response.body().string() : "";
                    response.close();

                    mainHandler.post(() -> {
                        // 200, 201, 204 = success; 409 = duplicate row = already saved, treat as success
                        if (code == 200 || code == 201 || code == 204 || code == 409) {
                            onComplete.onComplete(true, null);
                        } else {
                            Log.e(TAG, "Save media DB HTTP " + code + " | " + resp);
                            onComplete.onComplete(false, "Media DB save failed: HTTP " + code);
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Save media error: " + e.getMessage());
            onComplete.onComplete(false, "Failed to prepare media save.");
        }
    }
    private void uploadImageAndSave(String businessName, String description,
                                    String experience, String address, String city, String phone,
                                    boolean isAvailable) {
        SupabaseStorageHelper.uploadFile(
                this,
                selectedImageUri,
                SupabaseStorageHelper.BUCKET_QUICKFIX,
                "provider_" + providerId,
                new SupabaseStorageHelper.UploadCallback() {
                    @Override
                    public void onSuccess(String publicUrl) {
                        updateProfile(businessName, description, experience,
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
                            if (btnEditProfile != null) {
                                btnEditProfile.setEnabled(true);
                            }
                            Toast.makeText(BusinessProfileActivity.this,
                                    "Image upload failed: " + error,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }

    private void updateProfile(String businessName, String description, String experience,
                              String address, String city, String phone,
                              boolean isAvailable, String imageUrl) {
        try {
            JSONObject body = new JSONObject();
            body.put("business_name", businessName != null ? businessName : "");
            body.put("description", description != null ? description : "");
            body.put("experience", experience != null ? experience : "");
            body.put("address", address != null ? address : "");
            body.put("city", city != null ? city : "");
            body.put("phone", phone != null ? phone : "");
            body.put("is_available", isAvailable);
            if (imageUrl != null && !imageUrl.isEmpty()) {
                body.put("business_image_url", imageUrl);
            }

            // Update existing profile row only; do not allow category mutation in this flow.
            String url = SUPABASE_URL + "/rest/v1/provider_profiles?firebase_uid=eq." + providerId;
            Request request = new Request.Builder()
                    .url(url)
                    .patch(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
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
                        if (btnEditProfile != null) {
                            btnEditProfile.setEnabled(true);
                        }
                        Toast.makeText(BusinessProfileActivity.this,
                                "Update failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    String resp = response.body() != null ? response.body().string() : "";
                    response.close();
                    mainHandler.post(() -> {
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                        if (btnSave != null) {
                            btnSave.setEnabled(true);
                        }
                        if (btnEditProfile != null) {
                            btnEditProfile.setEnabled(true);
                        }
                        if (code == 200 || code == 204) {
                            Toast.makeText(BusinessProfileActivity.this,
                                    "Profile updated successfully!",
                                    Toast.LENGTH_SHORT).show();
                            selectedImageUri = null;
                            setEditMode(false);
                            loadProfile();
                        } else {
                            Log.e(TAG, "Profile update HTTP " + code + " | " + resp);
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
            if (btnEditProfile != null) {
                btnEditProfile.setEnabled(true);
            }
            Toast.makeText(this, "Error updating profile", Toast.LENGTH_SHORT).show();
        }
    }

    private String getText(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) return "";
        return editText.getText().toString().trim();
    }
}


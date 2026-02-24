package com.example.quickfix;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * ContentManagementActivity — Manage categories and featured services.
 * Available to: super_admin, content_admin
 */
public class ContentManagementActivity extends AppCompatActivity {

    private static final String TAG = "ContentManagementActivity";

    private ListView categoriesListView, featuredListView;
    private Button addCategoryButton, addFeaturedButton, refreshButton;

    private FirebaseAuth mAuth;
    private String currentAdminUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_content_management);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            finish();
            return;
        }

        currentAdminUid = currentUser.getUid();
        initViews();
        loadContent();
    }

    private void initViews() {
        categoriesListView = findViewById(R.id.categoriesListView);
        featuredListView = findViewById(R.id.featuredListView);
        addCategoryButton = findViewById(R.id.addCategoryButton);
        addFeaturedButton = findViewById(R.id.addFeaturedButton);
        refreshButton = findViewById(R.id.refreshButton);

        addCategoryButton.setOnClickListener(v -> showAddCategoryDialog());
        addFeaturedButton.setOnClickListener(v -> showAddFeaturedDialog());
        refreshButton.setOnClickListener(v -> loadContent());
    }

    private void loadContent() {
        // TODO: Load categories and featured services
        Toast.makeText(this, "Loading content...", Toast.LENGTH_SHORT).show();
    }

    private void showAddCategoryDialog() {
        Toast.makeText(this, "Add category dialog", Toast.LENGTH_SHORT).show();
    }

    private void showAddFeaturedDialog() {
        Toast.makeText(this, "Add featured service dialog", Toast.LENGTH_SHORT).show();
    }

    /**
     * Add a new category
     */
    public void addCategory(String categoryName, String description) {
        // TODO: Insert into service_categories table
        Toast.makeText(this, "Category added", Toast.LENGTH_SHORT).show();
        SupabaseClient.logAdminAction(currentAdminUid, "category_added", "service_categories",
                categoryName, null);
    }

    /**
     * Add a service to featured
     */
    public void addFeaturedService(String serviceId, int featureOrder) {
        // TODO: Insert into featured_services table
        Toast.makeText(this, "Added to featured", Toast.LENGTH_SHORT).show();
        SupabaseClient.logAdminAction(currentAdminUid, "featured_added", "featured_services",
                serviceId, null);
    }

    /**
     * Remove from featured
     */
    public void removeFromFeatured(String featuredId) {
        // TODO: Delete from featured_services table
        Toast.makeText(this, "Removed from featured", Toast.LENGTH_SHORT).show();
        SupabaseClient.logAdminAction(currentAdminUid, "featured_removed", "featured_services",
                featuredId, null);
    }
}


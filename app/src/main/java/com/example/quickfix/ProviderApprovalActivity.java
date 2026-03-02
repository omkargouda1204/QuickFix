package com.example.quickfix;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.quickfix.adapters.ProviderApplicationAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.List;

public class ProviderApprovalActivity extends AppCompatActivity implements ProviderApplicationAdapter.OnActionListener {
    private ProviderApplicationAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recycler;
    private String adminUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_approval);
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) { finish(); return; }
        adminUid = me.getUid();
        swipeRefresh = findViewById(R.id.swipeRefresh);
        recycler = findViewById(R.id.recyclerApplications);
        adapter = new ProviderApplicationAdapter(this);
        adapter.setOnActionListener(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        swipeRefresh.setOnRefreshListener(this::loadData);
        loadData();
    }
    private void loadData() {
        swipeRefresh.setRefreshing(true);
        ProviderApplicationRepository.fetchApplications("pending", new ProviderApplicationRepository.ApplicationListCallback() {
            @Override
            public void onSuccess(List<ProviderApplication> applications) {
                swipeRefresh.setRefreshing(false);
                adapter.setData(applications);
            }
            @Override
            public void onError(String message) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(ProviderApprovalActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
    @Override
    public void onApprove(ProviderApplication app) {
        ProviderApplicationRepository.approveApplication(app, adminUid, new ProviderApplicationRepository.Callback() {
            @Override
            public void onSuccess() {
                Toast.makeText(ProviderApprovalActivity.this, "Approved", Toast.LENGTH_SHORT).show();
                loadData();
            }
            @Override
            public void onError(String msg) {
                Toast.makeText(ProviderApprovalActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }
    @Override
    public void onReject(ProviderApplication app) {
        // Show dialog to input rejection reason
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter rejection reason");
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Reject Application")
            .setMessage("Please provide a reason for rejection:")
            .setView(input)
            .setPositiveButton("Reject", (dialog, which) -> {
                String reason = input.getText().toString().trim();
                if (reason.isEmpty()) {
                    Toast.makeText(this, "Reason is required", Toast.LENGTH_SHORT).show();
                    return;
                }
                ProviderApplicationRepository.rejectApplication(app.id, reason, adminUid, new ProviderApplicationRepository.Callback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(ProviderApprovalActivity.this, "Rejected", Toast.LENGTH_SHORT).show();
                        loadData();
                    }
                    @Override
                    public void onError(String msg) {
                        Toast.makeText(ProviderApprovalActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    @Override
    public void onViewDetails(ProviderApplication app) {
        // TODO: Show details bottom sheet
    }
}

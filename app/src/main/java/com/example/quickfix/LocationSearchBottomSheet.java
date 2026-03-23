package com.example.quickfix;

import android.app.Activity;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.adapters.LocationSuggestionAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationSearchBottomSheet {

    public interface PermissionDelegate {
        boolean hasLocationPermission();
        void requestLocationPermission();
    }

    public interface OnLocationPickedListener {
        void onLocationPicked(LocationSuggestion suggestion);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final long SEARCH_DEBOUNCE_MS = 220L;

    public static void show(Activity activity,
                            AppLocationManager locationManager,
                            PermissionDelegate permissionDelegate,
                            OnLocationPickedListener listener) {
        BottomSheetDialog sheet = new BottomSheetDialog(activity, R.style.Theme_QuickFix_BottomSheet);
        View root = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_location_picker, null);
        sheet.setContentView(root);

        EditText etSearch = root.findViewById(R.id.etLocationSearch);
        ImageView btnClearSearch = root.findViewById(R.id.btnClearLocationSearch);
        ImageView btnClose = root.findViewById(R.id.btnCloseLocationSheet);
        LinearLayout btnLiveLocation = root.findViewById(R.id.btnUseLiveLocation);
        TextView tvLiveSub = root.findViewById(R.id.tvLiveLocationSub);
        ProgressBar liveProgress = root.findViewById(R.id.locationProgress);
        TextView tvEmpty = root.findViewById(R.id.tvLocationEmpty);
        LinearLayout btnClearLocation = root.findViewById(R.id.btnClearLocation);
        RecyclerView rvResults = root.findViewById(R.id.rvLocationResults);

        Handler main = new Handler(Looper.getMainLooper());
        final Runnable[] pendingSearch = new Runnable[]{null};

        List<LocationSuggestion> allCities = new ArrayList<>();
        for (String city : AppLocationManager.CITY_LIST) {
            allCities.add(new LocationSuggestion(city, "", "", 0d, 0d, false, 0f));
        }

        LocationSuggestionAdapter adapter = new LocationSuggestionAdapter(item -> {
            locationManager.saveCity(item.city);
            if (listener != null) {
                listener.onLocationPicked(new LocationSuggestion(item.city, "", "", 0d, 0d, false, 0f));
            }
            sheet.dismiss();
        });
        rvResults.setLayoutManager(new LinearLayoutManager(activity));
        rvResults.setAdapter(adapter);
        adapter.submit(allCities);

        String savedLabel = locationManager.getSavedLocationLabel();
        if (tvLiveSub != null && !savedLabel.isEmpty()) {
            tvLiveSub.setText("Current: " + savedLabel);
        }

        if (btnClearLocation != null) {
            btnClearLocation.setVisibility(savedLabel.isEmpty() ? View.GONE : View.VISIBLE);
            btnClearLocation.setOnClickListener(v -> {
                locationManager.clearCity();
                if (tvLiveSub != null) {
                    tvLiveSub.setText("Detect via GPS automatically");
                }
                if (listener != null) {
                    listener.onLocationPicked(new LocationSuggestion("", "", "", 0d, 0d, false, 0f));
                }
                sheet.dismiss();
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> sheet.dismiss());
        }

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                if (etSearch != null) {
                    etSearch.setText("");
                }
            });
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String query = s == null ? "" : s.toString().trim().toLowerCase(Locale.getDefault());
                    if (btnClearSearch != null) {
                        btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                    }

                    if (pendingSearch[0] != null) {
                        main.removeCallbacks(pendingSearch[0]);
                        pendingSearch[0] = null;
                    }

                    pendingSearch[0] = () -> {
                        List<LocationSuggestion> filtered = new ArrayList<>();
                        if (query.isEmpty()) {
                            filtered.addAll(allCities);
                        } else {
                            for (LocationSuggestion item : allCities) {
                                if (item.city.toLowerCase(Locale.getDefault()).contains(query)) {
                                    filtered.add(item);
                                }
                            }
                        }
                        adapter.submit(filtered);
                        if (tvEmpty != null) {
                            tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    };
                    main.postDelayed(pendingSearch[0], SEARCH_DEBOUNCE_MS);
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        if (btnLiveLocation != null) {
            btnLiveLocation.setOnClickListener(v -> {
                if (!permissionDelegate.hasLocationPermission()) {
                    permissionDelegate.requestLocationPermission();
                    return;
                }
                if (liveProgress != null) {
                    liveProgress.setVisibility(View.VISIBLE);
                }
                locationManager.fetchLiveLocation(new AppLocationManager.LiveLocationCallback() {
                    @Override
                    public void onSuccess(String city, String area, double lat, double lng) {
                        if (liveProgress != null) {
                            liveProgress.setVisibility(View.GONE);
                        }
                        locationManager.saveLocation(city, area, lat, lng);
                        if (listener != null) {
                            listener.onLocationPicked(new LocationSuggestion(city, area, "", lat, lng, true, 0f));
                        }
                        sheet.dismiss();
                    }

                    @Override
                    public void onFailure(String reason) {
                        if (liveProgress != null) {
                            liveProgress.setVisibility(View.GONE);
                        }
                        if (tvLiveSub != null && reason != null && !reason.isEmpty()) {
                            tvLiveSub.setText(reason);
                        }
                    }
                });
            });
        }

        if (tvEmpty != null) {
            tvEmpty.setVisibility(allCities.isEmpty() ? View.VISIBLE : View.GONE);
        }
        sheet.show();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

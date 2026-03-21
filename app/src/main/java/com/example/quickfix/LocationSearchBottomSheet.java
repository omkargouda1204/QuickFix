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
        ProgressBar resultsProgress = root.findViewById(R.id.locationResultsProgress);
        TextView tvEmpty = root.findViewById(R.id.tvLocationEmpty);
        TextView tvError = root.findViewById(R.id.tvLocationError);
        LinearLayout btnClearLocation = root.findViewById(R.id.btnClearLocation);
        RecyclerView rvResults = root.findViewById(R.id.rvLocationResults);

        Handler main = new Handler(Looper.getMainLooper());
        List<LocationSuggestion> nearbyCache = new ArrayList<>();
        final double[] origin = new double[]{Double.NaN, Double.NaN};
        final int[] requestToken = new int[]{0};
        final Runnable[] pendingSearch = new Runnable[]{null};

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

        LocationSuggestionAdapter adapter = new LocationSuggestionAdapter(item -> {
            locationManager.saveLocation(item.city, item.area, item.lat, item.lng);
            if (listener != null) {
                listener.onLocationPicked(item);
            }
            sheet.dismiss();
        });
        rvResults.setLayoutManager(new LinearLayoutManager(activity));
        rvResults.setAdapter(adapter);

        Runnable showNearbyOnly = () -> {
            adapter.submit(nearbyCache);
            renderState(resultsProgress, tvEmpty, tvError, false,
                    nearbyCache.isEmpty(), "No nearby places found yet.");
        };

        Runnable loadNearby = () -> {
            renderState(resultsProgress, tvEmpty, tvError, true, false, null);
            if (!permissionDelegate.hasLocationPermission()) {
                renderState(resultsProgress, tvEmpty, tvError, false, false,
                        "Enable location permission to see nearby places.");
                return;
            }
            if (liveProgress != null) {
                liveProgress.setVisibility(View.VISIBLE);
            }
            if (tvLiveSub != null) {
                tvLiveSub.setText("Detecting your current location...");
            }

            locationManager.fetchLiveLocation(new AppLocationManager.LiveLocationCallback() {
                @Override
                public void onSuccess(String city, String area, double lat, double lng) {
                    origin[0] = lat;
                    origin[1] = lng;
                    if (tvLiveSub != null) {
                        String label = AppLocationManager.composeLocationLabel(city, area);
                        tvLiveSub.setText(label.isEmpty() ? "GPS location detected" : "Current: " + label);
                    }
                    EXECUTOR.execute(() -> {
                        List<LocationSuggestion> nearby = buildNearbySuggestions(activity, lat, lng);
                        main.post(() -> {
                            if (liveProgress != null) {
                                liveProgress.setVisibility(View.GONE);
                            }
                            nearbyCache.clear();
                            nearbyCache.addAll(nearby);
                            showNearbyOnly.run();
                        });
                    });
                }

                @Override
                public void onFailure(String reason) {
                    if (liveProgress != null) {
                        liveProgress.setVisibility(View.GONE);
                    }
                    renderState(resultsProgress, tvEmpty, tvError, false, false,
                            reason == null || reason.isEmpty() ? "Unable to detect location." : reason);
                }
            });
        };

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
                    String query = s == null ? "" : s.toString().trim();
                    if (btnClearSearch != null) {
                        btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                    }

                    if (pendingSearch[0] != null) {
                        main.removeCallbacks(pendingSearch[0]);
                        pendingSearch[0] = null;
                    }

                    if (query.isEmpty()) {
                        requestToken[0]++;
                        showNearbyOnly.run();
                        return;
                    }

                    int thisRequest = ++requestToken[0];
                    pendingSearch[0] = () -> {
                        renderState(resultsProgress, tvEmpty, tvError, true, false, null);
                        EXECUTOR.execute(() -> {
                            List<LocationSuggestion> result = searchLocations(activity, query, nearbyCache,
                                    origin[0], origin[1]);
                            main.post(() -> {
                                if (thisRequest != requestToken[0]) {
                                    return;
                                }
                                adapter.submit(result);
                                renderState(resultsProgress, tvEmpty, tvError, false,
                                        result.isEmpty(), "No locations match your search.");
                            });
                        });
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
                    renderState(resultsProgress, tvEmpty, tvError, false, false,
                            "Grant location permission and tap again.");
                    return;
                }
                loadNearby.run();
            });
        }

        loadNearby.run();
        sheet.show();
    }

    private static void renderState(ProgressBar loading,
                                    TextView empty,
                                    TextView error,
                                    boolean showLoading,
                                    boolean showEmpty,
                                    String errorText) {
        if (loading != null) {
            loading.setVisibility(showLoading ? View.VISIBLE : View.GONE);
        }
        if (empty != null) {
            empty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        }
        if (error != null) {
            boolean hasError = errorText != null && !errorText.isEmpty() && !showLoading;
            error.setVisibility(hasError ? View.VISIBLE : View.GONE);
            if (hasError) {
                error.setText(errorText);
            }
        }
    }

    private static List<LocationSuggestion> searchLocations(Activity activity,
                                                            String query,
                                                            List<LocationSuggestion> nearby,
                                                            double originLat,
                                                            double originLng) {
        String q = query.toLowerCase(Locale.getDefault());
        List<LocationSuggestion> output = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (LocationSuggestion item : nearby) {
            if (item.searchableText().contains(q)) {
                output.add(item);
                seen.add(uniqueKey(item.city, item.area, item.state));
            }
        }

        for (String city : AppLocationManager.CITY_LIST) {
            String safeCity = safe(city);
            if (!safeCity.toLowerCase(Locale.getDefault()).contains(q)) {
                continue;
            }
            String key = uniqueKey(safeCity, "", "");
            if (seen.contains(key)) {
                continue;
            }
            output.add(new LocationSuggestion(safeCity, "", "", 0d, 0d, false, 0f));
            seen.add(key);
            if (output.size() >= 20) {
                return output;
            }
        }

        if (!Geocoder.isPresent()) {
            return output;
        }

        try {
            Geocoder geocoder = new Geocoder(activity, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocationName(query + ", India", 20);
            if (addresses == null) {
                return output;
            }
            for (Address a : addresses) {
                String city = bestCity(a);
                String area = bestArea(a);
                String state = safe(a.getAdminArea());
                if (city.isEmpty() && area.isEmpty()) {
                    continue;
                }
                String key = uniqueKey(city, area, state);
                if (seen.contains(key)) {
                    continue;
                }
                float distance = 0f;
                if (!Double.isNaN(originLat) && !Double.isNaN(originLng)
                        && a.hasLatitude() && a.hasLongitude()) {
                    float[] d = new float[1];
                    Location.distanceBetween(originLat, originLng,
                            a.getLatitude(), a.getLongitude(), d);
                    distance = d[0] / 1000f;
                }
                output.add(new LocationSuggestion(city, area, state,
                        a.hasLatitude() ? a.getLatitude() : 0d,
                        a.hasLongitude() ? a.getLongitude() : 0d,
                        false, distance));
                seen.add(key);
                if (output.size() >= 20) {
                    break;
                }
            }
        } catch (Exception ignored) {
        }

        return output;
    }

    private static List<LocationSuggestion> buildNearbySuggestions(Activity activity,
                                                                   double lat,
                                                                   double lng) {
        List<LocationSuggestion> list = new ArrayList<>();
        if (!Geocoder.isPresent()) {
            return list;
        }
        Set<String> seen = new HashSet<>();
        Geocoder geocoder = new Geocoder(activity, Locale.getDefault());

        double latOffset = 10.0 / 111.0;
        double lngFactor = 111.0 * Math.max(0.2, Math.cos(Math.toRadians(lat)));
        double lngOffset = 10.0 / lngFactor;

        double[][] points = new double[][]{
                {lat, lng},
                {lat + latOffset, lng},
                {lat - latOffset, lng},
                {lat, lng + lngOffset},
                {lat, lng - lngOffset},
                {lat + latOffset, lng + lngOffset},
                {lat + latOffset, lng - lngOffset},
                {lat - latOffset, lng + lngOffset},
                {lat - latOffset, lng - lngOffset}
        };

        for (double[] point : points) {
            try {
                List<Address> addresses = geocoder.getFromLocation(point[0], point[1], 1);
                if (addresses == null || addresses.isEmpty()) {
                    continue;
                }
                Address a = addresses.get(0);
                String city = bestCity(a);
                String area = bestArea(a);
                String state = safe(a.getAdminArea());
                if (city.isEmpty() && area.isEmpty()) {
                    continue;
                }
                String key = uniqueKey(city, area, state);
                if (seen.contains(key)) {
                    continue;
                }
                float[] d = new float[1];
                Location.distanceBetween(lat, lng, a.getLatitude(), a.getLongitude(), d);
                list.add(new LocationSuggestion(city, area, state,
                        a.getLatitude(), a.getLongitude(), true, d[0] / 1000f));
                seen.add(key);
            } catch (Exception ignored) {
            }
        }

        java.util.Collections.sort(list, (a, b) -> Float.compare(a.distanceKm, b.distanceKm));
        return list;
    }

    private static String bestCity(Address a) {
        String city = safe(a.getLocality());
        if (city.isEmpty()) {
            city = safe(a.getSubAdminArea());
        }
        if (city.isEmpty()) {
            city = safe(a.getAdminArea());
        }
        return city;
    }

    private static String bestArea(Address a) {
        String area = safe(a.getSubLocality());
        if (area.isEmpty()) {
            area = safe(a.getFeatureName());
        }
        if (area.isEmpty()) {
            area = safe(a.getThoroughfare());
        }
        return area;
    }

    private static String uniqueKey(String city, String area, String state) {
        return safe(city).toLowerCase(Locale.getDefault()) + "|"
                + safe(area).toLowerCase(Locale.getDefault()) + "|"
                + safe(state).toLowerCase(Locale.getDefault());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}


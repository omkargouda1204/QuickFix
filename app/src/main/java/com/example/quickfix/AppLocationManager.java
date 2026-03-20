package com.example.quickfix;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.List;
import java.util.Locale;

/**
 * AppLocationManager — GPS live location + SharedPreferences storage.
 * Used globally to filter providers/services by city.
 */
public class AppLocationManager {

    private static final String TAG          = "AppLocationManager";
    public  static final String PREFS_NAME   = "quickfix_prefs";
    public  static final String KEY_CITY     = "selected_city";
    public  static final String KEY_LAT      = "selected_lat";
    public  static final String KEY_LNG      = "selected_lng";

    /** 100+ common Indian cities for manual picker. */
    public static final String[] CITY_LIST = {
        "Mumbai", "Delhi", "Bangalore", "Hyderabad", "Ahmedabad",
        "Chennai", "Kolkata", "Pune", "Jaipur", "Lucknow",
        "Kanpur", "Nagpur", "Indore", "Thane", "Bhopal",
        "Visakhapatnam", "Pimpri-Chinchwad", "Patna", "Vadodara", "Ghaziabad",
        "Ludhiana", "Agra", "Nashik", "Faridabad", "Meerut",
        "Rajkot", "Kalyan", "Vasai-Virar", "Varanasi", "Srinagar",
        "Aurangabad", "Dhanbad", "Amritsar", "Navi Mumbai", "Allahabad",
        "Ranchi", "Coimbatore", "Jabalpur", "Gwalior", "Vijayawada",
        "Jodhpur", "Madurai", "Raipur", "Kota", "Guwahati",
        "Chandigarh", "Solapur", "Hubli", "Tiruchirappalli", "Bareilly",
        "Mysore", "Tiruppur", "Gurgaon", "Noida", "Moradabad",
        "Jalandhar", "Bhubaneswar", "Salem", "Warangal", "Guntur",
        "Aligarh", "Jamalpur", "Dehradun", "Surat", "Bikaner",
        "Firozabad", "Jamshedpur", "Asansol", "Bhilai", "Cuttack",
        "Kochi", "Siliguri", "Durgapur", "Kolhapur", "Ajmer",
        "Thiruvananthapuram", "Mangalore", "Udaipur", "Bellary", "Nellore",
        "Jammu", "Shimla", "Puducherry", "Tirupati", "Karnal"
    };

    private final Context ctx;
    private final SharedPreferences prefs;

    public AppLocationManager(Context context) {
        this.ctx   = context.getApplicationContext();
        this.prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Returns currently saved city (or empty string). */
    public String getSavedCity() {
        return prefs.getString(KEY_CITY, "");
    }

    /** Saves city manually. */
    public void saveCity(String city) {
        prefs.edit().putString(KEY_CITY, city).apply();
    }

    /** Saves city with coordinates. */
    public void saveLocation(String city, double lat, double lng) {
        prefs.edit()
                .putString(KEY_CITY, city == null ? "" : city)
                .putString(KEY_LAT, String.valueOf(lat))
                .putString(KEY_LNG, String.valueOf(lng))
                .apply();
    }

    public double getSavedLat() {
        try {
            return Double.parseDouble(prefs.getString(KEY_LAT, "NaN"));
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    public double getSavedLng() {
        try {
            return Double.parseDouble(prefs.getString(KEY_LNG, "NaN"));
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    public interface LiveLocationCallback {
        void onSuccess(String city, double lat, double lng);
        void onFailure(String reason);
    }

    /**
     * Gets current GPS location, reverse-geocodes it to a city name, saves to prefs.
     */
    public void fetchLiveLocation(LiveLocationCallback callback) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            callback.onFailure("Location permission not granted");
            return;
        }

        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(ctx);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        // Fallback to last known
                        client.getLastLocation().addOnSuccessListener(last -> {
                            if (last != null) resolveCity(last, callback, mainHandler);
                            else callback.onFailure("Unable to get location. Please try again.");
                        }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                        return;
                    }
                    resolveCity(location, callback, mainHandler);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
        } catch (SecurityException e) {
            callback.onFailure("Permission denied: " + e.getMessage());
        }
    }

    private void resolveCity(Location location, LiveLocationCallback callback, Handler mainHandler) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();

        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(ctx, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                String city = "";
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    // Try city → subAdminArea → adminArea
                    city = addr.getLocality();
                    if (city == null || city.isEmpty()) city = addr.getSubAdminArea();
                    if (city == null || city.isEmpty()) city = addr.getAdminArea();
                    if (city == null) city = "";
                }
                final String finalCity = city;
                prefs.edit()
                    .putString(KEY_CITY, finalCity)
                    .putString(KEY_LAT,  String.valueOf(lat))
                    .putString(KEY_LNG,  String.valueOf(lng))
                    .apply();
                mainHandler.post(() -> callback.onSuccess(finalCity, lat, lng));
            } catch (Exception e) {
                Log.e(TAG, "Geocoder failed: " + e.getMessage());
                // Still save coordinates even if geocoder fails
                prefs.edit()
                    .putString(KEY_LAT, String.valueOf(lat))
                    .putString(KEY_LNG, String.valueOf(lng))
                    .apply();
                mainHandler.post(() -> callback.onSuccess("", lat, lng));
            }
        }).start();
    }

    /** Clears saved location. */
    public void clearCity() {
        prefs.edit()
                .remove(KEY_CITY)
                .remove(KEY_LAT)
                .remove(KEY_LNG)
                .apply();
    }
}

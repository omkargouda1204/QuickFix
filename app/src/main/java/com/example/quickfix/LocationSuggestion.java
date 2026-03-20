package com.example.quickfix;

public class LocationSuggestion {
    public final String city;
    public final String state;
    public final double lat;
    public final double lng;
    public final boolean nearby;
    public final float distanceKm;

    public LocationSuggestion(String city, String state, double lat, double lng,
                              boolean nearby, float distanceKm) {
        this.city = city == null ? "" : city;
        this.state = state == null ? "" : state;
        this.lat = lat;
        this.lng = lng;
        this.nearby = nearby;
        this.distanceKm = distanceKm;
    }

    public String displayTitle() {
        return state.isEmpty() ? city : city + ", " + state;
    }
}


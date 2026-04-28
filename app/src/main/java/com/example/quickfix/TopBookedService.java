package com.example.quickfix;

public class TopBookedService {
    public String category;
    public int totalBookings;
    public double startingPrice;
    public float rating;

    public TopBookedService(String category, int totalBookings, double startingPrice, float rating) {
        this.category = category;
        this.totalBookings = totalBookings;
        this.startingPrice = startingPrice;
        this.rating = rating;
    }
}

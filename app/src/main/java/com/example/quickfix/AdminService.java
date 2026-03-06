package com.example.quickfix;

/**
 * Data model for a service — matches the actual Supabase `services` table:
 *   id, provider_uid, title, description, category, price, price_unit,
 *   duration_mins, is_active, created_at
 */
public class AdminService {
    public String  id;
    public String  providerUid;   // provider_uid
    public String  title;         // title  (was "name" — does NOT exist in DB)
    public String  category;      // category (plain string, no FK)
    public String  description;
    public double  price;         // price   (was "base_price" — does NOT exist in DB)
    public String  priceUnit;     // price_unit  (e.g. "per hour", "fixed")
    public int     durationMins;  // duration_mins
    public boolean isActive;      // is_active
    public String  createdAt;     // created_at


    public AdminService() {}
}

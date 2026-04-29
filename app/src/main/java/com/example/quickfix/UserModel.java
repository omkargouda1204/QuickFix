package com.example.quickfix;

/**
 * UserModel — plain data class for a row in public.users
 */
public class UserModel {
    public String id;
    public String firebaseUid;
    public String name;
    public String email;
    public String phone;
    public String city;
    public String pincode;
    public String address;
    public String profileImage;
    public boolean isUser;
    public boolean isServiceProvider;
    public boolean isAdmin;
    public boolean isVerified;
    public boolean isActive;        // suspend/activate — ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE
    public String  createdAt;
    public String  updatedAt;
    public boolean darkMode;
    public String language;

    public String getRoleLabel() {
        if (isAdmin)           return "Admin";
        if (isServiceProvider) return "Provider";
        return "User";
    }

    /** Role colour as ARGB int */
    public int getRoleColor() {
        if (isAdmin)           return 0xFF8E24AA;
        if (isServiceProvider) return 0xFF00897B;
        return 0xFF1E88E5;
    }

    public UserModel copy() {
        UserModel u = new UserModel();
        u.id = id; u.firebaseUid = firebaseUid; u.name = name;
        u.email = email; u.phone = phone;
        u.city = city;
        u.pincode = pincode;
        u.address = address;
        u.profileImage = profileImage;
        u.isUser = isUser; u.isServiceProvider = isServiceProvider;
        u.isAdmin = isAdmin; u.isVerified = isVerified;
        u.isActive = isActive; u.createdAt = createdAt; u.updatedAt = updatedAt;
        u.darkMode = darkMode;
        u.language = language;
        return u;
    }
}

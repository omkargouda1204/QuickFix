package com.example.quickfix.api.model;

import com.google.gson.annotations.SerializedName;

/**
 * DTO for admin_settings table.
 */
public class AdminSettingsDto {
    public String id;

    @SerializedName("push_notifications")
    public Boolean pushNotifications;

    @SerializedName("email_notifications")
    public Boolean emailNotifications;

    @SerializedName("admin_alerts")
    public Boolean adminAlerts;

    @SerializedName("dark_mode")
    public Boolean darkMode;

    @SerializedName("auto_approve_providers")
    public Boolean autoApproveProviders;

    @SerializedName("auto_approve")
    public Boolean autoApproveLegacy;

    @SerializedName("daily_email_digest")
    public Boolean dailyEmailDigest;

    @SerializedName("daily_digest")
    public Boolean dailyDigestLegacy;

    @SerializedName("maintenance_mode")
    public Boolean maintenanceMode;

    @SerializedName("maintenance_message")
    public String maintenanceMessage;

    @SerializedName("maintenance_end_time")
    public String maintenanceEndTime;
}


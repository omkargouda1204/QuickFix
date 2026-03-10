package com.example.quickfix;

import org.json.JSONObject;

/**
 * BusinessMediaItem — Represents a media file (image/video) for business showcase gallery
 */
public class BusinessMediaItem {
    public String id;
    public String providerId;
    public String mediaUrl;
    public String mediaType; // "image" or "video"
    public String thumbnailUrl; // For videos
    public int displayOrder;
    public String createdAt;
    public String updatedAt;

    public static BusinessMediaItem fromJson(JSONObject o) {
        if (o == null) return null;
        BusinessMediaItem item = new BusinessMediaItem();
        item.id = str(o, "id");
        item.providerId = str(o, "provider_id");
        item.mediaUrl = str(o, "media_url");
        item.mediaType = str(o, "media_type");
        item.thumbnailUrl = str(o, "thumbnail_url");
        item.displayOrder = o.optInt("display_order", 0);
        item.createdAt = str(o, "created_at");
        item.updatedAt = str(o, "updated_at");
        return item;
    }

    private static String str(JSONObject o, String key) {
        if (o.isNull(key)) return "";
        String v = o.optString(key, "");
        return "null".equals(v) ? "" : v;
    }

    public boolean isVideo() {
        return "video".equalsIgnoreCase(mediaType);
    }

    public boolean isImage() {
        return "image".equalsIgnoreCase(mediaType) || mediaType == null || mediaType.isEmpty();
    }
}


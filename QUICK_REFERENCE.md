# Quick Reference - Admin CRUD System

**Your Go-To Guide**

---

## 🎯 5-Minute Setup

### Step 1: Copy SQL Setup
```bash
# Copy from: SUPABASE_SETUP.sql
# Paste into: Supabase SQL Editor
# Run: Execute All
```

### Step 2: Create Storage Bucket
```
Supabase → Storage → New Bucket
- Name: banner-images
- Privacy: Private
- Size: 5MB
```

### Step 3: Update Gradle
```gradle
buildConfigField "String", "SUPABASE_URL", "\"https://xxx.supabase.co\""
buildConfigField "String", "SUPABASE_ANON_KEY", "\"xxx\""
```

### Step 4: Build & Test
```bash
./gradlew build
# Launch app, navigate to Admin Panel
```

---

## 🔧 Quick CRUD Reference

### CREATE Banner

```java
BannerManagementActivity activity = this;

// 1. User fills form
String title = "Summer Sale";
String redirectType = "category";
String redirectId = "plumbing";

// 2. Select & upload image (automatic)
Uri imageUri = selectedImageUri; // From image picker

// 3. Save (automatic calls saveBanner internally)
activity.saveBanner(title, imageUrl, redirectType, redirectId, 
    "2026-04-24", true, 1, null, dialogInstance);
```

### READ Banners

```java
// Automatic when activity launches
activity.loadBanners();

// Result: RecyclerView populated with banners
// Each banner shows: title, image, status, expiry
```

### UPDATE Banner

```java
// User clicks edit button in RecyclerView
// Dialog shows with current values
// User modifies and saves

// System calls:
activity.saveBanner(updatedTitle, updatedImageUrl, ..., 
    bannerId, dialogInstance);  // bannerId provided = UPDATE mode
```

### DELETE Banner

```java
// User clicks delete button
// Confirmation dialog appears
// User confirms

// System calls:
activity.deleteBanner(banner);
// Banner removed from database and UI refreshed
```

### TOGGLE Status

```java
// User clicks status chip
// Toggle state: active ↔ inactive

// System calls:
activity.toggleBannerActive(banner, newState);
// Status updated in database and UI
```

---

## 📊 Database Queries

### Get All Banners
```sql
SELECT * FROM banners 
WHERE deleted_at IS NULL 
ORDER BY created_at DESC;
```

### Get Active Banners
```sql
SELECT * FROM banners 
WHERE is_active = true 
  AND deleted_at IS NULL
ORDER BY priority DESC;
```

### Get Expired Banners
```sql
SELECT * FROM banners 
WHERE expiry_date < CURRENT_DATE 
  AND is_active = true
  AND deleted_at IS NULL;
```

### Get Statistics
```sql
SELECT 
  COUNT(*) as total,
  SUM(CASE WHEN is_active THEN 1 ELSE 0 END) as active,
  SUM(CASE WHEN expiry_date < CURRENT_DATE THEN 1 ELSE 0 END) as expired
FROM banners
WHERE deleted_at IS NULL;
```

---

## 🌐 API Quick Reference

### Headers (Every Request)
```
apikey: your-anon-key
Authorization: Bearer your-anon-key
Content-Type: application/json
Accept: application/json
Prefer: return=minimal
```

### Endpoints
```
GET    /rest/v1/banners
POST   /rest/v1/banners
PATCH  /rest/v1/banners?id=eq.xxx
DELETE /rest/v1/banners?id=eq.xxx

GET    /rest/v1/offers
POST   /rest/v1/offers
PATCH  /rest/v1/offers?id=eq.xxx
DELETE /rest/v1/offers?id=eq.xxx
```

### Sample Payload (Create)
```json
{
  "id": "uuid-here",
  "title": "Banner Title",
  "image_url": "https://...",
  "redirect_type": "category",
  "redirect_id": "plumbing",
  "is_active": true,
  "priority": 1,
  "expiry_date": "2026-04-24",
  "status": "active"
}
```

### Sample Payload (Update)
```json
{
  "title": "Updated Title",
  "is_active": false,
  "priority": 2
}
```

---

## ✅ Validation Checklist

Before saving, check:

**Banners:**
- [ ] Title is not empty
- [ ] Image selected (new) or provided (edit)
- [ ] Redirect type selected
- [ ] Date format correct (YYYY-MM-DD)

**Offers:**
- [ ] Title is not empty
- [ ] Offer code is not empty
- [ ] Discount type selected
- [ ] Discount value > 0
- [ ] Offer code is unique

---

## 🐛 Troubleshooting

| Problem | Solution |
|---------|----------|
| "HTTP 401" | Check API key in BuildConfig |
| "404 Not Found" | Item doesn't exist - refresh list |
| "Images not uploading" | Check storage bucket created & permissions |
| "RecyclerView empty" | Call loadBanners() after changes |
| "Dates showing wrong" | Ensure ISO format (YYYY-MM-DD) |
| "Network timeout" | Check internet - increase timeout if needed |
| "Duplicate offer code" | Code already exists - use different code |
| "Can't update banner" | Ensure banner.id exists and is correct |

---

## 📱 Code Snippets

### Launch Admin Panel
```java
Intent intent = new Intent(this, BannerManagementActivity.class);
startActivity(intent);
```

### Check Admin Role
```java
FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
if (user != null) {
    // Check role in database
    checkUserRole(user.getUid());
}
```

### Handle Network Error
```java
catch (Exception e) {
    Log.e(TAG, "Error", e);
    showErrorDialog("Error", "Failed: " + e.getMessage());
}
```

### Update RecyclerView
```java
adapter.setItems(newDataList);
adapter.notifyDataSetChanged();
```

### Show Error Dialog
```java
new MaterialAlertDialogBuilder(this)
        .setTitle("Error")
        .setMessage("Something went wrong")
        .setPositiveButton("OK", null)
        .show();
```

---

## 🔐 Security Reminders

- ✅ API keys in BuildConfig, not hardcoded
- ✅ RLS policies configured in Supabase
- ✅ Validate all user input
- ✅ Use HTTPS only
- ✅ Check admin status before allowing modifications
- ✅ Never log sensitive data
- ✅ Use timeouts on network requests

---

## 📊 Files Overview

| File | Purpose | Lines |
|------|---------|-------|
| BannerManagementActivity.java | Admin banner CRUD | 572 |
| OfferManagementActivity.java | Admin offer CRUD | 578 |
| BannerAdapter.java | Banner RecyclerView | 118 |
| OfferAdapter.java | Offer RecyclerView | 108 |
| SUPABASE_SETUP.sql | Database schema | 500+ |
| ADMIN_CRUD_DOCUMENTATION.md | Complete reference | 850+ |
| IMPLEMENTATION_GUIDE.md | Setup guide | 900+ |
| README_ADMIN_SYSTEM.md | System overview | 750+ |

---

## 🧪 Testing Quick Commands

### Test Create Banner
```bash
curl -X POST "https://xxx.supabase.co/rest/v1/banners" \
  -H "apikey: xxx" \
  -H "Authorization: Bearer xxx" \
  -H "Content-Type: application/json" \
  -d '{"title":"Test","image_url":"test.jpg","is_active":true}'
```

### Test Read Banners
```bash
curl -X GET "https://xxx.supabase.co/rest/v1/banners" \
  -H "apikey: xxx" \
  -H "Authorization: Bearer xxx"
```

### Test Update Banner
```bash
curl -X PATCH "https://xxx.supabase.co/rest/v1/banners?id=eq.xxx" \
  -H "apikey: xxx" \
  -H "Authorization: Bearer xxx" \
  -H "Content-Type: application/json" \
  -d '{"title":"Updated"}'
```

### Test Delete Banner
```bash
curl -X DELETE "https://xxx.supabase.co/rest/v1/banners?id=eq.xxx" \
  -H "apikey: xxx" \
  -H "Authorization: Bearer xxx"
```

---

## 📈 Performance Tips

- Keep images < 1MB (auto-compressed)
- Use indexed columns for queries
- Paginate results (LIMIT 20 OFFSET 0)
- Cache images with Glide
- Batch operations when possible
- Monitor database size

---

## 🎯 Common Use Cases

### Use Case 1: Add Summer Sale Banner
```
1. Open BannerManagementActivity
2. Tap FAB (Floating Action Button)
3. Enter title: "Summer Sale 2026"
4. Select image from device
5. Set redirect type: "category"
6. Set redirect ID: "plumbing"
7. Set expiry: "2026-06-30"
8. Toggle active: ON
9. Tap Save
10. ✅ Banner added and appears in list
```

### Use Case 2: Create 20% Discount Offer
```
1. Open OfferManagementActivity
2. Tap FAB
3. Enter title: "Spring Promo"
4. Enter code: "SPRING20"
5. Select discount type: "percentage"
6. Enter value: "20"
7. Enter min amount: "500"
8. Set expiry: "2026-05-31"
9. Toggle active: ON
10. Toggle notification: ON
11. Tap Save
12. ✅ Offer created, users get notified
```

### Use Case 3: Edit Banner Priority
```
1. Open BannerManagementActivity
2. Find banner in list
3. Tap edit icon
4. Change priority: "1" → "2"
5. Tap Save
6. ✅ Priority updated, list reorders
```

### Use Case 4: Disable Old Banner
```
1. Find banner in list
2. Tap status chip (says "Active")
3. Chip toggles to "Disabled"
4. ✅ Banner disabled without deleting
```

---

## 📞 When Something Goes Wrong

1. **Check Logs:** Logcat in Android Studio
2. **Check Supabase:** Logs in Supabase dashboard
3. **Verify API Key:** In BuildConfig
4. **Check Network:** Ensure internet connection
5. **Verify Tables:** Exist in Supabase
6. **Check RLS:** Policies allow admin access
7. **Review Documentation:** ADMIN_CRUD_DOCUMENTATION.md

---

## 📚 More Information

- **API Details:** See ADMIN_CRUD_DOCUMENTATION.md
- **Setup:** See IMPLEMENTATION_GUIDE.md
- **Database:** See SUPABASE_SETUP.sql
- **Overview:** See README_ADMIN_SYSTEM.md
- **Complete Info:** See IMPLEMENTATION_COMPLETE.md

---

**Version:** 1.0.0  
**Last Updated:** March 24, 2026  
**Status:** Production Ready ✅

**Need help? Check the full documentation files!**


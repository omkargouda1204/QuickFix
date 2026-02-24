# Admin Panel - Quick Start Guide

## STEP 1: Database Setup

1. Go to Supabase SQL Editor
2. Execute `ADMIN_DATABASE_STRUCTURE.sql`
3. Assign yourself as super_admin:
```sql
SELECT id FROM admin_roles WHERE role_name = 'super_admin';
-- Find result, then:
INSERT INTO admin_users (firebase_uid, admin_role_id) 
VALUES ('YOUR_UID', 1);
```

## STEP 2: Add Java Files

Copy these files to `app/src/main/java/com/example/quickfix/`:
- `RoleManager.java`
- `AdminLoginActivity.java`
- `AdminDashboardActivity.java`
- `BusinessApprovalActivity.java`
- `UserManagementActivity.java`
- `ServiceManagementActivity.java`
- `ContentManagementActivity.java`

## STEP 3: Update SupabaseClient

Add admin methods to existing SupabaseClient.java:
- `fetchPendingApprovals()`
- `approveProvider()`
- `rejectProvider()`
- `logAdminAction()`

## STEP 4: Add XML Layouts

Copy to `app/src/main/res/layout/`:
- `activity_admin_login.xml`
- `activity_admin_dashboard.xml`
- `activity_business_approval.xml`
- `activity_user_management.xml`
- `activity_service_management.xml`
- `activity_content_management.xml`

## STEP 5: Update AndroidManifest

Add all admin activities to manifest (provided in code).

## STEP 6: Build & Test

```bash
Build → Clean Project
Build → Rebuild Project
Run app
```

## ROLE PERMISSIONS

| Role | Features |
|------|----------|
| super_admin | All features |
| content_admin | Services, Categories, Featured |
| approval_admin | Approve providers |
| user_admin | Manage users |

## DATABASE TABLES

- `admin_roles` - Role definitions
- `admin_users` - Admin assignments
- `admin_permissions` - Permission mappings
- `pending_approvals` - Provider applications
- `service_categories` - Service types
- `services` - Service catalog
- `featured_services` - Featured items
- `admin_logs` - Audit trail

## SECURITY FEATURES

✅ Firebase authentication
✅ Email verification required
✅ Role-based access control
✅ Activity-level security gates
✅ Permission checks per feature
✅ Audit logging of all actions
✅ Button visibility based on role
✅ Logout on unauthorized access

## MAIN FLOWS

**Admin Login:**
Email/Password → Firebase Auth → Email Verified? → Admin Role Check? → Dashboard

**Business Approval:**
User applies → Pending entry → Admin reviews → Approve/Reject → Update user role

**User Management:**
View all users → Suspend/Delete → Log action

**Service Management:**
View/Add/Edit/Delete services → Log changes

**Content Management:**
Manage categories → Manage featured services


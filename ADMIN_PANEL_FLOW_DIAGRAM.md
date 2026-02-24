# Admin Panel System - Flow Diagram & Architecture

## 1. OVERALL AUTHENTICATION FLOW

```
┌─────────────────────────────────────────────────────────────────┐
│                       APP LAUNCH                                 │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
          ┌──────────────────────┐
          │  SplashActivity      │
          │  (Check if logged)   │
          └──────────┬───────────┘
                     │
        ┌────────────┴────────────┐
        │                         │
        ▼                         ▼
   No User              Yes, User Logged
        │                         │
        ▼                         ▼
   ┌─────────────┐      ┌────────────────┐
   │LoginActivity│      │Check Role in DB│
   └──────┬──────┘      └────────┬───────┘
          │                      │
          │          ┌───────────┴──────────┐
          │          │                      │
          ▼          ▼                      ▼
   [Regular User]  [Admin User]     [Provider User]
        │              │                     │
        ▼              ▼                     ▼
    MainActivity AdminLoginActivity  MainActivity
                     │
                     ▼
             AdminDashboardActivity
                (ROLE CHECK GATE)
                     │
     ┌───────────────┼───────────────┐
     ▼               ▼               ▼
Approvals      UserMgmt         ServiceMgmt
Activity       Activity         Activity
```

## 2. ADMIN LOGIN FLOW

```
┌──────────────────────┐
│   AdminLoginActivity │
└──────────┬───────────┘
           │
           ▼
    ┌────────────────┐
    │ Enter Email &  │
    │ Password Form  │
    └────────┬───────┘
             │
             ▼
    ┌─────────────────┐
    │Firebase Sign In │
    └────────┬────────┘
             │
        ┌────┴──────┐
        │           │
        ▼ Success   ▼ Failure
        │           └──► Show Error
        │                Try Again
        ▼
    ┌──────────────────┐
    │Email Verified?   │
    └────────┬─────────┘
             │
        ┌────┴──────────────┐
        │ No               │ Yes
        ▼                  ▼
    Show Error      ┌──────────────────┐
                    │Check if Admin in │
                    │admin_users table │
                    └────────┬─────────┘
                             │
                         ┌───┴──────┐
                         │          │
                    Yes  ▼          ▼ No
                    ┌────────┐   Show Error
                    │DASHBOARD   Logout
                    │Activity│
                    └────────┘
```

## 3. ADMIN DASHBOARD - ROLE-BASED FEATURE ACCESS

```
                  ┌──────────────────────┐
                  │AdminDashboardActivity│
                  └──────────┬───────────┘
                             │
                  ┌──────────┴──────────┐
                  │  Verify Admin Role  │
                  │ (RoleManager check) │
                  └──────────┬──────────┘
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │ 
          ▼                  ▼                  ▼
    SUPER_ADMIN      CONTENT_ADMIN        APPROVAL_ADMIN
      │                   │                    │
      ├─► All Features    ├─► Services       ├─► Approvals
      │   Enabled         │   & Content      │   & Reject
      │                   │                  │
      ▼                   ▼                  ▼
    [All Buttons]   [Services Only]    [Approval Only]
                        │
                        ▼
                    [Other buttons
                     disabled]
```

## 4. BUSINESS APPROVAL WORKFLOW

```
┌────────────────────────────────────┐
│     User clicks "List Business"     │
│     (from MainActivity)             │
└────────────────┬────────────────────┘
                 │
                 ▼
        ┌─────────────────────┐
        │ Create pending app  │
        │ in pending_approvals│
        │ status = "pending"  │
        └────────────┬────────┘
                     │
                     ▼
      ┌──────────────────────────┐
      │  Admin Dashboard         │
      │  Approvals Section       │
      │  Shows pending list      │
      └────────┬─────────────────┘
               │
          ┌────┴─────┐
          │           │
      Approve        Reject
          │           │
          ▼           ▼
    ┌─────────┐  ┌──────────┐
    │Update   │  │Update    │
    │status   │  │status    │
    │approved │  │rejected  │
    │User role│  │Notify    │
    │provider │  │user      │
    └─────────┘  └──────────┘
          │           │
          └─────┬─────┘
                ▼
        ┌──────────────────┐
        │  Log admin       │
        │  action in       │
        │  admin_logs      │
        └──────────────────┘
```

## 5. DATABASE RELATIONSHIPS

```
┌─────────────────┐
│    users        │ (Firebase + Supabase)
├─────────────────┤
│ firebase_uid    │ (PK)
│ name            │
│ email           │
│ phone           │
│ role            │◄──────────────┐
│ status          │               │
│ created_at      │               │
└─────────────────┘               │
         ▲                         │
         │                         │
         │                    ┌────┴─────────────┐
         │                    │  admin_users     │
         │                    ├──────────────────┤
         │                    │ firebase_uid(FK)─┼──────┐
         │                    │ admin_role_id(FK)┼─┐    │
         │                    │ assigned_at      │ │    │
         │                    └──────────────────┘ │    │
         │                         │               │    │
         │                         └─────────┬─────┘    │
         │                                   │          │
         │                    ┌──────────────▼──┐      │
         │                    │  admin_roles     │      │
         │                    ├──────────────────┤      │
         │                    │ id (PK)          │      │
         │                    │ role_name        │      │
         │                    └──────────────────┘      │
         │                           │                   │
         │      ┌────────────────────┼──────────┐       │
         │      │                    │          │       │
         │      ▼                    ▼          │       │
         │  ┌──────────────┐  ┌──────────────┐ │       │
         │  │pending_      │  │  admin_      │ │       │
         │  │approvals     │  │permissions   │ │       │
         │  ├──────────────┤  ├──────────────┤ │       │
         └──┤user_firebase ├──┤admin_role_id │ │       │
            │_uid (FK)     │  │(FK)          │ │       │
            │status        │  │permission_   │ │       │
            │business_name │  │code          │ │       │
            │reviewed_by   │  └──────────────┘ │       │
            │_uid (FK)     │                    │       │
            └──────────────┘                    │       │
                   │                            │       │
                   └─────────────────────┬──────┼───────┘
                                         │      │
                                         ▼      ▼
                                    ┌─────────────┐
                                    │ admin_logs  │
                                    ├─────────────┤
                                    │ admin_uid   │
                                    │ action_type │
                                    │ target_type │
                                    │ timestamp   │
                                    └─────────────┘
```

## 6. SECURITY LAYERS

```
┌─────────────────────────────────────────────────┐
│           SECURITY IMPLEMENTATION               │
└─────────────────────────────────────────────────┘

Layer 1: FIREBASE AUTHENTICATION
├─ Email/Password verification
├─ Email verification (required)
└─ Firebase Auth tokens

Layer 2: ROLE VERIFICATION (RoleManager)
├─ Check if user exists in admin_users table
├─ Verify admin_role_id is valid
├─ Extract role_name (super_admin, content_admin, etc.)
└─ Block access if not found

Layer 3: ACTIVITY-LEVEL GUARDS
├─ AdminDashboardActivity verifies role on creation
├─ Cannot access without passing Role check
├─ Redirects to AdminLoginActivity if unauthorized
└─ Prevents deep linking to admin screens

Layer 4: PERMISSION CHECKS (Per Feature)
├─ RoleManager.checkPermission() for fine-grained control
├─ Query admin_permissions table
├─ Verify permission_code in user's role
└─ Disable UI buttons for unauthorized features

Layer 5: DATABASE ROW-LEVEL SECURITY (RLS)
├─ admin_users table: Only admins can view
├─ admin_roles table: Public read, restricted write
├─ admin_logs table: App-level validation
└─ Supabase policies enforce access

Layer 6: AUDIT LOGGING
├─ Every admin action logged to admin_logs
├─ Timestamp, admin_uid, action_type recorded
├─ Non-blocking: doesn't affect main operations
└─ Visible to super_admin only
```

## 7. ROLE HIERARCHY & PERMISSIONS

```
SUPER_ADMIN (Full Access)
├─ Approve Providers (approve_provider, reject_provider)
├─ View Users (view_users)
├─ Delete Users (delete_user)
├─ Suspend Users (suspend_user)
├─ Manage Services (manage_services)
├─ Manage Categories (manage_categories)
├─ Manage Featured Services (manage_featured)
└─ View Admin Logs

CONTENT_ADMIN (Content Only)
├─ Manage Services (manage_services)
├─ Manage Categories (manage_categories)
└─ Manage Featured Services (manage_featured)

APPROVAL_ADMIN (Provider Reviews)
├─ Approve Providers (approve_provider)
└─ Reject Providers (reject_provider)

USER_ADMIN (User Management)
├─ View Users (view_users)
├─ Delete Users (delete_user)
└─ Suspend Users (suspend_user)
```

## 8. ERROR HANDLING & REDIRECTS

```
                    ┌─────────────────┐
                    │ Access Denied?  │
                    └────────┬────────┘
                             │
           ┌─────────────────┼─────────────────┐
           │                 │                 │
     No Firebase User    Not Email Verified  No Admin Role
           │                 │                 │
           ▼                 ▼                 ▼
    Redirect to       Show Error +       Logout +
    LoginActivity     Resend Email      Redirect to
                                         AdminLogin
           │                 │                 │
           └─────────────────┼─────────────────┘
                             │
                             ▼
                      ┌──────────────┐
                      │ Clear session│
                      │ Prevent back │
                      │ button access│
                      └──────────────┘
```

## 9. DATA FLOW: PROVIDER APPROVAL

```
USER SIDE:                          ADMIN SIDE:
┌──────────────┐
│Main Activity │
└──────┬───────┘
       │
       ▼
┌──────────────────┐
│"List Business"   │
│    Button        │
└──────┬───────────┘
       │
       ▼
┌──────────────────────┐
│Create entry in       │
│pending_approvals     │
│status: "pending"     │
└──────┬───────────────┘
       │
       │ (Data in DB)
       │
       └──────────────────────────────┐
                                      │
                                      ▼
                            ┌──────────────────────┐
                            │Admin Dashboard       │
                            │ "Approvals" Button   │
                            └──────┬───────────────┘
                                   │
                                   ▼
                      ┌────────────────────────────┐
                      │BusinessApprovalActivity    │
                      │Fetch all pending_approvals │
                      │ (status = "pending")       │
                      └──────┬─────────────────────┘
                             │
                      ┌──────┴──────┐
                      │             │
                   Approve      Reject
                      │             │
                      ▼             ▼
                  ┌────────┐    ┌────────┐
                  │Update  │    │Update  │
                  │pending │    │pending │
                  │status  │    │status  │
                  │approved│    │reject  │
                  └────┬───┘    └────┬───┘
                       │             │
                       ▼             ▼
                   ┌─────────┐   ┌─────────┐
                   │Update   │   │Send     │
                   │users    │   │rejection│
                   │role to  │   │reason   │
                   │provider │   │to user  │
                   └─────────┘   └─────────┘
```

## 10. SETUP CHECKLIST

- [ ] Run SQL script (ADMIN_DATABASE_STRUCTURE.sql) in Supabase
- [ ] Assign at least one super_admin in admin_users table
- [ ] Add all Java files to project
- [ ] Add all XML layout files to project
- [ ] Update AndroidManifest.xml with admin activities
- [ ] Extend SupabaseClient (see code changes)
- [ ] Test admin login flow
- [ ] Verify role permissions
- [ ] Test provider approval workflow
- [ ] Check admin logs are being recorded
- [ ] Test error handling and redirects


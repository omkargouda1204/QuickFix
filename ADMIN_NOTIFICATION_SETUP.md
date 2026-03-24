# Admin Notification System Setup (QuickFix)

This project now sends admin notifications for:

1. New user registration (`user_registration`)
2. New provider application submission (`provider_application`)

## What is already implemented in app code

- Trigger after successful user profile persistence in `app/src/main/java/com/example/quickfix/SignupActivity.java`
- Trigger after successful provider application insert in `app/src/main/java/com/example/quickfix/ProviderApplicationRepository.java`
- FCM + Supabase delivery logic in `app/src/main/java/com/example/quickfix/FcmNotificationHelper.java`
- Foreground push handling and routing in `app/src/main/java/com/example/quickfix/MyFirebaseMessagingService.java`
- Admin inbox UI filter support in:
  - `app/src/main/java/com/example/quickfix/AdminNotificationsActivity.java`
  - `app/src/main/res/layout/activity_admin_notifications.xml`

## Required Supabase configuration

Ensure `supabase.properties` has:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `SUPABASE_NOTIFY_FN_URL`

Example:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_anon_key
SUPABASE_NOTIFY_FN_URL=https://your-project.supabase.co/functions/v1/send-notification
```

## Required table (optional but recommended)

Create `admin_notifications` table if not present:

```sql
create table if not exists public.admin_notifications (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  message text not null default '',
  type text not null default 'general',
  reference_id text,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists idx_admin_notifications_created_at
  on public.admin_notifications (created_at desc);

create index if not exists idx_admin_notifications_type
  on public.admin_notifications (type);

create index if not exists idx_admin_notifications_is_read
  on public.admin_notifications (is_read);
```

If this table is missing, app push still works and persistence is skipped safely.

## Edge Function contract

The app calls `SUPABASE_NOTIFY_FN_URL` with:

```json
{
  "token": "<device_fcm_token>",
  "title": "New User Registered",
  "body": "Alice created an account",
  "data": {
    "type": "user_registration",
    "uid": "optional-reference-id"
  }
}
```

Your edge function should use Firebase Admin SDK (or FCM HTTP v1) and return HTTP 200/201/204 on success.

## Runtime notes

- Admin device must be logged in at least once so FCM token is saved in `users.fcm_token`
- Admin users must have `users.is_admin = true`
- Push can be suppressed by app admin settings (`push_notifications`, `admin_alerts`)

## Optional server-side hardening

If you want notifications even when mobile client logic is bypassed, add Supabase DB triggers/edge-webhooks on:

- `users` insert -> `user_registration`
- `provider_applications` insert -> `provider_application`

Current implementation is app-driven and non-blocking (main insert success does not depend on notification send success).


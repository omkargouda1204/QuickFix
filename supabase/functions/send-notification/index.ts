/**
 * send-notification/index.ts
 *
 * Supabase Edge Function — sends FCM push notifications.
 *
 * Accepts TWO calling conventions:
 *
 * Convention A — token-based (used by FcmNotificationHelper.sendViaEdgeFunction):
 *   { tokens: string[], title: string, body: string, type: string, reference_id?: string, data?: object }
 *
 * Convention B — user_id-based (used by ProviderEmergencyRequestActivity.notifyUser):
 *   { user_id: string, title: string, body: string, type: string, data?: object }
 *
 * In Convention B the function resolves the FCM token from:
 *   1. users table (by id or firebase_uid)
 *   2. provider_profiles table (by firebase_uid)
 *   3. user_profiles table (by id)
 */

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

function jsonRes(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
  });
}

// ── FCM Legacy HTTP API ───────────────────────────────────────────────────────

async function sendFcm(
  firebaseKey: string,
  token: string,
  title: string,
  body: string,
  data: Record<string, string>
): Promise<{ success: boolean; error?: string }> {
  const payload = {
    to: token,
    priority: "high",
    notification: {
      title,
      body,
      sound: "default",
      click_action: "FLUTTER_NOTIFICATION_CLICK",
    },
    data: {
      ...data,
      click_action: "FLUTTER_NOTIFICATION_CLICK",
    },
  };

  try {
    const res = await fetch("https://fcm.googleapis.com/fcm/send", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `key=${firebaseKey}`,
      },
      body: JSON.stringify(payload),
    });

    const result = await res.json();
    console.log("[FCM] Response:", JSON.stringify(result));

    if (!res.ok || result.failure === 1) {
      const err = result.results?.[0]?.error || `HTTP ${res.status}`;
      return { success: false, error: err };
    }
    return { success: true };
  } catch (err) {
    console.error("[FCM] Fetch error:", err);
    return { success: false, error: String(err) };
  }
}

// ── Token resolution helpers ──────────────────────────────────────────────────

async function resolveTokenForUserId(
  supabaseUrl: string,
  serviceKey: string,
  userId: string
): Promise<string | null> {
  const headers = {
    apikey: serviceKey,
    authorization: `Bearer ${serviceKey}`,
    accept: "application/json",
  };

  // 1. users table — by id
  try {
    const res = await fetch(
      `${supabaseUrl}/rest/v1/users?id=eq.${encodeURIComponent(userId)}&select=fcm_token&limit=1`,
      { headers }
    );
    if (res.ok) {
      const rows = await res.json();
      if (rows.length > 0 && rows[0].fcm_token) return rows[0].fcm_token;
    }
  } catch (e) {
    console.warn("[Token] users(id) lookup failed:", e);
  }

  // 2. users table — by firebase_uid
  try {
    const res = await fetch(
      `${supabaseUrl}/rest/v1/users?firebase_uid=eq.${encodeURIComponent(userId)}&select=fcm_token&limit=1`,
      { headers }
    );
    if (res.ok) {
      const rows = await res.json();
      if (rows.length > 0 && rows[0].fcm_token) return rows[0].fcm_token;
    }
  } catch (e) {
    console.warn("[Token] users(firebase_uid) lookup failed:", e);
  }

  // 3. provider_profiles table — by firebase_uid
  try {
    const res = await fetch(
      `${supabaseUrl}/rest/v1/provider_profiles?firebase_uid=eq.${encodeURIComponent(userId)}&select=fcm_token&limit=1`,
      { headers }
    );
    if (res.ok) {
      const rows = await res.json();
      if (rows.length > 0 && rows[0].fcm_token) return rows[0].fcm_token;
    }
  } catch (e) {
    console.warn("[Token] provider_profiles lookup failed:", e);
  }

  // 4. user_profiles table — by id
  try {
    const res = await fetch(
      `${supabaseUrl}/rest/v1/user_profiles?id=eq.${encodeURIComponent(userId)}&select=fcm_token&limit=1`,
      { headers }
    );
    if (res.ok) {
      const rows = await res.json();
      if (rows.length > 0 && rows[0].fcm_token) return rows[0].fcm_token;
    }
  } catch (e) {
    console.warn("[Token] user_profiles lookup failed:", e);
  }

  return null;
}

// ── Main handler ──────────────────────────────────────────────────────────────

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS_HEADERS });
  }
  if (req.method !== "POST") {
    return jsonRes({ error: "Method not allowed" }, 405);
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceKey =
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ??
      Deno.env.get("SUPABASE_ANON_KEY") ??
      "";
    const firebaseKey = Deno.env.get("FIREBASE_SERVER_KEY") ?? "";

    if (!supabaseUrl || !serviceKey) {
      return jsonRes({ error: "Server misconfigured: missing Supabase env vars" }, 500);
    }
    if (!firebaseKey) {
      console.error("[Config] FIREBASE_SERVER_KEY is not set!");
      return jsonRes({ error: "Server misconfigured: FIREBASE_SERVER_KEY not set" }, 500);
    }

    let body: Record<string, unknown>;
    try {
      body = await req.json();
    } catch {
      return jsonRes({ error: "Invalid JSON body" }, 400);
    }

    const title = String(body.title || "QuickFix");
    const msgBody = String(body.body || "You have a new notification");
    const type = String(body.type || "general");
    const referenceId = String(body.reference_id || body.booking_id || "");
    const extraData = (body.data || {}) as Record<string, unknown>;

    // Build a string-only data payload for FCM
    const dataPayload: Record<string, string> = {
      type,
      title,
      body: msgBody,
    };
    if (referenceId) {
      dataPayload.booking_id = referenceId;
      dataPayload.request_id = referenceId;
      dataPayload.reference_id = referenceId;
    }
    for (const [k, v] of Object.entries(extraData)) {
      if (v !== null && v !== undefined) dataPayload[k] = String(v);
    }

    // ── Convention A: tokens[] provided directly ──────────────────────────────
    if (Array.isArray(body.tokens) && body.tokens.length > 0) {
      const tokens: string[] = (body.tokens as string[]).filter(
        (t) => typeof t === "string" && t.trim().length > 0
      );

      if (tokens.length === 0) {
        return jsonRes({ success: false, message: "No valid tokens provided" });
      }

      let sent = 0;
      const errors: string[] = [];

      for (const token of tokens) {
        const result = await sendFcm(firebaseKey, token, title, msgBody, dataPayload);
        if (result.success) {
          sent++;
        } else {
          errors.push(result.error || "unknown");
          console.warn(`[FCM] Failed for token ${token.slice(0, 20)}...: ${result.error}`);
        }
      }

      console.log(`[send-notification] Convention A: sent=${sent}/${tokens.length}`);
      return jsonRes({ success: sent > 0, sent, total: tokens.length, errors });
    }

    // ── Convention B: resolve token from user_id ──────────────────────────────
    const userId = String(body.user_id || "").trim();
    if (!userId) {
      return jsonRes(
        { error: "Provide either 'tokens' array or 'user_id'" },
        400
      );
    }

    const token = await resolveTokenForUserId(supabaseUrl, serviceKey, userId);

    if (!token) {
      console.warn(`[send-notification] No FCM token found for user_id=${userId}`);
      return jsonRes({ success: false, message: "No FCM token found for this user" });
    }

    const result = await sendFcm(firebaseKey, token, title, msgBody, dataPayload);

    console.log(`[send-notification] Convention B: user_id=${userId} success=${result.success}`);
    return jsonRes({ success: result.success, error: result.error });
  } catch (err) {
    console.error("[send-notification] Unhandled error:", err);
    return jsonRes({ error: String(err) }, 500);
  }
});

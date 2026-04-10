pastimport { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.4";

type NotifyPayload = {
  token?: string;
  tokens?: string[];
  receiver_id?: string;
  receiver_ids?: string[];
  receiver_role?: string;
  receiver_roles?: string[];
  title?: string;
  body?: string;
  type?: string;
  reference_id?: string | null;
  data?: Record<string, string>;
};

type ServiceAccount = {
  client_email: string;
  private_key: string;
  token_uri?: string;
  project_id: string;
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const payload = (await req.json()) as NotifyPayload;

    const title = (payload.title ?? "").trim();
    const body = (payload.body ?? "").trim();
    const type = (payload.type ?? "general").trim() || "general";

    if (!title || !body) {
      return json({ error: "title and body are required" }, 400);
    }

    const tokens = dedupe([
      ...(payload.tokens ?? []),
      payload.token ?? "",
    ].map((v) => v.trim()).filter(Boolean));

    const receiverIds = dedupe([
      ...(payload.receiver_ids ?? []),
      payload.receiver_id ?? "",
    ].map((v) => v.trim()).filter(Boolean));


    const receiverRoles = [
      ...(payload.receiver_roles ?? []),
      payload.receiver_role ?? "",
    ].map((v) => v.trim()).filter(Boolean);

    if (tokens.length === 0 && receiverIds.length === 0) {
      return json({ error: "Provide at least one token or receiver_id" }, 400);
    }

    if (tokens.length > 0 && receiverIds.length > 0 && tokens.length !== receiverIds.length) {
      return json({
        error: "tokens and receiver_ids length mismatch",
        tokens: tokens.length,
        receiver_ids: receiverIds.length,
      }, 400);
    }

    if (receiverRoles.length > 0 && receiverIds.length > 0 && receiverRoles.length !== receiverIds.length && receiverRoles.length !== 1) {
      return json({
        error: "receiver_roles must be length 1 or match receiver_ids length",
        receiver_roles: receiverRoles.length,
        receiver_ids: receiverIds.length,
      }, 400);
    }

    const serviceAccountRaw = Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "";
    if (!serviceAccountRaw) {
      return json({ error: "FCM_SERVICE_ACCOUNT secret is missing" }, 500);
    }

    const serviceAccount = JSON.parse(serviceAccountRaw) as ServiceAccount;
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

    if (!supabaseUrl || !serviceRoleKey) {
      return json({ error: "SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY missing" }, 500);
    }

    const supabase = createClient(supabaseUrl, serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    let fetchedTokens = tokens;

    // Optional fallback: if tokens are not provided but receiver ids exist, resolve fcm_token from users.
    if (fetchedTokens.length === 0 && receiverIds.length > 0) {
      const { data, error } = await supabase
        .from("users")
        .select("id,fcm_token")
        .in("id", receiverIds)
        .not("fcm_token", "is", null);

      if (!error && data) {
        fetchedTokens = dedupe(
          data
            .map((row: { fcm_token: string | null }) => (row.fcm_token ?? "").trim())
            .filter(Boolean),
        );
      }
    }

    const accessToken = await getGoogleAccessToken(serviceAccount);

    const sendResults = await Promise.all(
      fetchedTokens.map(async (token) => {
        try {
          const resp = await sendFcmV1(serviceAccount.project_id, accessToken, {
            token,
            title,
            body,
            type,
            referenceId: payload.reference_id ?? null,
            data: payload.data ?? {},
          });

          if (resp.status === 200) {
            return { token, ok: true };
          }

          const responseText = await resp.text();
          const shouldClearToken =
            responseText.includes("UNREGISTERED") ||
            responseText.includes("registration-token-not-registered") ||
            responseText.includes("invalid-registration-token");

          if (shouldClearToken) {
            await supabase.from("users").update({ fcm_token: null }).eq("fcm_token", token);
          }

          console.error("FCM send failed", { token, status: resp.status, responseText });
          return { token, ok: false, status: resp.status, responseText };
        } catch (err) {
          console.error("FCM send exception", { token, err });
          return { token, ok: false, error: String(err) };
        }
      }),
    );

    // Store notification rows for in-app history.
    const rows: Array<Record<string, unknown>> = [];

    if (receiverIds.length > 0) {
      for (let i = 0; i < receiverIds.length; i++) {
        const receiverId = receiverIds[i];
        const role = receiverRoles.length === receiverIds.length
          ? receiverRoles[i]
          : (receiverRoles[0] ?? null);
        rows.push({
          receiver_id: receiverId,
          receiver_role: role,
          title,
          body,
          type,
          reference_id: payload.reference_id ?? null,
          is_read: false,
        });
      }
    } else if (receiverRoles.length > 0) {
      for (const role of dedupe(receiverRoles)) {
        rows.push({
          receiver_id: null,
          receiver_role: role,
          title,
          body,
          type,
          reference_id: payload.reference_id ?? null,
          is_read: false,
        });
      }
    }

    if (rows.length > 0) {
      const { error } = await supabase.from("notifications").insert(rows);
      if (error) {
        console.error("notifications insert failed", error);
      }
    }

    const sent = sendResults.filter((x) => x.ok).length;
    return json({ ok: true, sent, totalTokens: fetchedTokens.length }, 200);
  } catch (e) {
    console.error("send-notification edge error", e);
    return json({ error: String(e) }, 500);
  }
});

function dedupe(values: string[]): string[] {
  return [...new Set(values)];
}

function base64Url(input: Uint8Array | string): string {
  const raw = typeof input === "string"
    ? btoa(input)
    : btoa(String.fromCharCode(...input));
  return raw.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function getGoogleAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const payload = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: sa.token_uri ?? "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };

  const encodedHeader = base64Url(JSON.stringify(header));
  const encodedPayload = base64Url(JSON.stringify(payload));
  const toSign = `${encodedHeader}.${encodedPayload}`;

  const cryptoKey = await importPkcs8Key(sa.private_key);
  const signature = await crypto.subtle.sign(
    { name: "RSASSA-PKCS1-v1_5" },
    cryptoKey,
    new TextEncoder().encode(toSign),
  );

  const assertion = `${toSign}.${base64Url(new Uint8Array(signature))}`;

  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion,
  });

  const tokenResp = await fetch(sa.token_uri ?? "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });

  if (!tokenResp.ok) {
    throw new Error(`OAuth token request failed: ${tokenResp.status} ${await tokenResp.text()}`);
  }

  const tokenJson = await tokenResp.json();
  return tokenJson.access_token as string;
}

async function importPkcs8Key(pem: string): Promise<CryptoKey> {
  const clean = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");

  const binary = Uint8Array.from(atob(clean), (c) => c.charCodeAt(0));
  return await crypto.subtle.importKey(
    "pkcs8",
    binary.buffer,
    {
      name: "RSASSA-PKCS1-v1_5",
      hash: "SHA-256",
    },
    false,
    ["sign"],
  );
}

async function sendFcmV1(
  projectId: string,
  accessToken: string,
  args: {
    token: string;
    title: string;
    body: string;
    type: string;
    referenceId: string | null;
    data: Record<string, string>;
  },
): Promise<Response> {
  const messageData: Record<string, string> = {
    type: args.type,
    ...(args.referenceId ? { reference_id: args.referenceId } : {}),
    ...args.data,
  };

  const channelId = bookingLikeType(args.type)
    ? "bookings"
    : providerLikeType(args.type)
      ? "provider_apps"
      : "general";

  return await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      message: {
        token: args.token,
        notification: {
          title: args.title,
          body: args.body,
        },
        data: messageData,
        android: {
          priority: "high",
          notification: {
            sound: "default",
            channel_id: channelId,
          },
        },
      },
    }),
  });
}

function bookingLikeType(type: string): boolean {
  return [
    "booking_request",
    "booking_accepted",
    "booking_rejected",
    "booking_completed",
    "booking_update",
    "booking",
  ].includes((type ?? "").trim());
}

function providerLikeType(type: string): boolean {
  return [
    "provider_application",
    "provider_status",
    "provider_approved",
    "provider_rejected",
  ].includes((type ?? "").trim());
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}


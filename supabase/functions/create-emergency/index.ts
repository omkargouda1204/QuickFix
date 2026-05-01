// create-emergency/index.ts
// Edge Function: creates an emergency request, finds nearby providers, and creates bookings.
//
// AUTH DESIGN:
//   - The Android client sends: Authorization: Bearer <SUPABASE_ANON_KEY>
//   - This function validates the header is present (not empty).
//   - All DB operations use SUPABASE_SERVICE_ROLE_KEY internally (bypasses RLS safely).
//   - The user_id in the payload is trusted (sent by the authenticated client).

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

// ── Provider discovery helpers ────────────────────────────────────────────────

async function callRpcFindProviders(
  supabaseUrl: string,
  serviceKey: string,
  payload: { p_lat: number; p_lng: number; p_category: string; p_radius_meters: number }
): Promise<any[]> {
  const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/rpc/find_nearby_providers`;
  try {
    console.log("Calling RPC find_nearby_providers with payload:", JSON.stringify(payload));
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        apikey: serviceKey,
        authorization: `Bearer ${serviceKey}`,
        Accept: 'application/json',
      },
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      const text = await res.text();
      console.warn(`RPC find_nearby_providers failed HTTP ${res.status}: ${text}`);
      return [];
    }
    const result = await res.json();
    console.log(`RPC returned ${Array.isArray(result) ? result.length : 0} providers`);
    return Array.isArray(result) ? result : [];
  } catch (err) {
    console.warn('RPC call failed, falling back to client-side filter:', err);
    return [];
  }
}

function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

async function fetchProvidersAndFilter(
  supabaseUrl: string,
  serviceKey: string,
  lat: number,
  lng: number,
  category: string,
  radiusKm: number
): Promise<any[]> {
  const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/provider_profiles` +
    `?select=id,firebase_uid,business_name,category,service_category,latitude,longitude,lat,lng,status,is_active,city,area` +
    `&limit=1000`;
  try {
    const res = await fetch(url, {
      method: 'GET',
      headers: {
        apikey: serviceKey,
        authorization: `Bearer ${serviceKey}`,
        Accept: 'application/json',
      },
    });
    if (!res.ok) {
      console.warn(`Provider fetch fallback failed HTTP ${res.status}`);
      return [];
    }
    const arr: any[] = await res.json();
    const out: any[] = [];
    const lcCategory = (category || '').toLowerCase();

    for (const p of arr) {
      // Category match
      const provCat = ((p.service_category || p.category || '') as string).toLowerCase();
      if (!provCat.includes(lcCategory) && !lcCategory.includes(provCat)) continue;

      // Active check
      if (p.is_active === false || p.status === 'inactive') continue;

      // Lat/lng
      let pLat: number | null = null;
      let pLng: number | null = null;
      if (p.latitude != null) pLat = Number(p.latitude);
      else if (p.lat != null) pLat = Number(p.lat);
      if (p.longitude != null) pLng = Number(p.longitude);
      else if (p.lng != null) pLng = Number(p.lng);
      if (pLat == null || pLng == null || isNaN(pLat) || isNaN(pLng)) continue;

      const dist = haversineKm(lat, lng, pLat, pLng);
      if (!isNaN(dist) && dist <= radiusKm) {
        p.distance_km = dist;
        out.push(p);
      }
    }
    console.log(`Fallback: fetched ${arr.length} total, matched ${out.length} by category+distance`);
    return out;
  } catch (err) {
    console.warn('Fallback fetch error:', err);
    return [];
  }
}

// ── DB write helpers (all use service role key — bypass RLS) ──────────────────

async function insertEmergencyRequest(
  supabaseUrl: string,
  serviceKey: string,
  body: Record<string, unknown>
): Promise<{ id: string }> {
  const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/emergency_requests?select=id`;
  const payload = {
    ...body,
    user_id: String(body.user_id || ''),
    user_firebase_uid: String(body.user_id || ''),
    status: 'pending',
  };

  console.log('Inserting emergency request:', JSON.stringify(payload));

  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      apikey: serviceKey,
      authorization: `Bearer ${serviceKey}`,
      Prefer: 'return=representation',
    },
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Insert emergency_request failed HTTP ${res.status}: ${text}`);
  }

  const arr: any[] = await res.json();
  if (!arr || arr.length === 0) {
    throw new Error('Insert emergency_request returned empty result');
  }
  return arr[0];
}

async function insertBookings(
  supabaseUrl: string,
  serviceKey: string,
  rows: Record<string, unknown>[]
): Promise<void> {
  if (!Array.isArray(rows) || rows.length === 0) return;

  const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/bookings`;
  const castRows = rows.map((r) => ({
    ...r,
    customer_id: String(r.customer_id || ''),
    status: r.status || 'pending',
  }));

  console.log(`Inserting ${castRows.length} bookings`);

  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      apikey: serviceKey,
      authorization: `Bearer ${serviceKey}`,
      Prefer: 'return=minimal',
    },
    body: JSON.stringify(castRows),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Insert bookings failed HTTP ${res.status}: ${text}`);
  }
  console.log(`Bookings inserted successfully`);
}

async function notifyProviders(
  supabaseUrl: string,
  serviceKey: string,
  requestId: string,
  emergencyData: Record<string, unknown>,
  providers: any[]
): Promise<void> {
  for (const provider of providers) {
    const firebaseUid = provider.firebase_uid || provider.provider_firebase_uid;
    if (!firebaseUid) continue;

    const notificationPayload = {
      user_id: firebaseUid,
      type: 'emergency_request',
      title: `Emergency: ${emergencyData.category}`,
      body: `Emergency request from ${emergencyData.user_name}. Tap to view.`,
      request_id: requestId,
      quick_action: 'accept',
    };

    try {
      const notifyUrl = `${supabaseUrl.replace(/\/$/, '')}/functions/v1/send-notification`;
      const res = await fetch(notifyUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          apikey: serviceKey,
          authorization: `Bearer ${serviceKey}`,
        },
        body: JSON.stringify(notificationPayload),
      });
      if (!res.ok) {
        console.warn(`Notification failed for provider ${firebaseUid}: HTTP ${res.status}`);
      } else {
        console.log(`Notification sent to provider ${firebaseUid}`);
      }
    } catch (err) {
      console.warn(`notifyProviders error for ${firebaseUid}:`, err);
    }
  }
}

// ── Main handler ──────────────────────────────────────────────────────────────

Deno.serve(async (req) => {
  // Handle preflight
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  if (req.method !== 'POST') return json({ error: 'Method not allowed' }, 405);

  try {
    // ── 1. Read env ──────────────────────────────────────────────────────────
    const env = Deno.env.toObject();
    const SUPABASE_URL = env.SUPABASE_URL || '';
    // Always prefer service role key for server-side DB writes (bypasses RLS)
    const SERVICE_KEY =
      env.SUPABASE_SERVICE_ROLE_KEY ||
      env.SUPABASE_ANON_KEY ||
      env.SUPABASE_API_KEY ||
      '';

    if (!SUPABASE_URL || !SERVICE_KEY) {
      return json({ error: 'Supabase URL or service key not configured' }, 500);
    }

    // ── 2. Validate Authorization header ────────────────────────────────────
    // The Android client must send: Authorization: Bearer <SUPABASE_ANON_KEY>
    // We just verify the header is present; internal DB calls use service role key.
    const authHeader = req.headers.get('authorization') || '';
    if (!authHeader.toLowerCase().startsWith('bearer ')) {
      console.error('Missing or invalid Authorization header');
      return json({
        error: 'Unauthorized: Authorization header is required. Use Bearer <SUPABASE_ANON_KEY>.',
      }, 401);
    }
    const bearerToken = authHeader.slice(7).trim();
    if (!bearerToken) {
      return json({ error: 'Unauthorized: Bearer token is empty.' }, 401);
    }
    console.log(`Authorization header present (token length=${bearerToken.length})`);

    // ── 3. Parse and validate request body ──────────────────────────────────
    let payload: Record<string, unknown>;
    try {
      payload = await req.json();
    } catch {
      return json({ error: 'Invalid JSON body' }, 400);
    }

    console.log('=== create-emergency START ===');
    console.log('Request body:', JSON.stringify({
      user_id: payload.user_id,
      category: payload.category,
      lat: payload.lat,
      lng: payload.lng,
    }));

    const required = ['user_id', 'category', 'lat', 'lng'];
    for (const k of required) {
      if (payload[k] === undefined || payload[k] === null || String(payload[k]).trim() === '') {
        return json({ error: `Missing required field: ${k}` }, 400);
      }
    }

    const p_lat = Number(payload.lat);
    const p_lng = Number(payload.lng);
    const p_category = String(payload.category || '').trim();
    const p_radius_meters = Number(payload.radius_meters || 25000);
    const p_radius_km = p_radius_meters / 1000;

    if (isNaN(p_lat) || isNaN(p_lng)) {
      return json({ error: 'Invalid lat/lng values' }, 400);
    }

    console.log(`User: ${payload.user_id} | Category: ${p_category} | Location: ${p_lat},${p_lng} | Radius: ${p_radius_km}km`);

    // ── 4. Find nearby providers ─────────────────────────────────────────────
    let providers: any[] = [];

    // Try RPC first (PostGIS ST_DWithin query)
    try {
      providers = await callRpcFindProviders(SUPABASE_URL, SERVICE_KEY, {
        p_lat,
        p_lng,
        p_category,
        p_radius_meters,
      });
    } catch (e) {
      console.warn('RPC error:', e);
    }

    // Fallback: client-side haversine filter
    if (!Array.isArray(providers) || providers.length === 0) {
      console.log('RPC returned no results, using fallback provider fetch...');
      providers = await fetchProvidersAndFilter(
        SUPABASE_URL, SERVICE_KEY, p_lat, p_lng, p_category, p_radius_km
      );
    }

    console.log(`Total providers found: ${providers.length}`);

    // ── 5. Insert emergency_requests row (always, regardless of providers) ───
    const reqBody: Record<string, unknown> = {
      user_id: String(payload.user_id),
      user_firebase_uid: String(payload.user_id),
      user_name: String(payload.user_name || 'Unknown'),
      phone: String(payload.phone || ''),
      category: p_category,
      description: String(payload.description || ''),
      lat: p_lat,
      lng: p_lng,
      city: String(payload.city || ''),
      area: String(payload.area || ''),
      address: String(payload.address || ''),
      status: 'pending',
    };

    const created = await insertEmergencyRequest(SUPABASE_URL, SERVICE_KEY, reqBody);
    const requestId = created.id;
    console.log('Emergency request created with ID:', requestId);

    // ── 6. Create booking rows for matched providers ──────────────────────────
    const bookingRows: Record<string, unknown>[] = providers.map((prov: any) => ({
      customer_id: String(payload.user_id),
      customer_name: String(payload.user_name || ''),
      customer_phone: String(payload.phone || ''),
      provider_id: prov.id || prov.provider_id,
      provider_firebase_uid: prov.firebase_uid || prov.provider_firebase_uid || null,
      provider_name: prov.business_name || 'Awaiting Acceptance',
      service_name: 'Emergency - ' + p_category,
      service_category: p_category,
      category: p_category,
      address: String(payload.address || ''),
      description: String(payload.description || ''),
      city: prov.city || payload.city || '',
      area: prov.area || payload.area || '',
      status: 'pending',
      type: 'Emergency',
      is_emergency: true,
      emergency_request_id: requestId,
      booking_date: new Date().toISOString().slice(0, 10),
      booking_time: new Date().toTimeString().split(' ')[0],
      created_at: new Date().toISOString(),
    }));

    if (bookingRows.length > 0) {
      await insertBookings(SUPABASE_URL, SERVICE_KEY, bookingRows);
      console.log(`Inserted ${bookingRows.length} bookings`);

      // Notify providers (non-fatal if this fails)
      try {
        await notifyProviders(SUPABASE_URL, SERVICE_KEY, requestId, reqBody, providers);
        console.log(`Notified ${providers.length} providers`);
      } catch (notifyErr) {
        console.warn('notifyProviders failed (non-fatal):', notifyErr);
      }
    } else {
      console.log('No providers found — emergency request saved without bookings');
    }

    console.log('=== create-emergency END ===');

    return json({
      success: true,
      message: bookingRows.length > 0
        ? `Emergency request created and ${bookingRows.length} provider(s) notified`
        : 'Emergency request saved. No providers found within 25km.',
      request_id: requestId,
      providers_count: bookingRows.length,
    }, 200);

  } catch (err) {
    console.error('create-emergency unhandled error:', err);
    return json({ error: String(err) }, 500);
  }
});

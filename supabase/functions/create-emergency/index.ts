// create-emergency/index.ts
// Edge Function: creates an emergency request, finds nearby providers, and creates bookings.
//
// AUTH DESIGN:
//   - The Android client sends: Authorization: Bearer <SUPABASE_ANON_KEY>
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

// ── Haversine distance ────────────────────────────────────────────────────────

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

// ── Category matching (case-insensitive + alias groups) ──────────────────────
// Handles cases like "AC Service" ↔ "ac repair", "Electrician" ↔ "electrical", etc.

const CATEGORY_ALIASES: Record<string, string[]> = {
  electrician: ['electrician', 'electrical', 'electric', 'wiring', 'power'],
  plumber: ['plumber', 'plumbing', 'pipe', 'drainage', 'water leak'],
  'ac service': ['ac', 'air conditioner', 'air conditioning', 'hvac', 'cooling', 'ac service', 'ac repair'],
  carpentry: ['carpenter', 'carpentry', 'wood', 'furniture'],
  cleaning: ['cleaning', 'cleaner', 'housekeeping', 'sanitization'],
  'appliance repair': ['appliance', 'appliance repair', 'washing machine', 'refrigerator', 'fridge', 'dishwasher'],
  painting: ['painting', 'painter', 'paint', 'color'],
  'general repair': ['general', 'repair', 'maintenance', 'handyman', 'general repair', 'repair & maintenance'],
};

function categoryMatches(requested: string, providerCategory: string): boolean {
  if (!requested || !providerCategory) return false;

  const req = requested.trim().toLowerCase();
  const prov = providerCategory.trim().toLowerCase();

  // 1. Direct match (case-insensitive)
  if (req === prov) return true;

  // 2. Substring match (both directions)
  if (prov.includes(req) || req.includes(prov)) return true;

  // 3. Alias group match — find which group req belongs to, then check prov
  for (const [_group, aliases] of Object.entries(CATEGORY_ALIASES)) {
    const reqInGroup = aliases.some((a) => req.includes(a) || a.includes(req));
    if (!reqInGroup) continue;
    const provInGroup = aliases.some((a) => prov.includes(a) || a.includes(prov));
    if (provInGroup) return true;
  }

  return false;
}

// ── Provider discovery helpers ────────────────────────────────────────────────

async function callRpcFindProviders(
  supabaseUrl: string,
  serviceKey: string,
  payload: { p_lat: number; p_lng: number; p_category: string; p_radius_meters: number }
): Promise<any[]> {
  const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/rpc/find_nearby_providers`;
  try {
    console.log('[RPC] Calling find_nearby_providers:', JSON.stringify(payload));
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
      console.warn(`[RPC] find_nearby_providers failed HTTP ${res.status}: ${text}`);
      return [];
    }
    const result = await res.json();
    const count = Array.isArray(result) ? result.length : 0;
    console.log(`[RPC] Returned ${count} providers`);
    return Array.isArray(result) ? result : [];
  } catch (err) {
    console.warn('[RPC] Call failed, falling back to client-side filter:', err);
    return [];
  }
}

async function fetchProvidersAndFilter(
  supabaseUrl: string,
  serviceKey: string,
  userLat: number,
  userLng: number,
  category: string,
  radiusKm: number
): Promise<any[]> {
  const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/provider_profiles` +
    `?select=id,firebase_uid,business_name,category,` +
    `latitude,longitude,lat,lng,status,is_active,is_approved,city,area` +
    `&limit=2000`;

  console.log("User Lat:", userLat);
  console.log("User Lng:", userLng);
  console.log("Category:", category);

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
      console.warn(`[Fallback] Provider fetch failed HTTP ${res.status}`);
      return [];
    }

    const arr: any[] = await res.json();
    console.log("Total providers in DB:", arr.length);

    const matched: any[] = [];

    for (const p of arr) {
      // EXACT Category match (case-insensitive)
      const provCat = String(p.category || '').trim().toLowerCase();
      const reqCat = String(category || '').trim().toLowerCase();
      
      if (provCat !== reqCat) {
        continue;
      }

      // Active/approved check (Checking is_approved OR is_active just in case)
      if (p.is_approved !== true && p.is_active !== true) {
        continue;
      }

      // Read lat/lng
      let pLat: number | null = null;
      let pLng: number | null = null;

      if (p.latitude != null && p.latitude !== '') pLat = Number(p.latitude);
      else if (p.lat != null && p.lat !== '') pLat = Number(p.lat);

      if (p.longitude != null && p.longitude !== '') pLng = Number(p.longitude);
      else if (p.lng != null && p.lng !== '') pLng = Number(p.lng);

      if (pLat == null || pLng == null || isNaN(pLat) || isNaN(pLng) || pLat === 0 || pLng === 0) {
        continue;
      }

      // Haversine distance
      const dist = haversineKm(userLat, userLng, pLat, pLng);
      p.distance_km = dist;
      matched.push(p);
    }

    // Sort by distance ascending
    matched.sort((a, b) => (a.distance_km ?? 999) - (b.distance_km ?? 999));

    console.log("Providers found:", matched.length);

    // Filter by radius
    let withinRadius = matched.filter(p => p.distance_km <= radiusKm);
    
    // STEP 8: FALLBACK - return nearest 3 if none within radius
    if (withinRadius.length === 0 && matched.length > 0) {
      console.log(`No providers within ${radiusKm}km, falling back to nearest 3 providers anyway`);
      withinRadius = matched.slice(0, 3);
    }
 
    if (withinRadius.length === 0) {
      console.log("NO PROVIDERS FOUND");
    }

    return withinRadius;
  } catch (err) {
    console.warn('[Fallback] Fetch error:', err);
    return [];
  }
}

// ── DB write helpers (all use service role key — bypass RLS) ──────────────────

async function insertBooking(
  supabaseUrl: string,
  serviceKey: string,
  bookingData: Record<string, unknown>
): Promise<{ id: string }> {
  const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/bookings?select=id`;

  console.log("Insert Data:", bookingData);

  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      apikey: serviceKey,
      authorization: `Bearer ${serviceKey}`,
      Prefer: 'return=representation',
    },
    body: JSON.stringify(bookingData),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Insert booking failed HTTP ${res.status}: ${text}`);
  }

  const arr: any[] = await res.json();
  if (!arr || arr.length === 0) {
    throw new Error('Insert booking returned empty result');
  }
  console.log('[DB] Booking inserted successfully with ID:', arr[0].id);
  return arr[0];
}

async function notifyProviders(
  supabaseUrl: string,
  serviceKey: string,
  emergencyData: Record<string, unknown>,
  providersWithBookingId: any[]
): Promise<void> {
  const firebaseKey = Deno.env.get('FIREBASE_SERVER_KEY');
  
  for (const provider of providersWithBookingId) {
    const firebaseUid = provider.firebase_uid || provider.provider_firebase_uid;
    if (!firebaseUid) {
      console.warn(`[Notify] Skipping provider ${provider.id} — no firebase_uid`);
      continue;
    }

    // Fetch FCM token from users table
    let fcmToken = null;
    try {
      const userRes = await fetch(`${supabaseUrl}/rest/v1/users?id=eq.${firebaseUid}&select=fcm_token`, {
        method: 'GET',
        headers: {
          apikey: serviceKey,
          authorization: `Bearer ${serviceKey}`,
          accept: 'application/json'
        }
      });
      if (userRes.ok) {
        const users = await userRes.json();
        if (users.length > 0 && users[0].fcm_token) {
          fcmToken = users[0].fcm_token;
        }
      }
    } catch (e) {
      console.warn(`[Notify] Failed to fetch token for ${firebaseUid}`, e);
    }

    if (!fcmToken) {
      console.warn(`[Notify] No FCM token found for provider ${firebaseUid}`);
      continue;
    }

    if (!firebaseKey) {
      console.warn(`[Notify] FIREBASE_SERVER_KEY not set. Cannot send push to ${fcmToken}`);
      continue;
    }

    const payload = {
      to: fcmToken,
      notification: {
        title: "🚨 Emergency Request",
        body: "New emergency booking nearby"
      },
      data: {
        booking_id: provider.booking_id,
        type: "emergency"
      }
    };

    console.log("Sending to:", fcmToken);

    try {
      const res = await fetch('https://fcm.googleapis.com/fcm/send', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `key=${firebaseKey}`
        },
        body: JSON.stringify(payload),
      });
      if (!res.ok) {
        console.warn(`[Notify] FCM failed for ${firebaseUid}: HTTP ${res.status}`);
      } else {
        console.log(`[Notify] FCM sent to provider ${firebaseUid}`);
      }
    } catch (err) {
      console.warn(`[Notify] Error sending FCM for ${firebaseUid}:`, err);
    }
  }
}

// ── Main handler ──────────────────────────────────────────────────────────────

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  if (req.method !== 'POST') return json({ error: 'Method not allowed' }, 405);

  try {
    // ── 1. Read env ──────────────────────────────────────────────────────────
    const env = Deno.env.toObject();
    const SUPABASE_URL = env.SUPABASE_URL || '';
    const SERVICE_KEY =
      env.SUPABASE_SERVICE_ROLE_KEY ||
      env.SUPABASE_ANON_KEY ||
      env.SUPABASE_API_KEY ||
      '';

    if (!SUPABASE_URL || !SERVICE_KEY) {
      return json({ error: 'Supabase URL or service key not configured' }, 500);
    }

    // ── 2. Validate Authorization header ─────────────────────────────────────
    const authHeader = req.headers.get('authorization') || '';
    if (!authHeader.toLowerCase().startsWith('bearer ') || !authHeader.slice(7).trim()) {
      console.error('[Auth] Missing or empty Authorization header');
      return json({ error: 'Unauthorized: Authorization: Bearer <SUPABASE_ANON_KEY> required.' }, 401);
    }
    console.log(`[Auth] Header OK (token length=${authHeader.slice(7).trim().length})`);

    // ── 3. Parse body ─────────────────────────────────────────────────────────
    let payload: Record<string, unknown>;
    try {
      payload = await req.json();
    } catch {
      return json({ error: 'Invalid JSON body' }, 400);
    }

    // ── 4. Validate required fields ───────────────────────────────────────────
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

    if (isNaN(p_lat) || isNaN(p_lng) || p_lat === 0 || p_lng === 0) {
      return json({ error: `Invalid lat/lng: lat=${p_lat}, lng=${p_lng}` }, 400);
    }

    console.log('=== create-emergency START ===');
    console.log(`[Request] user_id=${payload.user_id} | category="${p_category}" | lat=${p_lat}, lng=${p_lng} | radius=${p_radius_km}km`);

    // ── 5. Find nearby providers ──────────────────────────────────────────────
    let providers: any[] = [];

    // Try PostGIS RPC first
    try {
      providers = await callRpcFindProviders(SUPABASE_URL, SERVICE_KEY, {
        p_lat,
        p_lng,
        p_category,
        p_radius_meters,
      });
    } catch (e) {
      console.warn('[RPC] Error:', e);
    }

    // Fallback: fetch all + client-side haversine filter
    if (!Array.isArray(providers) || providers.length === 0) {
      console.log('[Search] RPC returned 0, switching to fallback haversine filter...');
      providers = await fetchProvidersAndFilter(
        SUPABASE_URL, SERVICE_KEY, p_lat, p_lng, p_category, p_radius_km
      );
    }

    // ── 7. Check if providers exist ──────────────────────────────────────────
    if (providers.length === 0) {
      console.log('[Search] No providers found even after 50km expansion');
      return json({ error: "No providers available in your area" }, 400);
    }

    // ── 8. Insert SINGLE booking (Emergency) ──────────────────────────────────
    const targetProvider = providers[0];

    const bookingData: Record<string, unknown> = {
      customer_id: String(payload.user_id),
      provider_id: targetProvider.id,
      provider_firebase_uid: targetProvider.firebase_uid,
      customer_name: String(payload.user_name || 'Emergency User'),
      customer_phone: String(payload.phone || ''),
      provider_name: targetProvider.business_name || 'Awaiting Acceptance',
      service_name: 'Emergency - ' + p_category,
      category: p_category,
      address: String(payload.address || ''),
      description: String(payload.description || ''),
      city: String(payload.city || ''),
      area: String(payload.area || ''), // Area is still stored here, but UI won't use it
      status: 'pending',
      is_emergency: true,
      booking_date: new Date().toISOString().slice(0, 10),
      booking_time: new Date().toTimeString().split(' ')[0],
      latitude: p_lat,
      longitude: p_lng,
    };

    let bookingId = '';
    try {
      const created = await insertBooking(SUPABASE_URL, SERVICE_KEY, bookingData);
      bookingId = created.id;
    } catch (err) {
      console.warn(`[DB] Failed to insert emergency booking:`, err);
    }

    // ── 9. Notify all matched providers ───────────────────────────────────────
    if (bookingId) {
      // Map all matched providers to this single booking ID for notifications
      const notifiedProviders = providers.map(p => ({
        ...p,
        booking_id: bookingId
      }));

      try {
        await notifyProviders(SUPABASE_URL, SERVICE_KEY, payload, notifiedProviders);
      } catch (notifyErr) {
        console.warn('[Notify] Failed (non-fatal):', notifyErr);
      }
    }

    console.log('=== create-emergency END ===');

    const nearestDist = providers.length > 0
      ? `${(providers[0].distance_km ?? 0).toFixed(1)}km`
      : null;

    return json({
      success: true,
      message: `Emergency request created and ${providers.length} provider(s) notified`,
      request_id: bookingId,
      providers_count: providers.length,
      nearest_provider_distance_km: nearestDist,
    }, 200);

  } catch (err) {
    console.error('[Error] create-emergency unhandled:', err);
    return json({ error: String(err) }, 500);
  }
});

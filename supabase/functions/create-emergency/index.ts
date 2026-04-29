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

async function callRpcFindProviders(supabaseUrl: string, apiKey: string, payload: any) {
  const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/rpc/find_nearby_providers`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      apikey: apiKey,
      authorization: `Bearer ${apiKey}`,
      Accept: 'application/json',
    },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`RPC find_nearby_providers failed HTTP ${res.status} ${text}`);
  }
  return res.json();
}

async function insertEmergencyRequest(supabaseUrl: string, apiKey: string, body: any) {
  const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/emergency_requests?select=id`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      apikey: apiKey,
      authorization: `Bearer ${apiKey}`,
      Prefer: 'return=representation',
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Insert emergency_request failed HTTP ${res.status} ${text}`);
  }
  const arr = await res.json();
  return arr[0];
}

async function insertBookings(supabaseUrl: string, apiKey: string, rows: any[]) {
  if (!Array.isArray(rows) || rows.length === 0) return [];
  const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/emergency_request_bookings`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      apikey: apiKey,
      authorization: `Bearer ${apiKey}`,
      Prefer: 'return=representation',
    },
    body: JSON.stringify(rows),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Insert bookings failed HTTP ${res.status} ${text}`);
  }
  return res.json();
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
  if (req.method !== 'POST') return json({ error: 'Method not allowed' }, 405);

  try {
    const env = Deno.env.toObject();
    const SUPABASE_URL = env.SUPABASE_URL || env.NEXT_PUBLIC_SUPABASE_URL || '';
    const SUPABASE_API_KEY = env.SUPABASE_SERVICE_ROLE_KEY || env.SUPABASE_ANON_KEY || env.SUPABASE_API_KEY || '';

    if (!SUPABASE_URL || !SUPABASE_API_KEY) {
      return json({ error: 'Supabase URL or API key not configured' }, 500);
    }

    const payload = await req.json();
    const required = ['user_id', 'user_name', 'phone', 'category', 'lat', 'lng'];
    for (const k of required) {
      if (payload[k] === undefined || payload[k] === null || String(payload[k]).trim() === '') {
        return json({ error: `Missing field: ${k}` }, 400);
      }
    }

    const p_lat = Number(payload.lat);
    const p_lng = Number(payload.lng);
    const p_category = String(payload.category || '').trim();
    const p_radius_km = Number(payload.radius_km || 25);

    // 1) Find providers via RPC
    const providers = await callRpcFindProviders(SUPABASE_URL, SUPABASE_API_KEY, {
      p_lat,
      p_lng,
      p_category,
      p_radius_km,
    });

    if (!Array.isArray(providers) || providers.length === 0) {
      return json({ success: false, message: 'No providers available within 25km', providers: [] }, 200);
    }

    // 2) Insert emergency_requests row
    const reqBody = {
      user_id: String(payload.user_id),
      user_name: String(payload.user_name),
      phone: String(payload.phone),
      category: p_category,
      description: String(payload.description || ''),
      lat: p_lat,
      lng: p_lng,
      city: String(payload.city || ''),
      area: String(payload.area || ''),
      address: String(payload.address || ''),
      status: 'pending',
    };

    const created = await insertEmergencyRequest(SUPABASE_URL, SUPABASE_API_KEY, reqBody);
    const requestId = created.id;

    // 3) Create bookings records for all providers
    const bookingRows = providers.map((prov: any) => ({
      emergency_request_id: requestId,
      provider_id: prov.id || prov.provider_id || prov.id,
      provider_firebase_uid: prov.firebase_uid || prov.provider_firebase_uid || null,
      status: 'pending',
      booking_date: new Date().toISOString().slice(0, 10),
      booking_time: new Date().toTimeString().split(' ')[0],
      created_at: new Date().toISOString(),
    }));

    await insertBookings(SUPABASE_URL, SUPABASE_API_KEY, bookingRows);

    return json({ success: true, message: 'Emergency request created', request_id: requestId, providers_count: providers.length }, 200);
  } catch (err) {
    console.error('create-emergency error', err);
    return json({ error: String(err) }, 500);
  }
});

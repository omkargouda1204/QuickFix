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
  try {
    console.log("Calling RPC with payload:", JSON.stringify(payload));
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
      console.warn(`RPC find_nearby_providers failed HTTP ${res.status}: ${text}`);
      return [];
    }
    const result = await res.json();
    console.log(`RPC returned ${Array.isArray(result) ? result.length : 0} providers`);
    return result;
  } catch (err) {
    console.warn('RPC call failed, falling back:', err);
    return [];
  }
}

function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number) {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

async function fetchProvidersAndFilter(supabaseUrl: string, apiKey: string, lat: number, lng: number, category: string, radiusKm: number) {
  const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/provider_profiles?select=id,firebase_uid,business_name,category,latitude,longitude,city,area,is_active&limit=1000`;
  try {
    const res = await fetch(url, {
      method: 'GET',
      headers: {
        apikey: apiKey,
        authorization: `Bearer ${apiKey}`,
        Accept: 'application/json',
      },
    });
    if (!res.ok) {
      console.warn(`Provider fetch failed HTTP ${res.status}`);
      return [];
    }
    const arr = await res.json();
    const out: any[] = [];
    const lcCategory = (category || '').toLowerCase();
    
    for (const p of arr) {
      // Category match (case-insensitive)
      const provCat = (p.category || '').toString().toLowerCase();
      if (provCat !== lcCategory) {
        continue;
      }
      
      // Check active
      if (p.is_active === false) continue;
      
      // Check lat/lng exist
      const pLat = Number(p.latitude);
      const pLng = Number(p.longitude);
      if (isNaN(pLat) || isNaN(pLng)) {
        console.warn(`Provider ${p.id} missing valid lat/lng: ${pLat}, ${pLng}`);
        continue;
      }
      
      // Distance check
      const dist = haversineKm(lat, lng, pLat, pLng);
      if (dist <= radiusKm) {
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

async function insertEmergencyRequest(supabaseUrl: string, apiKey: string, body: any) {
  const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/emergency_requests?select=id`;
  const payload = {
    ...body,
    user_id: String(body.user_id || ''),
  };
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      apikey: apiKey,
      authorization: `Bearer ${apiKey}`,
      Prefer: 'return=representation',
    },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Insert emergency_request failed HTTP ${res.status}: ${text}`);
  }
  const arr = await res.json();
  return arr[0];
}

async function insertBookings(supabaseUrl: string, apiKey: string, rows: any[]) {
  if (!Array.isArray(rows) || rows.length === 0) return [];
  const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/bookings`;
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
    throw new Error(`Insert bookings failed HTTP ${res.status}: ${text}`);
  }
  return res.json();
}

async function notifyProviders(supabaseUrl: string, apiKey: string, requestId: string, emergencyData: any, providers: any[]) {
  try {
    for (const provider of providers) {
      const firebaseUid = provider.firebase_uid;
      if (!firebaseUid) continue;
      
      const notificationPayload = {
        user_id: firebaseUid,
        type: 'emergency_request',
        title: `Emergency: ${emergencyData.category}`,
        body: `Emergency request from ${emergencyData.user_name}. Tap to view.`,
        request_id: requestId,
        quick_action: 'accept',
      };
      
      const notifyUrl = `${supabaseUrl.replace(/\/$/, '')}/functions/v1/send-notification`;
      const res = await fetch(notifyUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          apikey: apiKey,
          authorization: `Bearer ${apiKey}`,
        },
        body: JSON.stringify(notificationPayload),
      });
      
      if (!res.ok) {
        console.warn(`Notification send failed for provider ${firebaseUid}: HTTP ${res.status}`);
      } else {
        console.log(`Notification sent to provider ${firebaseUid}`);
      }
    }
  } catch (err) {
    console.warn('notifyProviders error:', err);
  }
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
    const required = ['user_id', 'category', 'lat', 'lng'];
    for (const k of required) {
      if (payload[k] === undefined || payload[k] === null || String(payload[k]).trim() === '') {
        return json({ error: `Missing field: ${k}` }, 400);
      }
    }

    const p_lat = Number(payload.lat);
    const p_lng = Number(payload.lng);
    const p_category = String(payload.category || '').trim();
    const p_radius_meters = Number(payload.radius_meters || 25000);

    console.log('=== create-emergency START ===');
    console.log('User Location:', { lat: p_lat, lng: p_lng });
    console.log('Category:', p_category);
    console.log('Radius:', p_radius_meters, 'meters');

    // 1) Try RPC (DB-side geo query with ST_DWithin)
    let providers: any[] = [];
    try {
      providers = await callRpcFindProviders(SUPABASE_URL, SUPABASE_API_KEY, {
        p_lat,
        p_lng,
        p_category,
        p_radius_meters,
      });
    } catch (e) {
      console.warn('RPC error:', e);
      providers = [];
    }

    // 2) If RPC empty, fallback to client-side haversine filter
    if (!Array.isArray(providers) || providers.length === 0) {
      console.log('RPC returned no results, using fallback...');
      const fallback = await fetchProvidersAndFilter(SUPABASE_URL, SUPABASE_API_KEY, p_lat, p_lng, p_category, p_radius_meters / 1000);
      providers = fallback;
    }

    console.log('Total providers found:', providers.length);

    // 3) Always insert emergency_requests row
    const reqBody: any = {
      user_id: String(payload.user_id),
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

    const created = await insertEmergencyRequest(SUPABASE_URL, SUPABASE_API_KEY, reqBody);
    const requestId = created.id;
    console.log('Emergency request created:', requestId);

    // 4) Create bookings for matched providers
    const bookingRows = (providers || []).map((prov: any) => ({
      customer_id: String(payload.user_id),
      customer_name: String(payload.user_name || ''),
      customer_phone: String(payload.phone || ''),
      provider_id: prov.id,
      provider_firebase_uid: prov.firebase_uid,
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
      await insertBookings(SUPABASE_URL, SUPABASE_API_KEY, bookingRows);
      console.log(`Inserted ${bookingRows.length} bookings`);
      
      // Notify all matched providers
      await notifyProviders(SUPABASE_URL, SUPABASE_API_KEY, requestId, reqBody, providers);
      console.log(`Notified ${providers.length} providers`);
    } else {
      console.log('No providers found, no bookings created');
    }

    console.log('=== create-emergency END ===');

    if (bookingRows.length === 0) {
      return json({
        success: true,
        message: 'Emergency request saved. No providers found within 25km.',
        request_id: requestId,
        providers_count: 0,
      }, 200);
    }

    return json({
      success: true,
      message: 'Emergency request created and notified to providers',
      request_id: requestId,
      providers_count: bookingRows.length,
    }, 200);
  } catch (err) {
    console.error('create-emergency error:', err);
    return json({ error: String(err) }, 500);
  }
});
" "
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
        };

        function json(data: unknown, status = 200) {
          return new Response(JSON.stringify(data), {
            status,
            headers: { ...corsHeaders, "Content-Type": "application/json" },
          });
        }

        // Try calling an existing RPC first. If RPC is not available or returns empty,
        // fall back to fetching provider_profiles and performing server-side geo filtering.
        async function callRpcFindProviders(supabaseUrl: string, apiKey: string, payload: any) {
          const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/rpc/find_nearby_providers`;
          try {
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
              console.warn(`RPC find_nearby_providers failed HTTP ${res.status} ${text}`);
              return [];
            }
            return await res.json();
          } catch (err) {
            console.warn('RPC call failed, falling back to client-side filter', err);
            return [];
          }
        }

        function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number) {
          const R = 6371;
          const dLat = (lat2 - lat1) * Math.PI / 180;
          const dLon = (lon2 - lon1) * Math.PI / 180;
          const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
          const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
          return R * c;
        }

        async function fetchProvidersAndFilter(supabaseUrl: string, apiKey: string, lat: number, lng: number, category: string, radiusKm: number) {
          const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/provider_profiles?select=id,firebase_uid,business_name,category,service_category,latitude,longitude,lat,lng,status,is_active,city,area&limit=1000`;
          const res = await fetch(url, {
            method: 'GET',
            headers: {
              apikey: apiKey,
              authorization: `Bearer ${apiKey}`,
              Accept: 'application/json',
            },
          });
          if (!res.ok) {
            const text = await res.text();
            console.warn('Provider fetch failed HTTP ' + res.status + ' ' + text);
            return [];
          }
          const arr = await res.json();
          const out: any[] = [];
          const lcCategory = (category || '').toLowerCase();
          for (const p of arr) {
            // normalize category
            const provCat = (p.service_category || p.category || '').toString().toLowerCase();
            if (provCat === '' || !provCat.includes(lcCategory) && !lcCategory.includes(provCat)) {
              continue;
            }
            // check active flag
            if (p.is_active === false || p.status === 'inactive') continue;

            // read lat/lng flexibly
            let pLat = null;
            let pLng = null;
            if (p.latitude !== undefined && p.latitude !== null) pLat = Number(p.latitude);
            else if (p.lat !== undefined && p.lat !== null) pLat = Number(p.lat);
            if (p.longitude !== undefined && p.longitude !== null) pLng = Number(p.longitude);
            else if (p.lng !== undefined && p.lng !== null) pLng = Number(p.lng);
            if (pLat == null || pLng == null || isNaN(pLat) || isNaN(pLng)) continue;

            const dist = haversineKm(lat, lng, pLat, pLng);
            if (!isNaN(dist) && dist <= radiusKm) {
              p.distance_km = dist;
              out.push(p);
            }
          }
          return out;
        }

        async function insertEmergencyRequest(supabaseUrl: string, apiKey: string, body: any) {
          const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/emergency_requests?select=id`;
          // ensure string types for firebase uid fields to avoid uuid/text mismatch
          const payload = {
            ...body,
            user_id: String(body.user_id || ''),
            user_firebase_uid: String(body.user_id || ''),
          };
          const res = await fetch(url, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              apikey: apiKey,
              authorization: `Bearer ${apiKey}`,
              Prefer: 'return=representation',
            },
            body: JSON.stringify(payload),
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
          const url = `${supabaseUrl.replace(/\/$/, '')}/rest/v1/bookings`;
          const castRows = rows.map(r => ({
            ...r,
            customer_id: String(r.customer_id || ''),
            provider_id: r.provider_id, // provider_id should be DB id (uuid) as returned by provider_profiles
          }));
          const res = await fetch(url, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              apikey: apiKey,
              authorization: `Bearer ${apiKey}`,
              Prefer: 'return=representation',
            },
            body: JSON.stringify(castRows),
          });
          if (!res.ok) {
            const text = await res.text();
            throw new Error(`Insert bookings failed HTTP ${res.status} ${text}`);
          }
          return res.json();
        }

        async function notifyProviders(supabaseUrl: string, apiKey: string, requestId: string, emergencyData: any, providers: any[]) {
          try {
            for (const provider of providers) {
              const firebaseUid = provider.firebase_uid || provider.provider_firebase_uid || provider.firebaseUid || provider.firebase_uid;
              if (!firebaseUid) continue;
              const notificationPayload = {
                user_id: firebaseUid,
                type: 'emergency_request',
                title: `Emergency: ${emergencyData.category}`,
                body: `Emergency request from ${emergencyData.user_name}. Tap to view.`,
                request_id: requestId,
                quick_action: 'accept',
              };
              const notifyUrl = `${supabaseUrl.replace(/\/$/, '')}/functions/v1/send-notification`;
              const res = await fetch(notifyUrl, {
                method: 'POST',
                headers: {
                  'Content-Type': 'application/json',
                  apikey: apiKey,
                  authorization: `Bearer ${apiKey}`,
                },
                body: JSON.stringify(notificationPayload),
              });
              if (!res.ok) {
                console.warn(`Notification send failed for provider ${firebaseUid}: HTTP ${res.status}`);
              }
            }
          } catch (err) {
            console.warn('notifyProviders error:', err);
          }
        }

        // Main handler
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
            const required = ['user_id', 'category', 'lat', 'lng'];
            for (const k of required) {
              if (payload[k] === undefined || payload[k] === null || String(payload[k]).trim() === '') {
                return json({ error: `Missing field: ${k}` }, 400);
              }
            }

            const p_lat = Number(payload.lat);
            const p_lng = Number(payload.lng);
            const p_category = String(payload.category || '').trim();
            const p_radius_km = Number(payload.radius_km || 25);

            console.log('create-emergency input:', { lat: p_lat, lng: p_lng, category: p_category });

            // 1) Try RPC (DB-side geo query) first
            let providers: any[] = [];
            try {
              providers = await callRpcFindProviders(SUPABASE_URL, SUPABASE_API_KEY, {
                p_lat,
                p_lng,
                p_category,
                p_radius_km,
              });
              console.log('RPC providers count:', Array.isArray(providers) ? providers.length : 0);
            } catch (e) {
              console.warn('RPC error:', e);
              providers = [];
            }

            // 2) If RPC did not return results, fallback to fetching provider_profiles and filtering server-side
            if (!Array.isArray(providers) || providers.length === 0) {
              const fallback = await fetchProvidersAndFilter(SUPABASE_URL, SUPABASE_API_KEY, p_lat, p_lng, p_category, p_radius_km);
              providers = fallback;
              console.log('Fallback providers count:', providers.length);
            }

            // 3) Insert emergency_requests row (always)
            const reqBody: any = {
              user_id: String(payload.user_id),
              user_name: String(payload.user_name || ''),
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

            const created = await insertEmergencyRequest(SUPABASE_URL, SUPABASE_API_KEY, reqBody);
            const requestId = created.id;

            // 4) Create booking rows for matched providers (or none)
            const bookingRows = (providers || []).map((prov: any) => ({
              customer_id: String(payload.user_id),
              customer_name: String(payload.user_name || ''),
              customer_phone: String(payload.phone || ''),
              provider_id: prov.id || prov.provider_id || prov.id,
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
              await insertBookings(SUPABASE_URL, SUPABASE_API_KEY, bookingRows);
              // notify all matched providers
              await notifyProviders(SUPABASE_URL, SUPABASE_API_KEY, requestId, reqBody, providers);
            }

            console.log('create-emergency done. providers_found=', bookingRows.length);

            if (!bookingRows || bookingRows.length === 0) {
              return json({ success: true, message: 'No providers available within 25km', request_id: requestId, providers_count: 0 }, 200);
            }

            return json({ success: true, message: 'Emergency request created', request_id: requestId, providers_count: bookingRows.length }, 200);
          } catch (err) {
            console.error('create-emergency error', err);
            return json({ error: String(err) }, 500);
          }
        });
      status: 'pending',
    };

    const created = await insertEmergencyRequest(SUPABASE_URL, SUPABASE_API_KEY, reqBody);
    const requestId = created.id;

    // 3) Create bookings records for all providers
    const bookingRows = providers.map((prov: any) => ({
      customer_id: String(payload.user_id),
      customer_name: String(payload.user_name),
      customer_phone: String(payload.phone),
      provider_id: prov.id || prov.provider_id || prov.id,
      provider_firebase_uid: prov.firebase_uid || prov.provider_firebase_uid || null,
      provider_name: "Awaiting Acceptance",
      service_name: "Emergency - " + p_category,
      service_category: p_category,
      category: p_category,
      address: String(payload.address || ''),
      description: String(payload.description || ''),
      city: String(payload.city || ''),
      area: String(payload.area || ''),
      status: 'pending',
      type: 'Emergency',
      is_emergency: true,
      emergency_request_id: requestId,
      booking_date: new Date().toISOString().slice(0, 10),
      booking_time: new Date().toTimeString().split(' ')[0],
      created_at: new Date().toISOString(),
    }));

    await insertBookings(SUPABASE_URL, SUPABASE_API_KEY, bookingRows);

    // 4) Notify all providers
    await notifyProviders(SUPABASE_URL, SUPABASE_API_KEY, requestId, reqBody, providers);

    return json({ success: true, message: 'Emergency request created', request_id: requestId, providers_count: providers.length }, 200);
  } catch (err) {
    console.error('create-emergency error', err);
    return json({ error: String(err) }, 500);
  }
});

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const { user_id, type, title, body, data } = await req.json()

    if (!user_id) {
      return new Response(JSON.stringify({ error: 'user_id is required' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? ''
    const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    const firebaseKey = Deno.env.get('FIREBASE_SERVER_KEY') ?? ''

    // 1. Fetch FCM token
    // We check the 'users' table which is used in create-emergency
    const userRes = await fetch(`${supabaseUrl}/rest/v1/users?id=eq.${user_id}&select=fcm_token`, {
      method: 'GET',
      headers: {
        apikey: serviceKey,
        authorization: `Bearer ${serviceKey}`,
        accept: 'application/json'
      }
    })

    if (!userRes.ok) {
      const err = await userRes.text()
      console.error('Failed to fetch user token:', err)
      return new Response(JSON.stringify({ error: 'Failed to fetch user token' }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    const users = await userRes.json()
    const fcmToken = users[0]?.fcm_token

    if (!fcmToken) {
      console.warn(`No FCM token found for user ${user_id}`)
      return new Response(JSON.stringify({ success: false, message: 'No FCM token found' }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      })
    }

    // 2. Send via FCM
    const fcmPayload = {
      to: fcmToken,
      notification: {
        title: title || 'QuickFix Update',
        body: body || 'You have a new update',
      },
      data: {
        type: type || 'general',
        ...(data || {})
      },
    }

    const fcmRes = await fetch('https://fcm.googleapis.com/fcm/send', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `key=${firebaseKey}`,
      },
      body: JSON.stringify(fcmPayload),
    })

    const fcmResult = await fcmRes.json()
    console.log('FCM Result:', fcmResult)

    return new Response(JSON.stringify({ success: true, result: fcmResult }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })

  } catch (error) {
    console.error('Error in send-notification:', error)
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    })
  }
})

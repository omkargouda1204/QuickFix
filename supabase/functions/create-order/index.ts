import Razorpay from "npm:razorpay@2.9.2";

type CreateOrderPayload = {
  amount?: number;
  booking_id?: string;
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  try {
    const payload = (await req.json()) as CreateOrderPayload;
    const amountInr = Number(payload.amount ?? 0);

    if (!Number.isFinite(amountInr) || amountInr <= 0) {
      return json({ error: "amount must be a positive number in INR" }, 400);
    }

    const keyId = Deno.env.get("RAZORPAY_KEY_ID") ?? "";
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET") ?? "";

    if (!keyId || !keySecret) {
      return json({ error: "Razorpay secrets are not configured" }, 500);
    }

    const razorpay = new Razorpay({
      key_id: keyId,
      key_secret: keySecret,
    });

    const amountPaise = Math.round(amountInr * 100);
    const order = await razorpay.orders.create({
      amount: amountPaise,
      currency: "INR",
      receipt: `qf_${Date.now()}`,
      notes: {
        booking_id: (payload.booking_id ?? "").trim(),
      },
    });

    return json({
      id: order.id,
      amount: order.amount,
      currency: order.currency,
      receipt: order.receipt,
      status: order.status,
    });
  } catch (error) {
    console.error("create-order edge error", error);
    return json({ error: String(error) }, 500);
  }
});

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}


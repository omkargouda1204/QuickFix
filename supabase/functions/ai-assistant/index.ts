import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.4";

type AiPayload = {
  user_id?: string;
  message?: string;
  prompt?: string;
};

type AiResult = {
  reply: string;
  service_type: string;
  possible_issue: string;
  estimated_cost: string;
  booking_suggestion: string;
  suggested_category: string;
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    const payload = (await req.json()) as AiPayload;
    const message = (payload.message ?? "").trim();
    const userId = (payload.user_id ?? "").trim();
    const systemPrompt = (payload.prompt ?? DEFAULT_PROMPT).trim();

    if (!message) return json({ error: "message is required" }, 400);

    const result = await generateAiResult(systemPrompt, message);
    const replyText = formatReply(result);

    await persistConversation(userId, message, replyText);

    return json({
      ...result,
      reply: replyText,
    });
  } catch (error) {
    console.error("ai-assistant edge error", error);
    return json({ error: String(error) }, 500);
  }
});

const DEFAULT_PROMPT = `You are an AI assistant for a service booking app.
Help users identify problems, suggest services, estimate cost in INR, and guide them to book services.
Return JSON only with keys: reply, service_type, possible_issue, estimated_cost, booking_suggestion, suggested_category.
Use concise language.`;

async function generateAiResult(systemPrompt: string, message: string): Promise<AiResult> {
  const openAiKey = (Deno.env.get("OPENAI_API_KEY") ?? "").trim();
  const geminiKey = (Deno.env.get("GEMINI_API_KEY") ?? "").trim();

  if (openAiKey) {
    const model = (Deno.env.get("OPENAI_MODEL") ?? "gpt-4o-mini").trim() || "gpt-4o-mini";
    const resp = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${openAiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model,
        temperature: 0.4,
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: message },
        ],
      }),
    });

    if (resp.ok) {
      const data = await resp.json();
      const content = data?.choices?.[0]?.message?.content ?? "";
      const parsed = extractAiResult(content, message);
      if (parsed) return parsed;
    }
  }

  if (geminiKey) {
    const resp = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${geminiKey}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [
          {
            role: "user",
            parts: [{ text: `${systemPrompt}\n\nUser message: ${message}` }],
          },
        ],
        generationConfig: { temperature: 0.4 },
      }),
    });

    if (resp.ok) {
      const data = await resp.json();
      const content = data?.candidates?.[0]?.content?.parts?.map((p: { text?: string }) => p.text ?? "").join(" ") ?? "";
      const parsed = extractAiResult(content, message);
      if (parsed) return parsed;
    }
  }

  return heuristicResult(message);
}

function extractAiResult(content: string, fallbackMessage: string): AiResult | null {
  const jsonText = firstJsonBlock(content) ?? content;
  try {
    const obj = JSON.parse(jsonText);
    return normalizeResult(obj, fallbackMessage);
  } catch {
    return null;
  }
}

function normalizeResult(obj: Record<string, unknown>, fallbackMessage: string): AiResult {
  const reply = cleanText(String(obj.reply ?? obj.answer ?? "")).trim() || heuristicResult(fallbackMessage).reply;
  const serviceType = cleanText(String(obj.service_type ?? obj.serviceType ?? "")).trim() || heuristicResult(fallbackMessage).service_type;
  const possibleIssue = cleanText(String(obj.possible_issue ?? obj.issue ?? "")).trim() || heuristicResult(fallbackMessage).possible_issue;
  const estimatedCost = cleanText(String(obj.estimated_cost ?? obj.cost ?? "")).trim() || heuristicResult(fallbackMessage).estimated_cost;
  const bookingSuggestion = cleanText(String(obj.booking_suggestion ?? obj.suggestion ?? "")).trim() || heuristicResult(fallbackMessage).booking_suggestion;
  const suggestedCategory = cleanText(String(obj.suggested_category ?? obj.category ?? serviceType ?? "")).trim() || heuristicResult(fallbackMessage).suggested_category;

  return { reply, service_type: serviceType, possible_issue: possibleIssue, estimated_cost: estimatedCost, booking_suggestion: bookingSuggestion, suggested_category: suggestedCategory };
}

function heuristicResult(message: string): AiResult {
  const text = message.toLowerCase();
  if (text.includes("ac")) {
    return {
      reply: "It looks like an AC cooling issue.",
      service_type: "AC Repair",
      possible_issue: "Possible issue: Gas leakage or low refrigerant",
      estimated_cost: "Estimated cost: ₹800–₹1500",
      booking_suggestion: "Book an AC technician for diagnosis and repair.",
      suggested_category: "AC Service",
    };
  }
  if (text.includes("clean")) {
    return {
      reply: "You likely need a cleaning service.",
      service_type: "Home Cleaning",
      possible_issue: "Possible issue: Deep dust or clutter buildup",
      estimated_cost: "Estimated cost: ₹500–₹2000",
      booking_suggestion: "Book a cleaning professional for the best result.",
      suggested_category: "Cleaning",
    };
  }
  if (text.includes("paint")) {
    return {
      reply: "Painting services can refresh your space.",
      service_type: "Painting",
      possible_issue: "Possible issue: Wall touch-up or full repaint needed",
      estimated_cost: "Estimated cost: ₹1200–₹5000",
      booking_suggestion: "Choose a painter after checking the wall area and finish.",
      suggested_category: "Painting",
    };
  }
  if (text.includes("plumb")) {
    return {
      reply: "This sounds like a plumbing issue.",
      service_type: "Plumbing",
      possible_issue: "Possible issue: Leak, blockage, or fitting replacement",
      estimated_cost: "Estimated cost: ₹400–₹2000",
      booking_suggestion: "Book a plumber for inspection and repair.",
      suggested_category: "Plumbing",
    };
  }

  return {
    reply: "I can help with AC repair, cleaning, painting, plumbing, electrical work, and more.",
    service_type: "General Service Help",
    possible_issue: "Possible issue: Please share more details",
    estimated_cost: "Estimated cost: Ask for a quick quote",
    booking_suggestion: "Tell me the problem and I’ll suggest the right service.",
    suggested_category: "",
  };
}

function formatReply(result: AiResult): string {
  const parts = [
    result.reply,
    result.service_type ? `Suggested service: ${result.service_type}` : "",
    result.possible_issue,
    result.estimated_cost,
    result.booking_suggestion,
  ].map((s) => (s ?? "").trim()).filter(Boolean);
  return parts.join("\n\n");
}

async function persistConversation(userId: string, message: string, response: string): Promise<void> {
  const supabaseUrl = (Deno.env.get("SUPABASE_URL") ?? "").trim();
  const serviceRoleKey = (Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "").trim();
  if (!supabaseUrl || !serviceRoleKey || !userId) return;

  const supabase = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  await supabase.from("ai_messages").insert({
    user_id: userId,
    message,
    response,
  });
}

function firstJsonBlock(text: string): string | null {
  const fenced = text.match(/```json\s*([\s\S]*?)```/i);
  if (fenced?.[1]) return fenced[1].trim();
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start >= 0 && end > start) return text.slice(start, end + 1).trim();
  return null;
}

function cleanText(value: string): string {
  return value
    .replace(/^\s*[-*]\s*/gm, "")
    .replace(/\r/g, "")
    .trim();
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

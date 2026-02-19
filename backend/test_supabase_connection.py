"""
test_supabase_connection.py - Verify Supabase credentials from .env

Run this to check if your SUPABASE_SERVICE_ROLE_KEY is correct.
"""
import os
from dotenv import load_dotenv

load_dotenv()

SUPABASE_URL = os.getenv("SUPABASE_URL", "")
SUPABASE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY", "")

print("=" * 70)
print("SUPABASE CONNECTION TEST")
print("=" * 70)
print(f"SUPABASE_URL: {SUPABASE_URL}")
print(f"SUPABASE_KEY: {SUPABASE_KEY[:20]}...{SUPABASE_KEY[-20:]}" if len(SUPABASE_KEY) > 40 else f"SUPABASE_KEY: {SUPABASE_KEY}")
print()

# Check if key looks valid
if not SUPABASE_URL or not SUPABASE_KEY:
    print("❌ ERROR: SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY is missing in .env")
    exit(1)

if SUPABASE_KEY.startswith("sb_publishable_"):
    print("❌ ERROR: You are using the PUBLIC/ANON key!")
    print()
    print("The key starts with 'sb_publishable_' which is the anon key.")
    print("You MUST use the SERVICE_ROLE key (starts with 'eyJ...').")
    print()
    print("Steps to fix:")
    print("1. Go to https://supabase.com/dashboard")
    print("2. Select your project")
    print("3. Settings → API")
    print("4. Copy the 'service_role' key (NOT 'anon' key)")
    print("5. Update SUPABASE_SERVICE_ROLE_KEY in .env")
    print("6. Restart backend")
    exit(1)

if not SUPABASE_KEY.startswith("eyJ"):
    print("⚠️  WARNING: Service role key usually starts with 'eyJ...'")
    print("   Your key might be incorrect.")
    print()

# Try to connect
try:
    from supabase import create_client
    print("📡 Testing connection to Supabase...")
    client = create_client(SUPABASE_URL, SUPABASE_KEY)

    # Try a simple query
    result = client.table("users").select("id").limit(1).execute()

    print("✅ SUCCESS: Connected to Supabase!")
    print(f"   Users table is accessible.")
    print(f"   Found {len(result.data)} user(s) in database.")
    print()
    print("=" * 70)
    print("Your Supabase credentials are CORRECT! ✅")
    print("=" * 70)

except Exception as e:
    print(f"❌ ERROR: {e}")
    print()
    if "Invalid API key" in str(e):
        print("The service_role key is invalid.")
        print("Please double-check you copied the FULL key from Supabase.")
    elif "users" in str(e).lower() and "does not exist" in str(e).lower():
        print("Connection works, but 'users' table doesn't exist.")
        print("Run schema.sql in Supabase SQL Editor to create tables.")
    else:
        print("Connection failed. Check your SUPABASE_URL and key.")
    exit(1)


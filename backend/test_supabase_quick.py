"""
Quick test to verify Supabase connection with the new service_role key.
Run this AFTER restarting backend to confirm everything works.
"""
import os
from dotenv import load_dotenv

load_dotenv()

SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_SERVICE_ROLE_KEY")

print("=" * 70)
print("TESTING SUPABASE CONNECTION")
print("=" * 70)
print(f"URL: {SUPABASE_URL}")
print(f"Key: {SUPABASE_KEY[:30]}...{SUPABASE_KEY[-20:]}")
print()

if not SUPABASE_KEY.startswith("eyJ"):
    print("ERROR: Key doesn't start with 'eyJ' - wrong key format!")
    exit(1)

print("Step 1: Importing supabase...")
try:
    from supabase import create_client
    print("  OK - supabase library imported")
except Exception as e:
    print(f"  FAIL: {e}")
    exit(1)

print("Step 2: Creating client...")
try:
    client = create_client(SUPABASE_URL, SUPABASE_KEY)
    print("  OK - client created")
except Exception as e:
    print(f"  FAIL: {e}")
    exit(1)

print("Step 3: Testing 'users' table access...")
try:
    result = client.table("users").select("id").limit(1).execute()
    print(f"  OK - users table exists (found {len(result.data)} rows)")
except Exception as e:
    if "does not exist" in str(e).lower():
        print("  WARNING: 'users' table doesn't exist yet")
        print("  You need to run schema.sql in Supabase SQL Editor")
        print("  See: https://supabase.com/dashboard/project/rddzycqxtgryrpymivfd/editor")
    else:
        print(f"  FAIL: {e}")
        exit(1)

print("Step 4: Testing 'otp_codes' table access...")
try:
    result = client.table("otp_codes").select("id").limit(1).execute()
    print(f"  OK - otp_codes table exists (found {len(result.data)} rows)")
except Exception as e:
    if "does not exist" in str(e).lower():
        print("  WARNING: 'otp_codes' table doesn't exist yet")
        print("  You need to run schema.sql in Supabase SQL Editor")
    else:
        print(f"  FAIL: {e}")
        exit(1)

print()
print("=" * 70)
print("SUCCESS! Supabase is fully connected and ready.")
print("=" * 70)


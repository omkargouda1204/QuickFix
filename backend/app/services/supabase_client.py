"""
app/services/supabase_client.py
Lazy Supabase client — created on first use so tests with placeholder
keys don't crash at import time. Uses the service-role key (bypasses RLS).
"""
import os
import logging
from dotenv import load_dotenv

load_dotenv(os.path.join(os.path.dirname(__file__), "..", "..", ".env"))

logger = logging.getLogger(__name__)

_client = None  # lazily initialised


def get_client():
    """Return the shared Supabase client, creating it on first call."""
    global _client
    if _client is None:
        from supabase import create_client, Client  # local import keeps tests fast
        url: str = os.getenv("SUPABASE_URL", "")
        key: str = os.getenv("SUPABASE_SERVICE_ROLE_KEY", "")

        if not url or not key:
            raise RuntimeError(
                "SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY must be set in .env"
            )

        _client = create_client(url, key)
        logger.info("Supabase client initialised (url=%s)", url)

    return _client


# Convenience alias — modules can still do `from .supabase_client import supabase`
# but now it's a callable proxy, not an eager object.
class _SupabaseProxy:
    """
    Transparent proxy that forwards every attribute access to the
    lazily-created real client. This means existing code like
      supabase.table("users").select("*").execute()
    continues to work unchanged.
    """
    def __getattr__(self, name):
        return getattr(get_client(), name)


supabase = _SupabaseProxy()


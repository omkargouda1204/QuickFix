"""
app/models/db.py
Re-exports the single shared Supabase client as `db`
so model files can do:  from .db import db
"""
from ..services.supabase_client import supabase as db  # noqa: F401


-- Provider read access for count queries and home/admin stats.
ALTER TABLE providers ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_policies
    WHERE schemaname = 'public'
      AND tablename = 'providers'
      AND policyname = 'Allow read providers'
  ) THEN
    CREATE POLICY "Allow read providers"
    ON providers
    FOR SELECT
    USING (true);
  END IF;
END
$$;

-- Limit emergency requests to 2 per calendar month per user.
CREATE OR REPLACE FUNCTION can_create_emergency(uid text)
RETURNS boolean AS $$
DECLARE
    count_requests int;
BEGIN
    SELECT COUNT(*) INTO count_requests
    FROM emergency_requests
    WHERE user_id = uid
      AND created_at >= date_trunc('month', now());

    RETURN count_requests < 2;
END;
$$ LANGUAGE plpgsql;


-- admin_settings
ALTER TABLE admin_settings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "allow all"
ON admin_settings
FOR ALL
USING (true)
WITH CHECK (true);

-- services
ALTER TABLE services ENABLE ROW LEVEL SECURITY;

CREATE POLICY "allow all services"
ON services
FOR ALL
USING (true)
WITH CHECK (true);

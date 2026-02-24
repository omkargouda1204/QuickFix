-- ═══════════════════════════════════════════════════════════════════════════════
-- ADMIN PANEL DATABASE STRUCTURE FOR SUPABASE
-- ═══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. MAIN USERS TABLE (Already exists - extend with admin fields)
-- ─────────────────────────────────────────────────────────────────────────────
-- ALTER TABLE public.users ADD COLUMN IF NOT EXISTS role TEXT DEFAULT 'user';
-- ALTER TABLE public.users ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'active'; -- active, suspended, deactivated
-- ALTER TABLE public.users ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW();
-- ALTER TABLE public.users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW();
--
-- Roles: 'admin', 'provider', 'user'
-- Status: 'active', 'suspended', 'deactivated'

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. ADMIN ROLES TABLE - Define admin permission levels
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.admin_roles (
    id BIGSERIAL PRIMARY KEY,
    role_name TEXT UNIQUE NOT NULL,  -- 'super_admin', 'content_admin', 'approval_admin'
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Insert predefined admin roles
INSERT INTO public.admin_roles (role_name, description) VALUES
    ('super_admin', 'Full access to all admin features'),
    ('content_admin', 'Manage services, categories, featured items'),
    ('approval_admin', 'Review and approve provider applications'),
    ('user_admin', 'Manage users, suspend/delete accounts')
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. ADMIN PERMISSIONS TABLE - Link roles to specific permissions
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.admin_permissions (
    id BIGSERIAL PRIMARY KEY,
    admin_role_id BIGINT NOT NULL REFERENCES public.admin_roles(id) ON DELETE CASCADE,
    permission_code TEXT NOT NULL,  -- 'view_users', 'delete_user', 'approve_provider', etc.
    description TEXT,
    UNIQUE(admin_role_id, permission_code),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Insert predefined permissions
INSERT INTO public.admin_permissions (admin_role_id, permission_code, description)
SELECT ar.id, 'view_users', 'View all users' FROM public.admin_roles ar WHERE ar.role_name = 'super_admin'
ON CONFLICT DO NOTHING;

INSERT INTO public.admin_permissions (admin_role_id, permission_code, description)
SELECT ar.id, 'delete_user', 'Delete user accounts' FROM public.admin_roles ar WHERE ar.role_name IN ('super_admin', 'user_admin')
ON CONFLICT DO NOTHING;

INSERT INTO public.admin_permissions (admin_role_id, permission_code, description)
SELECT ar.id, 'suspend_user', 'Suspend or deactivate accounts' FROM public.admin_roles ar WHERE ar.role_name IN ('super_admin', 'user_admin')
ON CONFLICT DO NOTHING;

INSERT INTO public.admin_permissions (admin_role_id, permission_code, description)
SELECT ar.id, 'manage_services', 'Add, edit, delete services' FROM public.admin_roles ar WHERE ar.role_name IN ('super_admin', 'content_admin')
ON CONFLICT DO NOTHING;

INSERT INTO public.admin_permissions (admin_role_id, permission_code, description)
SELECT ar.id, 'manage_categories', 'Manage service categories' FROM public.admin_roles ar WHERE ar.role_name IN ('super_admin', 'content_admin')
ON CONFLICT DO NOTHING;

INSERT INTO public.admin_permissions (admin_role_id, permission_code, description)
SELECT ar.id, 'manage_featured', 'Manage featured services' FROM public.admin_roles ar WHERE ar.role_name IN ('super_admin', 'content_admin')
ON CONFLICT DO NOTHING;

INSERT INTO public.admin_permissions (admin_role_id, permission_code, description)
SELECT ar.id, 'approve_provider', 'Approve provider applications' FROM public.admin_roles ar WHERE ar.role_name IN ('super_admin', 'approval_admin')
ON CONFLICT DO NOTHING;

INSERT INTO public.admin_permissions (admin_role_id, permission_code, description)
SELECT ar.id, 'reject_provider', 'Reject provider applications' FROM public.admin_roles ar WHERE ar.role_name IN ('super_admin', 'approval_admin')
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. ADMIN USERS TABLE - Link Firebase users to admin roles
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.admin_users (
    id BIGSERIAL PRIMARY KEY,
    firebase_uid TEXT UNIQUE NOT NULL REFERENCES public.users(firebase_uid) ON DELETE CASCADE,
    admin_role_id BIGINT NOT NULL REFERENCES public.admin_roles(id),
    assigned_by_uid TEXT REFERENCES public.users(firebase_uid),  -- Who assigned this admin role
    assigned_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. PENDING PROVIDER APPLICATIONS TABLE
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.pending_approvals (
    id BIGSERIAL PRIMARY KEY,
    user_firebase_uid TEXT NOT NULL REFERENCES public.users(firebase_uid) ON DELETE CASCADE,
    business_name TEXT NOT NULL,
    business_description TEXT,
    service_type TEXT,  -- plumbing, electrical, etc.
    contact_phone TEXT,
    contact_email TEXT,
    status TEXT DEFAULT 'pending',  -- 'pending', 'approved', 'rejected'
    application_date TIMESTAMP DEFAULT NOW(),
    reviewed_by_uid TEXT REFERENCES public.users(firebase_uid),
    reviewed_at TIMESTAMP,
    rejection_reason TEXT,
    UNIQUE(user_firebase_uid)  -- One pending application per user
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. SERVICE CATEGORIES TABLE
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.service_categories (
    id BIGSERIAL PRIMARY KEY,
    category_name TEXT UNIQUE NOT NULL,  -- Plumbing, Electrical, Cleaning, etc.
    description TEXT,
    icon_path TEXT,  -- Reference to drawable
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Insert predefined categories
INSERT INTO public.service_categories (category_name, description, icon_path) VALUES
    ('Plumbing', 'Plumbing services and repairs', 'ic_plumbing'),
    ('Electrical', 'Electrical installations and repairs', 'ic_electrical'),
    ('Cleaning', 'House and office cleaning', 'ic_cleaning'),
    ('Repairs', 'General repairs and maintenance', 'ic_repair'),
    ('AC', 'Air conditioning services', 'ic_ac'),
    ('Painting', 'Painting and decoration', 'ic_painting'),
    ('Carpentry', 'Carpentry and woodwork', 'ic_carpentry')
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. SERVICES TABLE (Main service catalog)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.services (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES public.service_categories(id),
    service_name TEXT NOT NULL,
    description TEXT,
    base_price DECIMAL(10, 2),
    is_featured BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_by_uid TEXT REFERENCES public.users(firebase_uid),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. FEATURED SERVICES TABLE
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.featured_services (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT NOT NULL REFERENCES public.services(id) ON DELETE CASCADE,
    feature_order INT,  -- Display order (1, 2, 3...)
    is_active BOOLEAN DEFAULT TRUE,
    featured_from TIMESTAMP DEFAULT NOW(),
    featured_until TIMESTAMP,  -- Null = indefinite
    created_at TIMESTAMP DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. ADMIN ACTIVITY LOG TABLE - Audit trail for all admin actions
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.admin_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_uid TEXT NOT NULL REFERENCES public.users(firebase_uid),
    action_type TEXT NOT NULL,  -- 'user_delete', 'provider_approve', 'service_add', etc.
    target_type TEXT,  -- 'user', 'provider', 'service', 'category'
    target_id TEXT,  -- ID of the affected resource
    details JSONB,  -- Extra details (old values, new values, etc.)
    action_timestamp TIMESTAMP DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. ROW LEVEL SECURITY (RLS) POLICIES
-- ─────────────────────────────────────────────────────────────────────────────

-- Enable RLS on sensitive tables
ALTER TABLE public.admin_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_logs ENABLE ROW LEVEL SECURITY;

-- Policy: Only super admins can view admin users
CREATE POLICY "admin_users_select_for_admins" ON public.admin_users
    FOR SELECT
    USING (auth.uid() IN (
        SELECT au.firebase_uid FROM public.admin_users au
        JOIN public.admin_roles ar ON au.admin_role_id = ar.id
        WHERE ar.role_name = 'super_admin'
    ));

-- Policy: Only admins can insert/update admin logs (app-level, not DB-enforced)
-- This is better handled in Java to avoid RLS issues

-- ─────────────────────────────────────────────────────────────────────────────
-- 11. VIEW FOR ADMIN USER DETAILS (easier queries from app)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW admin_user_details AS
SELECT
    u.firebase_uid,
    u.name,
    u.email,
    u.phone,
    au.admin_role_id,
    ar.role_name,
    au.assigned_at,
    u.created_at,
    u.status
FROM public.users u
JOIN public.admin_users au ON u.firebase_uid = au.firebase_uid
JOIN public.admin_roles ar ON au.admin_role_id = ar.id;

-- ─────────────────────────────────────────────────────────────────────────────
-- INDEXES FOR PERFORMANCE
-- ─────────────────────────────────────────────────────────────────────────────
CREATE INDEX idx_users_role ON public.users(role);
CREATE INDEX idx_users_status ON public.users(status);
CREATE INDEX idx_admin_users_firebase_uid ON public.admin_users(firebase_uid);
CREATE INDEX idx_pending_approvals_status ON public.pending_approvals(status);
CREATE INDEX idx_pending_approvals_user ON public.pending_approvals(user_firebase_uid);
CREATE INDEX idx_admin_logs_admin_uid ON public.admin_logs(admin_uid);
CREATE INDEX idx_admin_logs_timestamp ON public.admin_logs(action_timestamp);
CREATE INDEX idx_services_featured ON public.services(is_featured);
CREATE INDEX idx_services_active ON public.services(is_active);


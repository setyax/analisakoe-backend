-- Modul CRUD Employee Request (HR & GA).
-- Header + detail item/UOM tanpa harga. Semua data difilter tenant_id + company_id.

CREATE TABLE IF NOT EXISTS hr.employee_request_hdr (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL DEFAULT 1,
    company_id  BIGINT NOT NULL DEFAULT 1,
    doc_no      VARCHAR(30) NOT NULL,
    doc_date    DATE NOT NULL,
    employee_id BIGINT NOT NULL REFERENCES hr.employee(id),
    reason      TEXT,
    status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    CONSTRAINT hr_employee_request_hdr_doc_key UNIQUE (tenant_id, company_id, doc_no),
    CONSTRAINT hr_employee_request_hdr_status_check
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS hr.employee_request_dtl (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    header_id     BIGINT NOT NULL REFERENCES hr.employee_request_hdr(id) ON DELETE CASCADE,
    line_no       INTEGER NOT NULL,
    item_id       BIGINT NOT NULL REFERENCES md.item(id),
    uom_id        BIGINT NOT NULL REFERENCES md.uom(id),
    qty           NUMERIC(18,4) NOT NULL,
    short_text    TEXT,
    delivery_date DATE,
    CONSTRAINT hr_employee_request_dtl_header_line_key UNIQUE (header_id, line_no)
);

CREATE INDEX IF NOT EXISTS idx_employee_request_hdr_ctx
    ON hr.employee_request_hdr (tenant_id, company_id, id DESC);

INSERT INTO core.permission (code, module, description) VALUES
    ('hr.employee_request.read',  'hr', 'Lihat Employee Request'),
    ('hr.employee_request.write', 'hr', 'Kelola Employee Request')
ON CONFLICT (code) DO NOTHING;

-- ADMIN, MANAGER, STAFF: read + write. VIEWER: read saja.
INSERT INTO core.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM core.role r
JOIN core.permission p ON p.code IN ('hr.employee_request.read', 'hr.employee_request.write')
WHERE r.code IN ('ADMIN', 'MANAGER', 'STAFF')
ON CONFLICT DO NOTHING;

INSERT INTO core.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM core.role r
JOIN core.permission p ON p.code = 'hr.employee_request.read'
WHERE r.code = 'VIEWER'
ON CONFLICT DO NOTHING;

INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission)
SELECT p.id, 'hr.employee_requests', 'Employee Requests', '/ui/hr/employee-requests',
       'clipboard-check', 72, FALSE, 'hr.employee_request.read'
FROM core.menu p WHERE p.code = 'hrga'
ON CONFLICT (code) DO NOTHING;

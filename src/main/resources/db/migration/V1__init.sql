CREATE TABLE scan (
    id             UUID PRIMARY KEY,
    start_url      TEXT NOT NULL,
    status         TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | RUNNING | DONE | FAILED
    max_pages      INT  NOT NULL DEFAULT 30,
    org_name       TEXT,
    site_name      TEXT,
    contact_email  TEXT,
    engine_name    TEXT,
    engine_version TEXT,
    pages_scanned  INT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,
    error          TEXT
);

CREATE TABLE scan_page (
    id          UUID PRIMARY KEY,
    scan_id     UUID NOT NULL REFERENCES scan(id) ON DELETE CASCADE,
    url         TEXT NOT NULL,
    http_status INT,
    page_error  TEXT
);

CREATE TABLE violation (
    id        UUID PRIMARY KEY,
    page_id   UUID NOT NULL REFERENCES scan_page(id) ON DELETE CASCADE,
    rule_id   TEXT NOT NULL,
    impact    TEXT NOT NULL,            -- minor | moderate | serious | critical
    wcag_sc   TEXT,                     -- comma-separated success criteria, e.g. "1.4.3"
    help_url  TEXT,
    selector  TEXT,
    html      TEXT
);

CREATE INDEX idx_scan_page_scan ON scan_page (scan_id);
CREATE INDEX idx_violation_page ON violation (page_id);
CREATE INDEX idx_violation_rule ON violation (rule_id);

PRAGMA foreign_keys = ON;

-- ============================================================
-- CRMS 2.0 - Phase 1 database foundation
-- Compatible with the current Java + SQLite application.
-- Existing tables are retained so current DAOs continue to work.
-- New tables provide the richer investigation data model.
-- ============================================================

-- -------------------------
-- Existing / compatibility tables
-- -------------------------
CREATE TABLE IF NOT EXISTS officers (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    contact TEXT NOT NULL,
    badge_number TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    officer_id TEXT UNIQUE,
    role TEXT NOT NULL CHECK(role IN ('ADMIN','COMMAND_OFFICER','FIELD_AGENT')),
    FOREIGN KEY(officer_id) REFERENCES officers(id)
);

CREATE TABLE IF NOT EXISTS cases (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    severity TEXT NOT NULL,
    status TEXT NOT NULL,
    opening_date TEXT NOT NULL,
    assigned_officer_id TEXT,
    priority INTEGER NOT NULL,
    FOREIGN KEY(assigned_officer_id) REFERENCES officers(id)
);

-- Kept for backward compatibility with the current UI/DAO layer.
CREATE TABLE IF NOT EXISTS suspects (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    contact TEXT NOT NULL,
    physical_description TEXT NOT NULL,
    risk_level TEXT NOT NULL,
    repeat_offender INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS case_suspects (
    case_id TEXT NOT NULL,
    suspect_id TEXT NOT NULL,
    PRIMARY KEY(case_id,suspect_id),
    FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY(suspect_id) REFERENCES suspects(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS evidence (
    id TEXT PRIMARY KEY,
    case_id TEXT NOT NULL,
    description TEXT NOT NULL,
    collection_date TEXT NOT NULL,
    current_custodian TEXT NOT NULL,
    file_path TEXT,
    file_hash_sha256 TEXT,
    hash_algorithm TEXT DEFAULT 'SHA-256',
    hash_verified_at TEXT,
    file_size_bytes INTEGER,
    FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS evidence_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    evidence_id TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    action_type TEXT NOT NULL,
    from_custodian TEXT NOT NULL,
    to_custodian TEXT NOT NULL,
    remarks TEXT NOT NULL,
    FOREIGN KEY(evidence_id) REFERENCES evidence(id) ON DELETE CASCADE
);

-- -------------------------
-- New CRMS 2.0 entities
-- -------------------------

CREATE TABLE IF NOT EXISTS persons (
    id TEXT PRIMARY KEY,
    first_name TEXT NOT NULL,
    last_name TEXT,
    date_of_birth TEXT,
    phone TEXT,
    email TEXT,
    address TEXT,
    physical_description TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS case_persons (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_id TEXT NOT NULL,
    person_id TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('SUSPECT','VICTIM','WITNESS','COMPLAINANT','PERSON_OF_INTEREST','OTHER')),
    notes TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(case_id, person_id, role),
    FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY(person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS locations (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    address TEXT,
    city TEXT,
    state TEXT,
    country TEXT,
    latitude REAL,
    longitude REAL,
    location_type TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS case_locations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_id TEXT NOT NULL,
    location_id TEXT NOT NULL,
    relationship_type TEXT NOT NULL CHECK(relationship_type IN ('INCIDENT_LOCATION','EVIDENCE_LOCATION','WITNESS_LOCATION','ARREST_LOCATION','OTHER')),
    notes TEXT,
    UNIQUE(case_id, location_id, relationship_type),
    FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS vehicles (
    id TEXT PRIMARY KEY,
    registration_number TEXT NOT NULL UNIQUE,
    make TEXT,
    model TEXT,
    vehicle_type TEXT,
    color TEXT,
    owner_person_id TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(owner_person_id) REFERENCES persons(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS case_vehicles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_id TEXT NOT NULL,
    vehicle_id TEXT NOT NULL,
    relationship_type TEXT NOT NULL CHECK(relationship_type IN ('INVOLVED','SEEN_AT_SCENE','OWNERSHIP_LINK','EVIDENCE_LINK','OTHER')),
    notes TEXT,
    UNIQUE(case_id, vehicle_id, relationship_type),
    FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY(vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS case_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    event_time TEXT NOT NULL,
    location_id TEXT,
    person_id TEXT,
    evidence_id TEXT,
    source TEXT,
    verification_status TEXT NOT NULL DEFAULT 'UNVERIFIED'
        CHECK(verification_status IN ('UNVERIFIED','PENDING_REVIEW','VERIFIED','CONTRADICTED')),
    truth_level TEXT NOT NULL DEFAULT 'REPORTED'
        CHECK(truth_level IN ('FACT','REPORTED','INFERRED','UNVERIFIED')),
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE SET NULL,
    FOREIGN KEY(person_id) REFERENCES persons(id) ON DELETE SET NULL,
    FOREIGN KEY(evidence_id) REFERENCES evidence(id) ON DELETE SET NULL,
    FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS investigation_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_id TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    assigned_to TEXT,
    priority TEXT NOT NULL DEFAULT 'MEDIUM'
        CHECK(priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK(status IN ('PENDING','IN_PROGRESS','BLOCKED','COMPLETED','CANCELLED')),
    due_date TEXT,
    completed_at TEXT,
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY(assigned_to) REFERENCES officers(id) ON DELETE SET NULL,
    FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS investigation_findings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_id TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    type TEXT NOT NULL CHECK(type IN ('FACT','OBSERVATION','LEAD','HYPOTHESIS','INFERENCE')),
    status TEXT NOT NULL DEFAULT 'OPEN'
        CHECK(status IN ('OPEN','UNDER_REVIEW','SUPPORTED','CONTRADICTED','RESOLVED')),
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS finding_evidence (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    finding_id INTEGER NOT NULL,
    evidence_id TEXT NOT NULL,
    relationship TEXT NOT NULL CHECK(relationship IN ('SUPPORTS','CONTRADICTS','CONTEXT')),
    notes TEXT,
    UNIQUE(finding_id, evidence_id, relationship),
    FOREIGN KEY(finding_id) REFERENCES investigation_findings(id) ON DELETE CASCADE,
    FOREIGN KEY(evidence_id) REFERENCES evidence(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS investigation_hypotheses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_id TEXT NOT NULL,
    hypothesis_code TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK(status IN ('ACTIVE','UNDER_REVIEW','RETAINED','REJECTED_BY_INVESTIGATOR','INACTIVE')),
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(case_id, hypothesis_code),
    FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS hypothesis_evidence (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    hypothesis_id INTEGER NOT NULL,
    evidence_id TEXT NOT NULL,
    relationship_type TEXT NOT NULL
        CHECK(relationship_type IN ('SUPPORTS','CONTRADICTS','CONTEXT','UNASSESSED')),
    notes TEXT,
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(hypothesis_id, evidence_id),
    FOREIGN KEY(hypothesis_id) REFERENCES investigation_hypotheses(id) ON DELETE CASCADE,
    FOREIGN KEY(evidence_id) REFERENCES evidence(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS entity_relationships (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_type TEXT NOT NULL CHECK(source_type IN ('PERSON','CASE','EVIDENCE','LOCATION','VEHICLE')),
    source_id TEXT NOT NULL,
    target_type TEXT NOT NULL CHECK(target_type IN ('PERSON','CASE','EVIDENCE','LOCATION','VEHICLE')),
    target_id TEXT NOT NULL,
    relationship_type TEXT NOT NULL,
    confidence REAL CHECK(confidence IS NULL OR (confidence >= 0.0 AND confidence <= 1.0)),
    status TEXT NOT NULL DEFAULT 'PENDING_REVIEW'
        CHECK(status IN ('PENDING_REVIEW','CONFIRMED','DISMISSED')),
    reason TEXT,
    created_by TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS case_reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_id TEXT NOT NULL,
    report_type TEXT NOT NULL CHECK(report_type IN ('INCIDENT_REPORT','INVESTIGATION_REPORT','FORENSIC_REPORT','SUPERVISOR_REPORT','FINAL_REPORT')),
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'DRAFT'
        CHECK(status IN ('DRAFT','UNDER_REVIEW','APPROVED','ARCHIVED')),
    created_by TEXT,
    approved_by TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE,
    FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY(approved_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    timestamp TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details TEXT,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- -------------------------
-- Helpful indexes
-- -------------------------
CREATE INDEX IF NOT EXISTS idx_cases_status ON cases(status);
CREATE INDEX IF NOT EXISTS idx_cases_priority ON cases(priority DESC);
CREATE INDEX IF NOT EXISTS idx_cases_assigned_officer ON cases(assigned_officer_id);
CREATE INDEX IF NOT EXISTS idx_case_persons_case ON case_persons(case_id);
CREATE INDEX IF NOT EXISTS idx_case_persons_person ON case_persons(person_id);
CREATE INDEX IF NOT EXISTS idx_case_locations_case ON case_locations(case_id);
CREATE INDEX IF NOT EXISTS idx_case_locations_location ON case_locations(location_id);
CREATE INDEX IF NOT EXISTS idx_case_vehicles_case ON case_vehicles(case_id);
CREATE INDEX IF NOT EXISTS idx_case_vehicles_vehicle ON case_vehicles(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_case_events_case_time ON case_events(case_id, event_time);
CREATE INDEX IF NOT EXISTS idx_case_events_person ON case_events(person_id);
CREATE INDEX IF NOT EXISTS idx_case_events_evidence ON case_events(evidence_id);
CREATE INDEX IF NOT EXISTS idx_tasks_case_status ON investigation_tasks(case_id, status);
CREATE INDEX IF NOT EXISTS idx_findings_case_status ON investigation_findings(case_id, status);
CREATE INDEX IF NOT EXISTS idx_relationships_source ON entity_relationships(source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_relationships_target ON entity_relationships(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_user_time ON audit_logs(user_id, timestamp);

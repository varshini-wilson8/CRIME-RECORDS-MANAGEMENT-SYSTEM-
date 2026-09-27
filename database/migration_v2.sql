PRAGMA foreign_keys = ON;

-- ============================================================
-- CRMS 2.0 - Migration from the current schema
-- Safe to run repeatedly for the current development dataset.
-- Existing suspects become PERSON records with role SUSPECT.
-- ============================================================

BEGIN TRANSACTION;

-- 1. Convert current suspect records into generic people.
--    IDs are preserved so the migration is traceable.
INSERT OR IGNORE INTO persons
    (id, first_name, last_name, phone, physical_description)
SELECT
    s.id,
    CASE
        WHEN instr(trim(s.name), ' ') > 0 THEN substr(trim(s.name), 1, instr(trim(s.name), ' ') - 1)
        ELSE trim(s.name)
    END AS first_name,
    CASE
        WHEN instr(trim(s.name), ' ') > 0 THEN ltrim(substr(trim(s.name), instr(trim(s.name), ' ') + 1))
        ELSE NULL
    END AS last_name,
    s.contact,
    s.physical_description
FROM suspects s;

-- 2. Preserve every existing case-suspect link in the new model.
INSERT OR IGNORE INTO case_persons (case_id, person_id, role, notes)
SELECT
    cs.case_id,
    cs.suspect_id,
    'SUSPECT',
    CASE WHEN s.repeat_offender = 1 THEN 'Migrated from legacy suspect record; repeat-offender flag was set.' ELSE 'Migrated from legacy suspect record.' END
FROM case_suspects cs
JOIN suspects s ON s.id = cs.suspect_id
WHERE cs.case_id <> 'C-2026-044';

-- 3. Seed the current evidence record as a timeline event.
INSERT INTO case_events
    (case_id, event_type, title, description, event_time, evidence_id, source, verification_status)
SELECT
    e.case_id,
    'EVIDENCE_COLLECTION',
    'Evidence collected: ' || e.id,
    e.description,
    e.collection_date || 'T00:00:00',
    e.id,
    'Legacy evidence record',
    'VERIFIED'
FROM evidence e
WHERE e.case_id <> 'C-2026-044' AND NOT EXISTS (
    SELECT 1 FROM case_events ce
    WHERE ce.case_id = e.case_id AND ce.evidence_id = e.id AND ce.event_type = 'EVIDENCE_COLLECTION'
);

-- 4. Convert the existing custody trail into timeline events.
INSERT INTO case_events
    (case_id, event_type, title, description, event_time, evidence_id, source, verification_status)
SELECT
    e.case_id,
    'CUSTODY_TRANSFER',
    'Evidence custody: ' || l.action_type,
    l.remarks || ' | ' || l.from_custodian || ' -> ' || l.to_custodian,
    l.timestamp,
    l.evidence_id,
    'Legacy evidence custody log',
    'VERIFIED'
FROM evidence_logs l
JOIN evidence e ON e.id = l.evidence_id
WHERE e.case_id <> 'C-2026-044' AND NOT EXISTS (
    SELECT 1 FROM case_events ce
    WHERE ce.evidence_id = l.evidence_id
      AND ce.event_time = l.timestamp
      AND ce.event_type = 'CUSTODY_TRANSFER'
);

-- 5. Create initial investigation tasks for cases that have no tasks yet.
INSERT INTO investigation_tasks (case_id, title, description, assigned_to, priority, status)
SELECT
    c.id,
    'Complete initial case review',
    'Review case details, linked people, evidence and outstanding leads.',
    c.assigned_officer_id,
    CASE c.severity
        WHEN 'CRITICAL' THEN 'CRITICAL'
        WHEN 'HIGH' THEN 'HIGH'
        WHEN 'MEDIUM' THEN 'MEDIUM'
        ELSE 'LOW'
    END,
    CASE WHEN c.status IN ('CLOSED','COLD') THEN 'COMPLETED' ELSE 'PENDING' END
FROM cases c
WHERE NOT EXISTS (
    SELECT 1 FROM investigation_tasks t WHERE t.case_id = c.id
);

-- 6. Record the migration itself in the audit trail.
INSERT INTO audit_logs (action, entity_type, entity_id, details)
SELECT 'MIGRATION', 'SYSTEM', 'CRMS-V2', 'Migrated legacy suspects, case links, evidence custody events and initial investigation tasks into the CRMS 2.0 foundation.'
WHERE NOT EXISTS (SELECT 1 FROM audit_logs WHERE action='MIGRATION' AND entity_type='SYSTEM' AND entity_id='CRMS-V2');

COMMIT;

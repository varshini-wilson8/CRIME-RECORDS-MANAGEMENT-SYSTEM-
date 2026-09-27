# CRMS Phase 1J — Audit & Investigation Activity Intelligence

## Purpose
Phase 1J adds a chronological accountability trail to the investigation workspace. Important actions are recorded with timestamp, actor, action, entity, and details so investigators and supervisors can trace how a case evolved.

## Added backend
- `AuditLogDao.java` — writes and reads persistent audit records.
- `AuditLogApiHandler.java` — read-only case activity endpoint at `/api/case-audit?caseId=...`.
- Audit route is protected by the existing role/session security wrapper.

## Actions currently audited
- Case created / updated
- Evidence added
- Evidence custody transferred
- Investigation task created
- Investigation task status changed
- Investigation finding created
- Finding status changed
- Finding-to-evidence relationship linked
- Investigation report generated
- Report status changed, including approval

Each record includes a `CASE_ID=...` reference so the case activity feed can retrieve it.

## UI
The Investigation Workspace now contains **Audit & investigation activity** with:
- total audit events
- distinct actors
- latest activity
- chronological activity cards
- entity/action badges
- action details
- refresh control

## Security/design note
The activity feed is read-only from the browser. It does not allow users to rewrite or delete audit records. Audit records describe recorded system actions; they are not investigative conclusions.

## Validation
The Java source was compiled successfully against the bundled SQLite JDBC driver. A DAO smoke test successfully inserted and retrieved an audit event for case `C-2026-041`.

The distributable ZIP intentionally excludes `ccecs.db` so local test/audit records are not shipped as part of the source package.

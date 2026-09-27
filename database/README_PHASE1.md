# CRMS 2.0 — Phase 1 Database Foundation

## What changed

The existing schema is preserved for backward compatibility, while new normalized investigation tables are added:

- `persons`
- `case_persons`
- `locations`
- `case_locations`
- `vehicles`
- `case_vehicles`
- `case_events`
- `investigation_tasks`
- `investigation_findings`
- `finding_evidence`
- `entity_relationships`
- `case_reports`
- `audit_logs`

Useful indexes are also created.

## Migration

1. Back up `ccecs.db`.
2. Apply `schema_v2.sql` to create the new tables.
3. Apply `migration_v2.sql` once.
4. Keep the legacy `suspects` / `case_suspects` tables for now because the current Java DAO layer still uses them.
5. After the new `PersonDao` and `CasePersonDao` are implemented and the UI is switched over, the legacy suspect tables can be retired in a later migration.

## Important

`schema_v2.sql` is intentionally additive. It does not drop or rename existing tables, so the current application can continue running while the new architecture is implemented incrementally.

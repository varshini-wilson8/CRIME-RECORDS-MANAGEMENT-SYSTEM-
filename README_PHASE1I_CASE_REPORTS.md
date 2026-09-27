# CRMS Phase 1I — Investigation Reports & Case Package

Adds a structured case-report workflow to the Investigation Workspace.

## Features
- Generate a report from recorded case data.
- Includes case overview, people, timeline, evidence, findings and investigation tasks.
- Completeness validation for core case records.
- Report lifecycle: Draft, Under Review, Approved, Archived.
- Print-friendly report view.
- Existing authentication/role protection is retained.
- Reports are stored in the existing `case_reports` table.

## Design note
The report summarizes recorded information. It does not automatically determine guilt, innocence, or other legal conclusions.

## Run
Compile with the existing SQLite JDBC driver and start `SecureWebServer` as before. Open an investigation page using `?caseId=C-1001` and use **Investigation report & case package**.

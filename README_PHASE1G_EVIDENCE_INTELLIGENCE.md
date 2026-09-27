# CRMS Phase 1G — Evidence Intelligence & Finding Matrix

This phase adds a reviewable evidence-to-finding workflow to the Investigation Workspace.

## Features
- Investigation findings with types: FACT, OBSERVATION, LEAD, HYPOTHESIS, INFERENCE.
- Finding status lifecycle: OPEN, UNDER_REVIEW, SUPPORTED, CONTRADICTED, RESOLVED.
- Evidence linked to findings using SUPPORTS, CONTRADICTS, or CONTEXT.
- Evidence summary includes current custodian and custody-log count.
- Add findings and link evidence directly from the Investigation Workspace.
- Existing evidence custody data is preserved.

## API
- `GET /api/investigation-findings?caseId=C-2026-041`
- `POST /api/investigation-findings?caseId=C-2026-041` — create finding.
- `POST /api/investigation-findings?caseId=C-2026-041` with `action=link` — link evidence.
- `PATCH /api/investigation-findings?caseId=C-2026-041` — update finding status.

## Design principle
The matrix records an investigator's explicit relationship between evidence and a finding. It does not automatically determine guilt, identity, or truth. Findings remain reviewable and status can be changed as the investigation develops.

## Verification
The Java source compiles against the bundled SQLite JDBC driver. The server starts on port 8081, login succeeds with the development account, and the findings API returns seeded findings and evidence links for the Riverside Gallery development case.

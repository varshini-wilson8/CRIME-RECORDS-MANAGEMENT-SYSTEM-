# CRMS Phase 1H – Explainable Case Priority & Investigation Dashboard

This phase adds an operational, explainable case-priority panel to the Investigation Workspace.

## Features
- Priority score from 0–100 with visible contributors.
- Contributors: severity, case age, overdue open tasks, and investigation gaps.
- Investigation progress based on completed versus total tasks.
- Operational counters for open tasks, overdue tasks, evidence, open findings, timeline events, and linked people.
- Explicitly presented as a workflow/operations aid, not a prediction of criminal behavior.

## Endpoint
`GET /api/case-priority?caseId=...`

## Compatibility
- SQLite remains the source of truth.
- Existing case, people, graph, task, and evidence/finding features are preserved.
- No legacy suspect tables are removed.

# CRMS Phase 1K — Advanced Case Intelligence & Pattern Detection

Phase 1K adds an explainable, review-first cross-case intelligence layer to the investigation workspace.

## Signals
- Shared people across recorded cases
- Shared locations across recorded cases
- Shared vehicles across recorded cases
- Same recorded crime type in other cases
- Timeline events within 48 hours across different cases

## Design principle
These are descriptive signals only. The system does not infer guilt, criminal propensity, or an automatic case connection. Investigators must review source records and decide whether a relationship should be recorded.

## API
`GET /api/case-intelligence?caseId=<case id>`

The endpoint is protected by the same role wrapper as the other investigation APIs.

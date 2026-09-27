# CRMS Phase 1D – People Intelligence

This phase extends the Investigation Workspace with a generic People Intelligence layer.

## What was added
- `PersonRecord` model for a person independent of the legacy `Suspect` model.
- `PersonDao` to retrieve people linked to a case and calculate cross-case context.
- `PersonApiHandler` at `GET /api/case-people?caseId=...`.
- Investigation Workspace People Intelligence cards.
- Displays investigation role, linked-case count, related-event count, linked-vehicle count, contact details, notes, and case connections.
- Development data links the fictional Jordan Taylor record to two cases, two timeline events, and one vehicle so the feature is visible immediately.

## Design principle
A person's case role is shown as recorded in the case (for example SUSPECT, VICTIM, WITNESS, or PERSON_OF_INTEREST). Cross-case links are presented as context for investigator review; the feature does not infer guilt or make an automated determination.

## Run
From this directory:

```text
javac -d build -cp "lib/sqlite-jdbc.jar" $(find . -maxdepth 1 -name "*.java" ! -name "WebServer.java") $(find model repository service -name "*.java")
java -cp "build:lib/sqlite-jdbc.jar" SecureWebServer
```

Open `http://localhost:8081/login`.

## Test case
Open the Investigation Workspace for case `C-2026-041`. The People Intelligence section should show Jordan Taylor with:
- 2 linked cases
- 2 related timeline events
- 1 linked vehicle

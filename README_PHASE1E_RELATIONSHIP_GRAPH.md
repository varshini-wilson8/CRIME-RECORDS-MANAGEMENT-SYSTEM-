# CRMS Phase 1E — Case Relationship Graph

## What was added
- New endpoint: `/api/case-graph?caseId=<CASE_ID>`
- Interactive SVG relationship graph in the Investigation Workspace.
- Graph node types: CASE, PERSON, EVIDENCE, LOCATION, VEHICLE, EVENT.
- Direct case relationships are derived from the normalized CRMS 2.0 tables.
- People connected to multiple cases are shown as cross-case connections.
- Vehicle ownership links are shown when an owner is available.
- Timeline events connect to referenced people, evidence, and locations.
- Explicit `entity_relationships` records are included when they touch the selected case graph.
- Hovering a node shows its type, ID, and available detail.

## Investigation-safety design
The graph is a visualization of recorded or explicitly entered relationships. It does not infer guilt, identity, or criminal responsibility. Any relationship stored with a review status remains visibly contextual and should be assessed by an authorized investigator.

## Run
1. Open the project folder.
2. Compile with the SQLite JDBC jar on the classpath.
3. Run `SecureWebServer`.
4. Sign in and open a case from the dashboard.
5. Click **Investigate**.
6. Scroll to **Case relationship graph**.

## Example
For the seeded Riverside Gallery case, the graph includes the selected case, a linked person, a related case, evidence, a vehicle, and investigation timeline events.

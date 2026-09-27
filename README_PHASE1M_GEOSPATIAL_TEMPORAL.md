# CRMS Phase 1M — Geospatial & Temporal Intelligence

## 1. Overview
Phase 1M implements geospatial mapping and temporal pattern intelligence according to Section 17 of the CRMS Master Development Sequence & Implementation Specification.

It equips investigators with:
- **Geocoded Incident Mapping**: Validated latitude and longitude coordinates for incident scenes, evidence sites, and recovery locations.
- **Interactive Map Canvas (`/map`)**: Interactive precinct map rendering georeferenced incident clusters color-coded by severity (CRITICAL=Red, HIGH=Orange, MEDIUM=Yellow, LOW=Blue).
- **Incident Inspector**: Selecting any location pin opens an inspector card displaying location metadata, coordinates, and associated cases with a direct one-click link to the Investigation Workspace.
- **24-Hour Temporal Activity Patterns**: Hourly histogram analyzing crime event occurrences across the 24-hour cycle to surface operational crime peaks.
- **Day-of-Week Distribution**: Activity breakdown across Monday through Sunday.
- **Recurring Location Intelligence**: Surfaces locations associated with multiple distinct investigations (e.g. Victoria Warehouse Complex appearing in cases `C-2026-041` and `C-2026-043`).
- **Spatiotemporal Proximity Signals**: Highlights events occurring within 48 hours at adjacent locations.
- **Investigative Safeguard**: Prominently warns investigators that geographic and temporal clustering are descriptive observations for review and do not infer causation, offender identity, or guilt.

---

## 2. Changes Implemented

### Database & Seed Data
- Geocoded locations seeded with validated coordinates in Chennai precinct:
  - `LOC-001`: Riverside Gallery (`13.0827, 80.2707`, Commercial)
  - `LOC-002`: Central Station Terminal (`13.0800, 80.2750`, Public Space)
  - `LOC-003`: North Overpass Junction (`13.0890, 80.2800`, Vehicle Stop)
  - `LOC-004`: Victoria Warehouse Complex (`13.0845, 80.2730`, Commercial)
- Linked location IDs to `case_locations` and `case_events`.

### Backend Layer
- `GeospatialDao.java`:
  - `getIncidentLocations()`: returns geocoded incident locations with case links and severity aggregation.
  - `getHourlyEventDistribution()`: 24-hour event histogram.
  - `getDayOfWeekDistribution()`: day-of-week pattern counts.
  - `getRecurringLocations()`: finds locations appearing in multiple cases.
  - `getSpatiotemporalSignals()`: computes cross-case events within 48 hours at adjacent scenes.
- `GeospatialApiHandler.java`: protected endpoint at `/api/geospatial` returning structured JSON and recording `VIEW_GEOSPATIAL` in `audit_logs`.
- `SecureWebServer.java`: registered `/map` and `/api/geospatial` routes with role-based access control.

### Frontend Layer
- `web/map.html`: Dedicated Crime Map & Temporal Analytics interface.
- Updated navigation in `web/index.html`, `web/command-center.html`, and `web/investigation.html` to include the Crime Map.

---

## 3. How to Compile and Run

### Compilation (Java 11)
```powershell
javac -cp "lib/sqlite-jdbc.jar;." -d out *.java model/*.java service/*.java repository/*.java util/*.java menu/*.java
```

### Run Server
```powershell
java -cp "out;lib/sqlite-jdbc.jar" SecureWebServer
```
The server starts at: `http://localhost:8081/login`

### Verification Test Suite
```powershell
javac -d out ApiTester.java
java -cp out ApiTester
```

### Accessing the Interface
1. Open browser: `http://localhost:8081/login`
2. Credentials:
   - Admin: `admin` / `ChangeMe!2026`
   - Command Officer: `commander` / `ChangeMe!2026`
   - Field Agent: `fieldagent` / `ChangeMe!2026`
3. Click **Crime map** in navigation or visit `http://localhost:8081/map`.

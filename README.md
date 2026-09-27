# CRMS 2.0 — Release Candidate: All 14 Defined Automated Acceptance Audit Phases Passed

**The CRMS 2.0 Release Candidate has completed its defined automated acceptance audit, covering database integrity, authentication, RBAC, epistemological safeguards, ACH grounding, evidence integrity, investigation readiness, deterministic package generation, audit protection, case isolation, and UI/API regression. All 14 defined audit phases passed.**

---

## 🌟 Core System Capabilities

1. **Epistemological Timeline & Neutral Gap Detection**:
   - Every event tagged with dual epistemic status: `truth_level` (`FACT` vs `REPORTED`) and `verification_status` (`VERIFIED`, `UNVERIFIED`, `CONTRADICTED`, `PENDING_REVIEW`).
   - Algorithmic neutral temporal gap detection (e.g. identifies the 18:47 → 18:56 9-minute unexplained window during the critical incident timeframe on Case `C-2026-041`).
   - Epistemic Safeguard: Explicitly denotes that unexplained gaps do not prove deliberate tampering or criminal action.

2. **Analysis of Competing Hypotheses (ACH)**:
   - Full implementation of Richards J. Heuer Jr.'s ACH framework.
   - Grounded hypotheses ($H_1$, $H_2$, $H_3$) linked against evidence items with descriptive contradiction counters (`Inconsistent`, `Very Inconsistent`, `Consistent`).
   - Epistemic Safeguard: Non-guilt-determining analytic technique; evaluation remains strictly within the purview of the human investigation officer.

3. **Digital Evidence Integrity & Verification**:
   - SHA-256 cryptographic verification using Java `MessageDigest`.
   - File attachment fixture support with server filesystem path isolation (browser only sees file basenames, never raw OS paths).
   - Tamper detection: Differentiates `VERIFIED_MATCH` from `INTEGRITY_MISMATCH`. On mismatch, original stored baseline hashes are strictly preserved and never overwritten.

4. **Investigation Readiness / Coverage Indicator**:
   - 6-factor weighted metric calculating investigative completeness across:
     - Case Setup & Classification: 15%
     - Person of Interest Coverage: 15%
     - Timeline Documentation: 20%
     - Physical/Digital Evidence: 20%
     - Competing Hypotheses (ACH): 20%
     - Task Resolution: 10%
   - Safeguard: Explicit disclaimer that readiness score measures documentation thoroughness, not factual guilt.

5. **Court / Investigation Case Package with Dual SHA-256 Hashing**:
   - Deterministic Canonical Record Set SHA-256: Hashes normalized case data (UTF-8 LF) excluding volatile export timestamps for repeatable verification.
   - Exported Package File SHA-256: Cryptographically seals the complete exported file.
   - Print-optimized court layout via `@media print`.

6. **Unified 9-Tab Investigation Workspace**:
   - Tabs: `Overview`, `Timeline`, `People`, `Evidence`, `Analysis (ACH, Gaps, Signals)`, `Tasks`, `Graph`, `Audit`, `Package`.
   - Universal URL binding (`?caseId=...`) with zero hardcoded mock fallbacks.

7. **Security & Role-Based Access Control (RBAC)**:
   - Roles: `ADMIN`, `COMMAND_OFFICER`, `INVESTIGATOR`, `FIELD_AGENT`.
   - Strict server-side route and API enforcement (e.g., `FIELD_AGENT` is blocked with 403 Forbidden from Command Center, Suspect registry, and Officer management).

---

## 🚀 Quick Start Guide

### Prerequisites
- Java JDK 11 or higher
- SQLite JDBC driver (`lib/sqlite-jdbc.jar` bundled in repository)

### Running on Windows
Double-click `run.bat` or run:
```cmd
run.bat
```

### Running on Linux / macOS
```bash
chmod +x run.sh test.sh
./run.sh
```

### Default Credentials
| Username | Password | Role | Access Level |
|---|---|---|---|
| `admin` | `ChangeMe!2026` | Admin | Full Access |
| `fieldagent` | `ChangeMe!2026` | Field Agent | Operational Access (Restricted from Command Center & Management) |

Server URL: **http://localhost:8081/login**

---

## 🧪 Automated Acceptance Audit Suite

Run the full 14-phase acceptance audit and regression verification suite:
```cmd
test.bat
```
or on Linux:
```bash
./test.sh
```

The test suite exercises:
- **Phase 1: Zero-Data-Loss & Individual Row Preservation**: Verifies 12/12 original `case_events` rows preserved with zero modifications, zero data loss, and zero duplicate rows across migration cycles.
- **Phase 2: Unauthenticated Access Protection**: Rejects unauthenticated requests with 401 Unauthorized or login redirect.
- **Phase 3: Administrative Authentication & Session Creation**: Authenticates administrative credentials and issues secure HTTP session cookie.
- **Phase 4: Core Protected APIs**: Verifies all 17 REST endpoints return 200 OK with valid JSON payloads.
- **Phase 5: Epistemological Timeline & Neutral Gap Detection**: Validates dual truth levels (`FACT` vs `REPORTED`) and algorithmic detection of the 18:47 → 18:56 (9-minute) temporal gap with neutral disclaimers.
- **Phase 6: ACH Grounding & Semantic Neutrality Review**: Verifies $H_1$, $H_2$, $H_3$ hypotheses are strictly grounded in database records, checks contradiction counters, verifies epistemic disclaimers, and prevents automated guilt conclusions.
- **Phase 7: Cross-Case Mutation Isolation Enforcement**: Rejects cross-case hypothesis-evidence associations with 400 Bad Request and guarantees database remains completely unchanged.
- **Phase 8: Digital Evidence Integrity, Negative Tamper Detection & Path Isolation**: Exercises positive SHA-256 verification, path traversal prevention, negative tamper detection on byte modification, preservation of stored baseline hashes, and fixture restoration.
- **Phase 9: Custody-Aware Evidence & Readiness Boundary Tests**: Verifies readiness score logic (digital requires verified hash; physical requires custody log history; penalizes missing custody; handles 0-hypothesis boundary).
- **Phase 10: Court Package Deterministic Repeatability & Dual Hashes**: Verifies identical canonical record hashes across multiple exports while generating unique sealed package file hashes.
- **Phase 11: Audit Trail Append-Oriented Protection**: Verifies POST, PUT, and DELETE methods are rejected with 405 Method Not Allowed on audit endpoints.
- **Phase 12: RBAC Mutation Security & Session Actor Integrity**: Verifies 401 on unauthenticated mutations, 403 Forbidden for Field Agent unauthorized operations, 201 Created on authorized mutations, and session actor integrity (anti-spoofing).
- **Phase 13: Consistent ISO-8601 Timestamp Formatting**: Validates all event and audit timestamps adhere to strict ISO-8601 format.
- **Phase 14: UI Pages & Static Assets Availability**: Verifies all 11 web routes, HTML pages, CSS, and JS assets return 200 OK.


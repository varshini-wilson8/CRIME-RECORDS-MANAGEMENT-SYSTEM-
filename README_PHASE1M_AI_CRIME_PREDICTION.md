# CRMS Phase 1M-AI — Crime Pattern Prediction & Historical Forecasting Engine

## 1. Overview
Phase 1M-AI integrates an aggregate, statistical spatiotemporal machine learning forecasting engine into CRMS 2.0 without introducing external Python or cloud ML dependencies. The engine is written in pure Java 11 and operates strictly on historical incident data (`cases`, `case_locations`, `locations`, `case_events`).

### Epistemic & Ethical Boundaries (Non-Goals)
- **Zero Individual Profiling:** The model does NOT ingest, store, or evaluate `persons`, `suspects`, `case_persons`, `risk_level`, `repeat_offender`, names, genders, race, or phone numbers.
- **Aggregate Incident Patterns Only:** Predictions reflect spatio-temporal crime likelihoods across environments, times of day, days of the week, and geographic grid sectors.
- **Severity Excluded:** `severity` is deliberately excluded from the feature vector to avoid circular feedback loops.
- **Observation Ground Truth:** Exactly one observation represents one historical case incident ($N=5$). Row multiplication across multiple timeline events is prohibited.
- **Unbiased Null Preservation:** Cases with unrecorded incident hours or unmapped coordinates retain `null` values; no manufactured 12:00 PM or (0,0) coordinates are introduced.
- **Honest Telemetry & Limitations:** When sample size is small ($N < 10$), the system transparently reports `LIMITED_SAMPLE_SIZE` and `dataSupportLevel="LIMITED"`. Metric fabrication (e.g. hardcoded 72%) is strictly prohibited. The current historical database contains only Burglary observations, so the model's available distribution is dominated by that class until additional historical diversity accumulates.

---

## 2. Technical Architecture

### 2.1 Model Implementation (`CrimePatternPredictionEngine.java`)
- **Algorithm:** Multiclass Categorical Naive Bayes with Laplace smoothing ($k=1.0$).
- **Log-Space Computation:** Evaluates posterior log-likelihoods:
  $$\ln P(C_k \mid X) = \ln P(C_k) + \sum_{j} \ln P(x_j \mid C_k)$$
- **Normalization:** Uses stable log-sum-exp normalization to compute **normalized posterior probabilities** summing strictly to $1.0 \pm 0.0001$.
- **Validation:** Automated Leave-One-Out Cross-Validation (LOOCV). Transparently computes multi-class Accuracy, Precision, Recall, and Macro-F1 when multiple classes exist, or reports single-class dataset limitations honestly.
- **Diurnal Risk Modeling:** Computes a continuous 24-hour diurnal risk curve using Laplace-smoothed hourly distribution across incident timestamps.
- **Distance-Decay Hotspot Density:** Implements Haversine distance-decay risk scoring across known precinct locations matching the exact Java implementation (`CrimePredictionApiHandler.java`):
  ```java
  double density = Math.min(100.0, Math.max(10.0, Math.round(100.0 / (1.0 + (dist * 0.5)))));
  ```
  Where `dist` is the distance in kilometers from the query location, bounded in `[10.0, 100.0]`:
  $$\text{Density}(d) = \min\left(100.0, \max\left(10.0, \text{round}\left(\frac{100.0}{1.0 + 0.5 \times d}\right)\right)\right)$$

### 2.2 Data Layer (`CrimePredictionDao.java`)
- Queries live SQLite `ccecs.db` to extract historical features.
- Priority hierarchy for single-observation timestamp:
  1. Primary `INCIDENT` event timestamp.
  2. `CASE_OPENED` event timestamp.
  3. Earliest case event timestamp.
  4. Case `opening_date` (with `hour = null`).
- Returns immutable `CrimePatternFeature` instances.

### 2.3 API Layer (`CrimePredictionApiHandler.java`)
- **`GET /api/ai/patterns`**: Returns model architecture metadata, evaluation metrics, known locations, diurnal curve, spatial hotspots, and epistemic disclaimer.
- **`POST /api/ai/predict`**: Accepts scenario parameters (`locationId`, `locationType`, `targetHour`, `targetDayOfWeek`, `latitude`, `longitude`), runs deterministic prediction, records `AI_FORECAST` in `audit_logs`, and returns normalized posterior probabilities and spatial hotspots.
- **Security:** Protected by `RoleProtectedHandler`. Rejects unauthenticated requests with 401 Unauthorized.

### 2.4 Presentation Layer
- **Dedicated Workspace (`/crime-prediction`)**: 4-card interface featuring Model Architecture & Integrity telemetry, Scenario Forecast Builder, Dynamic Probability Bars, and Spatiotemporal Patterns.
- **Analytical Distinction Guide:** Clear visual separation between:
  1. `● Real Historical Observations` (active database records)
  2. `◎ Statistical Forecast Patterns` (probabilistic posterior distributions)
  3. `🔬 Synthetic Benchmark Validation` (test fixture verification)
- **Interactive Crime Map (`/map`)**: Interactive AI Pattern Layer toggle rendering dashed amber heat rings (`stroke-dasharray="5,4"`, `◎`) around forecast hotspots, visually distinguished from solid historical incident pins (`●`), with active safeguard banner.
- **Command Center (`/command-center`)**: Dedicated "AI Crime Pattern Intelligence" panel summarizing model version, training cases, evaluation status, and peak temporal windows with a direct link to the forecast builder.
- **Global Header & Navigation (`app.js`)**: Seamless sidebar navigation links to Crime Prediction across all workspaces.

---

## 3. Verification & Testing (`TestCrimePrediction.java`)

### Important Distinction: Production Dataset vs Synthetic Benchmark
- **Production Dataset Evaluation:** Operates on the 5 genuine cases in `ccecs.db`. Status is strictly `LIMITED_SAMPLE_SIZE` with class-homogeneous baseline accuracy and null precision/recall/F1.
- **Synthetic Multi-Class Benchmark:** A balanced multi-class testing fixture (Burglary, Robbery, Theft) executed exclusively in test phase 4 to verify that the mathematical formulas for multi-class LOOCV, Precision, Recall, and Macro-F1 calculate accurately. **This synthetic 1.00 score is a software unit test verification and is NOT the performance metric of the CRMS production system.**

### Verification Phases (All 11 Passed):
1. **Feature Extraction Integrity:** Exactly 5 cases extracted into 5 unique observations ($N=5$).
2. **Safeguard Verification:** Reflection confirms absence of suspect/person/severity/risk fields. Null hours and coordinates preserved.
3. **Model Evaluation:** LOOCV runs cleanly and reports `LIMITED_SAMPLE_SIZE` with honest notice.
4. **Synthetic Multi-Class Benchmark:** LOOCV computes real Accuracy, Precision, Recall, and Macro-F1 ($1.00$) on synthetic multi-class validation data.
5. **Deterministic Behavior:** Log-sum-exp normalization verified (probabilities sum strictly to 1.0; identical inputs yield identical outputs).
6. **Diurnal Risk Curve:** Generates 24-hour probability distribution.
7. **Security & Authentication:** 401 on unauthenticated calls; 200 on authenticated session.
8. **Patterns API:** Returns valid telemetry, diurnal curve, and disclaimer.
9. **Scenario Prediction API:** Returns normalized posterior probabilities and spatial hotspots.
10. **Audit Trail Accountability:** Verified `AI_FORECAST` record in `audit_logs`.
11. **Dedicated UI:** Verified 200 OK for `/crime-prediction`.

---

## 4. Compilation & Execution

```powershell
# Compile Java Sources
javac -encoding UTF-8 -cp "lib\sqlite-jdbc.jar;." -d out *.java model\*.java repository\*.java service\*.java util\*.java menu\*.java

# Run Server
java -cp "out;lib\sqlite-jdbc.jar" SecureWebServer

# Run AI Test Suite
java -cp "out;lib\sqlite-jdbc.jar" TestCrimePrediction

# Run Full Regression Audits
java -cp "out;lib\sqlite-jdbc.jar" ApiTester
java -cp "out;lib\sqlite-jdbc.jar" TestAuthWorkflow

# Package Release Archive (Excludes ccecs.db)
powershell -ExecutionPolicy Bypass -File .\package_release.ps1
```

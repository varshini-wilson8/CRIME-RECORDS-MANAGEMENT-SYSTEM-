import java.time.Instant;
import java.util.*;
import model.CrimePatternFeature;

/**
 * Categorical Naive Bayes Crime Pattern Prediction Engine (Phase 1M-AI).
 *
 * Epistemic & Ethical Invariants:
 * 1. Categorical Naive Bayes with Laplace smoothing (k=1.0) in pure Java 11.
 * 2. Strictly spatio-temporal features: location type, time slot, day type, spatial grid.
 * 3. Never consumes suspect/person identifiers or severity attributes.
 * 4. Genuine model evaluation via Leave-One-Out Cross-Validation (LOOCV).
 * 5. Transparent reporting: if N < 10 or distinct classes < 2, sets status to
 *    LIMITED_SAMPLE_SIZE with exact calculated numbers (zero fabricated metrics).
 * 6. Deterministic: identical inputs always yield identical probabilities summing to 1.0.
 */
public final class CrimePatternPredictionEngine {

    public enum ModelState {
        NOT_INITIALIZED,
        TRAINING,
        READY,
        READY_WITH_LIMITED_EVALUATION,
        INSUFFICIENT_DATA,
        ERROR
    }

    private volatile ModelState state = ModelState.NOT_INITIALIZED;
    private final String modelVersion = "CRMS-NB-1.0";

    // Trained Model Parameters
    private List<CrimePatternFeature> trainingDataset = Collections.emptyList();
    private Set<String> distinctClasses = new TreeSet<>();
    private Set<String> knownLocationTypes = new TreeSet<>();
    private Set<String> knownTimeSlots = new TreeSet<>(Arrays.asList("NIGHT", "MORNING", "AFTERNOON", "EVENING"));
    private Set<String> knownDayTypes = new TreeSet<>(Arrays.asList("WEEKDAY", "WEEKEND"));
    private Set<String> knownSpatialGrids = new TreeSet<>();

    // Frequencies
    private Map<String, Integer> classCounts = new HashMap<>();
    private Map<String, Map<String, Integer>> locTypeCounts = new HashMap<>(); // class -> locType -> count
    private Map<String, Map<String, Integer>> timeSlotCounts = new HashMap<>(); // class -> slot -> count
    private Map<String, Map<String, Integer>> dayTypeCounts = new HashMap<>(); // class -> dayType -> count
    private Map<String, Map<String, Integer>> gridCounts = new HashMap<>(); // class -> grid -> count
    private int totalSamples = 0;

    // Evaluation Metrics
    private EvaluationMetrics evaluationMetrics;

    public CrimePatternPredictionEngine() {
        this.evaluationMetrics = new EvaluationMetrics(
            null, null, null, null,
            "NOT_INITIALIZED",
            "Model has not yet been trained.",
            "NONE",
            Instant.now().toString(),
            0, 0, Collections.emptyMap()
        );
    }

    public synchronized void train(List<CrimePatternFeature> dataset) {
        this.state = ModelState.TRAINING;
        if (dataset == null || dataset.isEmpty()) {
            this.state = ModelState.INSUFFICIENT_DATA;
            this.trainingDataset = Collections.emptyList();
            this.evaluationMetrics = new EvaluationMetrics(
                null, null, null, null,
                "INSUFFICIENT_DATA",
                "Historical dataset contains 0 incident records.",
                "NONE",
                Instant.now().toString(),
                0, 0, Collections.emptyMap()
            );
            return;
        }

        this.trainingDataset = new ArrayList<>(dataset);
        this.totalSamples = dataset.size();

        // 1. Reset parameter collections
        this.distinctClasses.clear();
        this.knownLocationTypes.clear();
        this.knownSpatialGrids.clear();
        this.classCounts.clear();
        this.locTypeCounts.clear();
        this.timeSlotCounts.clear();
        this.dayTypeCounts.clear();
        this.gridCounts.clear();

        // 2. Discover vocabulary and accumulate feature counts
        for (CrimePatternFeature f : dataset) {
            String c = f.getCrimeType();
            distinctClasses.add(c);
            classCounts.put(c, classCounts.getOrDefault(c, 0) + 1);

            if (f.getLocationType() != null && !"UNKNOWN".equalsIgnoreCase(f.getLocationType())) {
                knownLocationTypes.add(f.getLocationType());
                increment(locTypeCounts, c, f.getLocationType());
            }

            String slot = f.getTimeSlot();
            if (!"UNKNOWN".equalsIgnoreCase(slot)) {
                increment(timeSlotCounts, c, slot);
            }

            String dayType = f.getDayType();
            if (!"UNKNOWN".equalsIgnoreCase(dayType)) {
                increment(dayTypeCounts, c, dayType);
            }

            String grid = f.getSpatialGridKey();
            if (!"UNMAPPED".equalsIgnoreCase(grid)) {
                knownSpatialGrids.add(grid);
                increment(gridCounts, c, grid);
            }
        }

        // 3. Perform Leave-One-Out Cross-Validation (LOOCV)
        this.evaluationMetrics = evaluateLOOCV(dataset);

        // 4. Update State
        if (totalSamples < 10 || distinctClasses.size() < 2) {
            this.state = ModelState.READY_WITH_LIMITED_EVALUATION;
        } else {
            this.state = ModelState.READY;
        }
    }

    private void increment(Map<String, Map<String, Integer>> outer, String cls, String featureVal) {
        outer.computeIfAbsent(cls, k -> new HashMap<>())
             .merge(featureVal, 1, Integer::sum);
    }

    /**
     * Honest Leave-One-Out Cross Validation (LOOCV).
     * If sample size is small (< 10) or classes < 2, transparently records
     * LIMITED_SAMPLE_SIZE with exact calculated numbers.
     */
    private EvaluationMetrics evaluateLOOCV(List<CrimePatternFeature> dataset) {
        int n = dataset.size();
        int classCount = distinctClasses.size();
        Map<String, Integer> dist = new TreeMap<>(classCounts);

        if (n == 0) {
            return new EvaluationMetrics(null, null, null, null,
                "INSUFFICIENT_DATA", "Dataset is empty.", "LOOCV", Instant.now().toString(), 0, 0, dist);
        }

        if (classCount < 2) {
            // Only 1 crime class exists in historical cases
            return new EvaluationMetrics(
                1.0, // Trivial single-class accuracy
                null, // Multi-class precision undefined
                null, // Multi-class recall undefined
                null, // Multi-class F1 undefined
                "LIMITED_SAMPLE_SIZE",
                "The current historical dataset contains only " + distinctClasses.iterator().next() +
                " observations (" + n + " cases), so the model's available crime-type distribution is dominated by that class. Additional historical crime-type diversity is required for meaningful multiclass comparison.",
                "LEAVE_ONE_OUT_CV",
                Instant.now().toString(),
                n, classCount, dist
            );
        }

        // Multiple classes: run LOOCV folds
        int correct = 0;
        Map<String, Integer> tp = new HashMap<>();
        Map<String, Integer> fp = new HashMap<>();
        Map<String, Integer> fn = new HashMap<>();

        for (String c : distinctClasses) {
            tp.put(c, 0);
            fp.put(c, 0);
            fn.put(c, 0);
        }

        for (int i = 0; i < n; i++) {
            List<CrimePatternFeature> trainSplit = new ArrayList<>(dataset);
            CrimePatternFeature testItem = trainSplit.remove(i);

            // Sub-engine trained on split
            CrimePatternPredictionEngine foldModel = new CrimePatternPredictionEngine();
            foldModel.trainFoldWithoutEvaluation(trainSplit);

            String predicted = foldModel.predictTopClass(
                testItem.getLocationType(),
                testItem.getTimeSlot(),
                testItem.getDayType(),
                testItem.getSpatialGridKey()
            );

            String actual = testItem.getCrimeType();
            if (Objects.equals(predicted, actual)) {
                correct++;
                tp.put(actual, tp.get(actual) + 1);
            } else {
                if (predicted != null && fp.containsKey(predicted)) {
                    fp.put(predicted, fp.get(predicted) + 1);
                }
                fn.put(actual, fn.get(actual) + 1);
            }
        }

        double accuracy = (double) correct / n;
        double sumPrecision = 0.0;
        double sumRecall = 0.0;
        double sumF1 = 0.0;
        int evaluatedClasses = 0;

        for (String c : distinctClasses) {
            int t = tp.get(c);
            int p = fp.get(c);
            int f = fn.get(c);

            double prec = (t + p > 0) ? (double) t / (t + p) : 0.0;
            double rec = (t + f > 0) ? (double) t / (t + f) : 0.0;
            double f1 = (prec + rec > 0) ? (2.0 * prec * rec) / (prec + rec) : 0.0;

            sumPrecision += prec;
            sumRecall += rec;
            sumF1 += f1;
            evaluatedClasses++;
        }

        double macroPrecision = evaluatedClasses > 0 ? sumPrecision / evaluatedClasses : 0.0;
        double macroRecall = evaluatedClasses > 0 ? sumRecall / evaluatedClasses : 0.0;
        double macroF1 = evaluatedClasses > 0 ? sumF1 / evaluatedClasses : 0.0;

        String status = (n < 10) ? "LIMITED_SAMPLE_SIZE" : "EVALUATED";
        String notice = (n < 10)
            ? "Sample size N=" + n + " with " + classCount + " classes is small. Metrics reflect active sample distribution."
            : "Model successfully evaluated via Leave-One-Out Cross-Validation.";

        return new EvaluationMetrics(
            round2(accuracy),
            round2(macroPrecision),
            round2(macroRecall),
            round2(macroF1),
            status,
            notice,
            "LEAVE_ONE_OUT_CV",
            Instant.now().toString(),
            n, classCount, dist
        );
    }

    private void trainFoldWithoutEvaluation(List<CrimePatternFeature> split) {
        this.totalSamples = split.size();
        for (CrimePatternFeature f : split) {
            String c = f.getCrimeType();
            distinctClasses.add(c);
            classCounts.put(c, classCounts.getOrDefault(c, 0) + 1);

            if (f.getLocationType() != null && !"UNKNOWN".equalsIgnoreCase(f.getLocationType())) {
                knownLocationTypes.add(f.getLocationType());
                increment(locTypeCounts, c, f.getLocationType());
            }
            if (!"UNKNOWN".equalsIgnoreCase(f.getTimeSlot())) {
                increment(timeSlotCounts, c, f.getTimeSlot());
            }
            if (!"UNKNOWN".equalsIgnoreCase(f.getDayType())) {
                increment(dayTypeCounts, c, f.getDayType());
            }
            if (!"UNMAPPED".equalsIgnoreCase(f.getSpatialGridKey())) {
                knownSpatialGrids.add(f.getSpatialGridKey());
                increment(gridCounts, c, f.getSpatialGridKey());
            }
        }
    }

    /**
     * Compute normalized posterior Categorical Naive Bayes probabilities for a scenario.
     */
    public List<PredictionEntry> predict(String locationType, Integer targetHour, Integer targetDayOfWeek, String spatialGrid) {
        if (distinctClasses.isEmpty()) {
            return Collections.emptyList();
        }

        String locType = locationType != null ? locationType.toUpperCase(Locale.ROOT) : "UNKNOWN";

        String slot = "UNKNOWN";
        if (targetHour != null && targetHour >= 0 && targetHour < 24) {
            int h = targetHour;
            if (h >= 22 || h <= 5) slot = "NIGHT";
            else if (h <= 11) slot = "MORNING";
            else if (h <= 16) slot = "AFTERNOON";
            else slot = "EVENING";
        }

        String dayType = "UNKNOWN";
        if (targetDayOfWeek != null && targetDayOfWeek >= 0 && targetDayOfWeek <= 6) {
            dayType = (targetDayOfWeek == 0 || targetDayOfWeek == 6) ? "WEEKEND" : "WEEKDAY";
        }

        String grid = (spatialGrid != null && !spatialGrid.isBlank()) ? spatialGrid : "UNMAPPED";

        return computeProbabilities(locType, slot, dayType, grid);
    }

    private String predictTopClass(String locType, String slot, String dayType, String grid) {
        List<PredictionEntry> entries = computeProbabilities(locType, slot, dayType, grid);
        return entries.isEmpty() ? null : entries.get(0).getCrimeType();
    }

    private List<PredictionEntry> computeProbabilities(String locType, String slot, String dayType, String grid) {
        int cSize = distinctClasses.size();
        int locVocab = Math.max(knownLocationTypes.size(), 1);
        int slotVocab = knownTimeSlots.size();
        int dayVocab = knownDayTypes.size();
        int gridVocab = Math.max(knownSpatialGrids.size(), 1);

        Map<String, Double> logScores = new LinkedHashMap<>();
        double maxLog = -Double.MAX_VALUE;

        for (String c : distinctClasses) {
            int cCount = classCounts.getOrDefault(c, 0);

            // Prior P(C_k) with Laplace smoothing
            double pPrior = (double) (cCount + 1) / (totalSamples + cSize);
            double logProb = Math.log(pPrior);

            // Conditional P(LocType | C_k)
            if (!"UNKNOWN".equalsIgnoreCase(locType)) {
                int matchCount = locTypeCounts.getOrDefault(c, Collections.emptyMap()).getOrDefault(locType, 0);
                double pLoc = (double) (matchCount + 1) / (cCount + locVocab);
                logProb += Math.log(pLoc);
            }

            // Conditional P(TimeSlot | C_k)
            if (!"UNKNOWN".equalsIgnoreCase(slot)) {
                int matchCount = timeSlotCounts.getOrDefault(c, Collections.emptyMap()).getOrDefault(slot, 0);
                double pSlot = (double) (matchCount + 1) / (cCount + slotVocab);
                logProb += Math.log(pSlot);
            }

            // Conditional P(DayType | C_k)
            if (!"UNKNOWN".equalsIgnoreCase(dayType)) {
                int matchCount = dayTypeCounts.getOrDefault(c, Collections.emptyMap()).getOrDefault(dayType, 0);
                double pDay = (double) (matchCount + 1) / (cCount + dayVocab);
                logProb += Math.log(pDay);
            }

            // Conditional P(SpatialGrid | C_k)
            if (!"UNMAPPED".equalsIgnoreCase(grid)) {
                int matchCount = gridCounts.getOrDefault(c, Collections.emptyMap()).getOrDefault(grid, 0);
                double pGrid = (double) (matchCount + 1) / (cCount + gridVocab);
                logProb += Math.log(pGrid);
            }

            logScores.put(c, logProb);
            if (logProb > maxLog) {
                maxLog = logProb;
            }
        }

        // Log-sum-exp normalization
        double sumExp = 0.0;
        Map<String, Double> expScores = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : logScores.entrySet()) {
            double exp = Math.exp(e.getValue() - maxLog);
            expScores.put(e.getKey(), exp);
            sumExp += exp;
        }

        List<PredictionEntry> results = new ArrayList<>();
        double accumulatedProb = 0.0;
        for (Map.Entry<String, Double> e : expScores.entrySet()) {
            double prob = sumExp > 0 ? (e.getValue() / sumExp) : (1.0 / cSize);
            accumulatedProb += prob;
            results.add(new PredictionEntry(e.getKey(), prob));
        }

        // Ensure exact 1.0 sum adjustment on largest element
        results.sort((a, b) -> Double.compare(b.getProbability(), a.getProbability()));
        if (!results.isEmpty() && Math.abs(accumulatedProb - 1.0) > 0.0001) {
            double diff = 1.0 - accumulatedProb;
            PredictionEntry top = results.get(0);
            results.set(0, new PredictionEntry(top.getCrimeType(), top.getProbability() + diff));
        }

        return results;
    }

    /**
     * Computes diurnal hourly risk distribution curve [0..23].
     */
    public List<HourlyRisk> computeHourlyRiskCurve() {
        int[] counts = new int[24];
        int totalHours = 0;
        for (CrimePatternFeature f : trainingDataset) {
            if (f.hasValidHour()) {
                counts[f.getHourOfDay()]++;
                totalHours++;
            }
        }

        List<HourlyRisk> curve = new ArrayList<>(24);
        int denom = totalHours + 24; // Laplace smoothing over 24 hours
        for (int h = 0; h < 24; h++) {
            double prob = (double) (counts[h] + 1) / denom;
            curve.add(new HourlyRisk(h, counts[h], round4(prob)));
        }
        return curve;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    // --- Accessors & Inner Types ---

    public ModelState getState() {
        return state;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public int getTotalSamples() {
        return totalSamples;
    }

    public EvaluationMetrics getEvaluationMetrics() {
        return evaluationMetrics;
    }

    public Set<String> getDistinctClasses() {
        return Collections.unmodifiableSet(distinctClasses);
    }

    public static final class PredictionEntry {
        private final String crimeType;
        private final double probability;

        public PredictionEntry(String crimeType, double probability) {
            this.crimeType = crimeType;
            this.probability = Math.max(0.0, Math.min(1.0, probability));
        }

        public String getCrimeType() {
            return crimeType;
        }

        public double getProbability() {
            return probability;
        }

        public double getPercentage() {
            return Math.round(probability * 1000.0) / 10.0;
        }
    }

    public static final class HourlyRisk {
        public final int hour;
        public final int historicalCount;
        public final double probability;

        public HourlyRisk(int hour, int historicalCount, double probability) {
            this.hour = hour;
            this.historicalCount = historicalCount;
            this.probability = probability;
        }
    }

    public static final class EvaluationMetrics {
        public final Double accuracy;
        public final Double precision;
        public final Double recall;
        public final Double f1;
        public final String status;
        public final String evaluationNotice;
        public final String evaluationMethod;
        public final String evaluationDate;
        public final int totalSamples;
        public final int distinctClasses;
        public final Map<String, Integer> classDistribution;

        public EvaluationMetrics(Double accuracy, Double precision, Double recall, Double f1,
                                 String status, String evaluationNotice, String evaluationMethod,
                                 String evaluationDate, int totalSamples, int distinctClasses,
                                 Map<String, Integer> classDistribution) {
            this.accuracy = accuracy;
            this.precision = precision;
            this.recall = recall;
            this.f1 = f1;
            this.status = status;
            this.evaluationNotice = evaluationNotice;
            this.evaluationMethod = evaluationMethod;
            this.evaluationDate = evaluationDate;
            this.totalSamples = totalSamples;
            this.distinctClasses = distinctClasses;
            this.classDistribution = classDistribution;
        }
    }
}

package service;

import model.CaseRecord;
import model.Officer;
import model.Suspect;
import repository.CaseRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Business logic layer sitting on top of CaseRepository.
 * Implements the three "special" features of CCECS:
 *   1. Officer workload auto-assignment
 *   2. Repeat-offender auto-flagging
 *   3. Priority score based sorting
 */
public class CaseService {

    private final CaseRepository repository;
    private final List<Officer> officers;

    public CaseService(CaseRepository repository) {
        this.repository = repository;
        this.officers = new ArrayList<>();
    }

    // ----- Officer management -----

    public void addOfficer(Officer officer) {
        officers.add(officer);
    }

    public List<Officer> getAllOfficers() {
        return officers;
    }

    /**
     * FEATURE: Officer Workload Auto-Assignment.
     * Scans all registered officers and returns whichever currently
     * has the fewest active cases. Ties are broken by list order.
     */
    private Officer findLeastLoadedOfficer() {
        Officer chosen = null;
        for (Officer o : officers) {
            if (chosen == null || o.getActiveCaseCount() < chosen.getActiveCaseCount()) {
                chosen = o;
            }
        }
        return chosen;
    }

    /**
     * Adds a new case to the repository and automatically assigns it
     * to the least-loaded officer. Returns the officer that was assigned
     * (or null if no officers are registered yet).
     */
    public Officer addCaseWithAutoAssignment(CaseRecord caseRecord) {
        repository.addCase(caseRecord);

        Officer leastLoaded = findLeastLoadedOfficer();
        if (leastLoaded != null) {
            caseRecord.assignOfficer(leastLoaded); // also increments officer's active count
        }
        return leastLoaded;
    }

    // ----- Suspect linking + repeat offender detection -----

    /**
     * FEATURE: Repeat-Offender Auto-Flag.
     * Links a suspect to a case, then scans all existing cases for any
     * prior appearance of the same suspect ID. If found, the suspect
     * object is flagged as a repeat offender.
     *
     * @return true if the suspect was flagged as a repeat offender as a
     *         result of this link, false otherwise.
     */
    public boolean linkSuspectToCase(CaseRecord caseRecord, Suspect suspect) {
        // Count how many existing links already reference this suspect ID
        // BEFORE we add this new one, so we can tell if they were already
        // linked elsewhere.
        int priorLinks = repository.countCaseLinksForSuspect(suspect.getId());

        caseRecord.linkSuspect(suspect);

        if (!repository.getAllSuspects().contains(suspect)) {
            repository.addSuspect(suspect);
        }

        if (priorLinks > 0) {
            suspect.setRepeatOffender(true);
            return true;
        }
        return false;
    }

    // ----- Priority scoring -----

    /**
     * FEATURE: Priority Scoring.
     * Returns all cases sorted by computed priority score, highest first.
     * The score itself is computed inside CaseRecord.calculatePriorityScore(),
     * which factors in severity and days open.
     */
    public List<CaseRecord> getCasesSortedByPriority() {
        List<CaseRecord> allCases = new ArrayList<>(repository.getAllCases());
        allCases.sort(Comparator.comparingInt(CaseRecord::calculatePriorityScore).reversed());
        return allCases;
    }

    // ----- Pass-through convenience methods -----

    public CaseRecord searchById(String caseId) {
        return repository.search(caseId);
    }

    public List<CaseRecord> searchByTitle(String keyword) {
        return repository.searchByTitleKeyword(keyword);
    }

    public boolean deleteCase(String caseId) {
        return repository.deleteCase(caseId);
    }

    public List<CaseRecord> getAllCases() {
        return repository.getAllCases();
    }

    public CaseRepository getRepository() {
        return repository;
    }
}

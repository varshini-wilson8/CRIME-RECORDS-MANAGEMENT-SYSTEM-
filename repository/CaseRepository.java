package repository;

import model.CaseRecord;
import model.CaseStatus;
import model.Suspect;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory data store for CaseRecord and Suspect objects.
 * Provides add, search, and filter operations. This is the only
 * layer that touches the raw lists directly - CaseService and Menu
 * should always go through this class rather than holding their own lists.
 */
public class CaseRepository {

    private final List<CaseRecord> cases;
    private final List<Suspect> suspects;

    public CaseRepository() {
        this.cases = new ArrayList<>();
        this.suspects = new ArrayList<>();
    }

    // ----- Case operations -----

    public void addCase(CaseRecord caseRecord) {
        cases.add(caseRecord);
    }

    public List<CaseRecord> getAllCases() {
        return cases;
    }

    /**
     * Searches for a single case by its exact case ID.
     * POLYMORPHISM: overloaded alongside search(String) below.
     */
    public CaseRecord search(String caseId) {
        for (CaseRecord c : cases) {
            if (c.getCaseId().equalsIgnoreCase(caseId)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Searches for cases whose title contains the given keyword
     * (case-insensitive, partial match).
     * POLYMORPHISM: overloaded alongside search(String) above but
     * kept as a distinct name (searchByTitleKeyword) since Java cannot
     * overload two methods with the identical (String) signature.
     * Kept here so both search styles live in one place.
     */
    public List<CaseRecord> searchByTitleKeyword(String keyword) {
        List<CaseRecord> matches = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();
        for (CaseRecord c : cases) {
            if (c.getTitle().toLowerCase().contains(lowerKeyword)) {
                matches.add(c);
            }
        }
        return matches;
    }

    public List<CaseRecord> filterByStatus(CaseStatus status) {
        List<CaseRecord> matches = new ArrayList<>();
        for (CaseRecord c : cases) {
            if (c.getStatus() == status) {
                matches.add(c);
            }
        }
        return matches;
    }

    public boolean deleteCase(String caseId) {
        return cases.removeIf(c -> c.getCaseId().equalsIgnoreCase(caseId));
    }

    // ----- Suspect operations -----

    public void addSuspect(Suspect suspect) {
        suspects.add(suspect);
    }

    public List<Suspect> getAllSuspects() {
        return suspects;
    }

    public Suspect findSuspectById(String suspectId) {
        for (Suspect s : suspects) {
            if (s.getId().equalsIgnoreCase(suspectId)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Counts how many existing cases already have this suspect ID linked.
     * Used by CaseService for the repeat-offender auto-flag feature.
     */
    public int countCaseLinksForSuspect(String suspectId) {
        int count = 0;
        for (CaseRecord c : cases) {
            for (Suspect s : c.getLinkedSuspects()) {
                if (s.getId().equalsIgnoreCase(suspectId)) {
                    count++;
                }
            }
        }
        return count;
    }
}

package menu;

import model.CaseRecord;
import model.CaseStatus;
import model.Evidence;
import model.EvidenceLog;
import model.Officer;
import model.Severity;
import model.Suspect;
import service.CaseService;
import util.InvalidInputException;
import util.Validation;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

/**
 * Console-based menu for the Crime Case & Evidence Chain-of-Custody
 * System (CCECS). Every action is wrapped in its own try-catch so that
 * a single bad input never terminates the whole program - the user is
 * simply shown an error message and returned to the menu.
 */
public class Menu {

    private final Scanner scanner;
    private final CaseService caseService;

    public Menu(CaseService caseService) {
        this.caseService = caseService;
        this.scanner = new Scanner(System.in);
    }

    /**
     * Main menu loop. Runs until the user chooses option 7 (Exit).
     */
    public void start() {
        boolean exit = false;

        while (!exit) {
            printMenu();
            String rawChoice = scanner.nextLine();

            try {
                int choice = Validation.validateMenuChoice(rawChoice, 7);

                switch (choice) {
                    case 1:
                        addCase();
                        break;
                    case 2:
                        searchCases();
                        break;
                    case 3:
                        updateCaseStatus();
                        break;
                    case 4:
                        linkSuspectToCase();
                        break;
                    case 5:
                        addEvidenceAndViewTrail();
                        break;
                    case 6:
                        viewCasesByPriority();
                        break;
                    case 7:
                        exit = true;
                        System.out.println("Exiting CCECS. Goodbye.");
                        break;
                    default:
                        // Unreachable because validateMenuChoice already bounds-checks,
                        // but kept for defensive completeness.
                        System.out.println("Invalid option.");
                }
            } catch (InvalidInputException e) {
                System.out.println("Input error: " + e.getMessage());
            } catch (Exception e) {
                // Final safety net - guarantees the menu loop itself never dies,
                // no matter what unexpected exception a lower layer throws.
                System.out.println("Unexpected error: " + e.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println("\n===== CCECS - Crime Case & Evidence Chain-of-Custody System =====");
        System.out.println("1. Add a crime case record (auto officer assignment)");
        System.out.println("2. Search records by case ID or title keyword");
        System.out.println("3. Update a case's status");
        System.out.println("4. Link a suspect to a case (repeat-offender check)");
        System.out.println("5. Add evidence and view custody trail");
        System.out.println("6. View all cases sorted by priority score");
        System.out.println("7. Exit");
        System.out.print("Enter your choice: ");
    }

    // ----- Option 1: Add case -----

    private void addCase() throws InvalidInputException {
        System.out.print("Case ID: ");
        String caseId = Validation.validateText(scanner.nextLine(), "Case ID");

        if (caseService.searchById(caseId) != null) {
            System.out.println("A case with ID '" + caseId + "' already exists.");
            return;
        }

        System.out.print("Title: ");
        String title = Validation.validateText(scanner.nextLine(), "Title");

        System.out.print("Description: ");
        String description = Validation.validateText(scanner.nextLine(), "Description");

        Severity severity = promptSeverity();

        CaseRecord caseRecord = new CaseRecord(caseId, title, description, severity);
        Officer assigned = caseService.addCaseWithAutoAssignment(caseRecord);

        System.out.println("Case '" + caseId + "' added successfully.");
        if (assigned != null) {
            System.out.println("Auto-assigned to officer: " + assigned.getName()
                    + " (badge " + assigned.getBadgeNumber()
                    + ", now handling " + assigned.getActiveCaseCount() + " case(s)).");
        } else {
            System.out.println("No officers are registered yet - case left unassigned.");
        }
    }

    private Severity promptSeverity() throws InvalidInputException {
        System.out.println("Severity: 1=LOW  2=MEDIUM  3=HIGH  4=CRITICAL");
        System.out.print("Choose severity: ");
        int choice = Validation.validateNumber(scanner.nextLine(), "Severity", 1, 4);
        switch (choice) {
            case 1: return Severity.LOW;
            case 2: return Severity.MEDIUM;
            case 3: return Severity.HIGH;
            default: return Severity.CRITICAL;
        }
    }

    // ----- Option 2: Search -----

    private void searchCases() throws InvalidInputException {
        System.out.println("Search by: 1=Case ID  2=Title keyword");
        int mode = Validation.validateNumber(scanner.nextLine(), "Search mode", 1, 2);

        if (mode == 1) {
            System.out.print("Enter Case ID: ");
            String caseId = Validation.validateText(scanner.nextLine(), "Case ID");
            CaseRecord found = caseService.searchById(caseId);
            if (found == null) {
                System.out.println("No case found with ID '" + caseId + "'.");
            } else {
                System.out.println(found);
            }
        } else {
            System.out.print("Enter title keyword: ");
            String keyword = Validation.validateText(scanner.nextLine(), "Keyword");
            List<CaseRecord> matches = caseService.searchByTitle(keyword);
            if (matches.isEmpty()) {
                System.out.println("No cases match keyword '" + keyword + "'.");
            } else {
                System.out.println("Found " + matches.size() + " matching case(s):");
                for (CaseRecord c : matches) {
                    System.out.println(c);
                }
            }
        }
    }

    // ----- Option 3: Update status -----

    private void updateCaseStatus() throws InvalidInputException {
        System.out.print("Enter Case ID to update: ");
        String caseId = Validation.validateText(scanner.nextLine(), "Case ID");
        CaseRecord caseRecord = caseService.searchById(caseId);

        if (caseRecord == null) {
            System.out.println("No case found with ID '" + caseId + "'.");
            return;
        }

        System.out.println("Current status: " + caseRecord.getStatus());
        System.out.println("New status: 1=OPEN  2=UNDER_INVESTIGATION  3=CLOSED  4=COLD");
        int choice = Validation.validateNumber(scanner.nextLine(), "Status choice", 1, 4);

        CaseStatus newStatus;
        switch (choice) {
            case 1: newStatus = CaseStatus.OPEN; break;
            case 2: newStatus = CaseStatus.UNDER_INVESTIGATION; break;
            case 3: newStatus = CaseStatus.CLOSED; break;
            default: newStatus = CaseStatus.COLD; break;
        }

        caseRecord.setStatus(newStatus);
        System.out.println("Case '" + caseId + "' status updated to " + newStatus + ".");
    }

    // ----- Option 4: Link suspect -----

    private void linkSuspectToCase() throws InvalidInputException {
        System.out.print("Enter Case ID: ");
        String caseId = Validation.validateText(scanner.nextLine(), "Case ID");
        CaseRecord caseRecord = caseService.searchById(caseId);

        if (caseRecord == null) {
            System.out.println("No case found with ID '" + caseId + "'.");
            return;
        }

        System.out.print("Suspect ID: ");
        String suspectId = Validation.validateText(scanner.nextLine(), "Suspect ID");

        Suspect suspect = caseService.getRepository().findSuspectById(suspectId);
        if (suspect == null) {
            System.out.print("New suspect - enter name: ");
            String name = Validation.validateText(scanner.nextLine(), "Name");
            System.out.print("Contact: ");
            String contact = Validation.validateText(scanner.nextLine(), "Contact");
            System.out.print("Physical description: ");
            String physicalDescription = Validation.validateText(scanner.nextLine(), "Physical description");
            System.out.print("Risk level (LOW/MEDIUM/HIGH): ");
            String riskLevel = Validation.validateText(scanner.nextLine(), "Risk level");
            suspect = new Suspect(suspectId, name, contact, physicalDescription, riskLevel);
        }

        boolean flaggedNow = caseService.linkSuspectToCase(caseRecord, suspect);

        System.out.println("Suspect '" + suspect.getName() + "' linked to case '" + caseId + "'.");
        if (flaggedNow || suspect.isRepeatOffender()) {
            System.out.println("*** ALERT: Suspect ID '" + suspectId
                    + "' is linked to more than one case - flagged as REPEAT OFFENDER. ***");
        }
    }

    // ----- Option 5: Add evidence + view trail -----

    private void addEvidenceAndViewTrail() throws InvalidInputException {
        System.out.print("Enter Case ID: ");
        String caseId = Validation.validateText(scanner.nextLine(), "Case ID");
        CaseRecord caseRecord = caseService.searchById(caseId);

        if (caseRecord == null) {
            System.out.println("No case found with ID '" + caseId + "'.");
            return;
        }

        System.out.print("Evidence ID: ");
        String evidenceId = Validation.validateText(scanner.nextLine(), "Evidence ID");
        System.out.print("Description: ");
        String description = Validation.validateText(scanner.nextLine(), "Description");
        System.out.print("Initial custodian (e.g. officer name): ");
        String custodian = Validation.validateText(scanner.nextLine(), "Custodian");

        Evidence evidence = new Evidence(evidenceId, description, LocalDate.now(), custodian);
        caseRecord.addEvidence(evidence);

        System.out.println("Evidence '" + evidenceId + "' added to case '" + caseId + "'.");
        System.out.print("Transfer custody now? (y/n): ");
        String answer = scanner.nextLine().trim().toLowerCase();

        if (answer.equals("y")) {
            System.out.print("Transfer to: ");
            String transferTo = Validation.validateText(scanner.nextLine(), "Transferred to");
            System.out.print("Remarks: ");
            String remarks = Validation.validateText(scanner.nextLine(), "Remarks");
            evidence.transferCustody(transferTo, remarks);
        }

        System.out.println("\n--- Custody Trail for Evidence '" + evidenceId + "' ---");
        for (EvidenceLog log : evidence.getCustodyHistory()) {
            System.out.println(log);
        }
    }

    // ----- Option 6: View by priority -----

    private void viewCasesByPriority() {
        List<CaseRecord> sorted = caseService.getCasesSortedByPriority();

        if (sorted.isEmpty()) {
            System.out.println("No cases recorded yet.");
            return;
        }

        System.out.println("\n--- Cases Sorted by Priority Score (highest first) ---");
        for (CaseRecord c : sorted) {
            System.out.println("[" + c.calculatePriorityScore() + "] "
                    + c.getCaseId() + " - " + c.getTitle()
                    + " (" + c.getSeverity() + ", " + c.getStatus() + ")");
        }
    }
}

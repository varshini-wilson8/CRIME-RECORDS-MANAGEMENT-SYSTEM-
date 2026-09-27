package util;

import model.CaseRecord;
import repository.CaseRepository;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Background worker (Runnable) that auto-saves all current case data
 * to data/backup.txt every 15 seconds, without blocking the console
 * menu thread. Started as a daemon thread from Main so it never
 * prevents the JVM from shutting down when the user exits the menu.
 */
public class BackupThread implements Runnable {

    private static final String BACKUP_FILE_PATH = "data/backup.txt";
    private static final long BACKUP_INTERVAL_MS = 15_000; // 15 seconds
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CaseRepository repository;
    private volatile boolean running;

    public BackupThread(CaseRepository repository) {
        this.repository = repository;
        this.running = true;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(BACKUP_INTERVAL_MS);
                performBackup();
            } catch (InterruptedException e) {
                // Thread was interrupted (e.g. during shutdown) - exit the loop cleanly.
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    /**
     * Writes every case record currently in the repository to the
     * backup file, overwriting the previous snapshot. Any IO failure
     * is logged to the console but never crashes the background thread
     * or the main menu.
     */
    private void performBackup() {
        List<CaseRecord> cases = repository.getAllCases();

        // Make sure the "data" folder exists before writing to it, since a
        // fresh checkout of the project won't have it yet.
        File backupFile = new File(BACKUP_FILE_PATH);
        File parentDir = backupFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(backupFile))) {
            writer.println("# CCECS Backup - generated " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
            writer.println("# Total cases: " + cases.size());
            writer.println();
            for (CaseRecord c : cases) {
                writer.println(c.toString());
                writer.println("----------------------------------------");
            }
            System.out.println("\n[BackupThread] Auto-saved " + cases.size()
                    + " case(s) to " + BACKUP_FILE_PATH + " at "
                    + LocalDateTime.now().format(TIMESTAMP_FORMAT));
        } catch (IOException e) {
            System.out.println("\n[BackupThread] WARNING: backup failed - " + e.getMessage());
        }
    }

    /**
     * Signals the background thread to stop after its current sleep
     * cycle finishes. Call this from Main before exiting so the
     * program shuts down cleanly.
     */
    public void stopBackup() {
        this.running = false;
    }
}

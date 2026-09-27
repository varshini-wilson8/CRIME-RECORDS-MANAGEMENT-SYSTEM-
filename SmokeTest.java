import java.sql.*;
import java.io.*;

public class SmokeTest {
    public static void main(String[] args) {
        System.out.println("=== CRMS Phase 1A-1L Baseline Verification ===");
        try {
            Database.initialize();
            SampleDataSeeder.seed();
            System.out.println("✓ Database initialized and seeded successfully with schema_v2 and migration_v2");
            
            try (Connection c = Database.connect(); Statement s = c.createStatement()) {
                checkCount(s, "cases");
                checkCount(s, "officers");
                checkCount(s, "users");
                checkCount(s, "persons");
                checkCount(s, "case_persons");
                checkCount(s, "locations");
                checkCount(s, "case_locations");
                checkCount(s, "vehicles");
                checkCount(s, "case_vehicles");
                checkCount(s, "case_events");
                checkCount(s, "investigation_tasks");
                checkCount(s, "investigation_findings");
                checkCount(s, "case_reports");
                checkCount(s, "audit_logs");
            }
            System.out.println("✓ All Phase 1A-1L database tables verified!");
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void checkCount(Statement s, String table) throws SQLException {
        try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (r.next()) {
                System.out.println("  - Table " + table + ": " + r.getInt(1) + " rows");
            }
        }
    }
}

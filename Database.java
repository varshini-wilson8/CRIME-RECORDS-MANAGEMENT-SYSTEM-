import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/** SQLite connection factory and CRMS 2.0 schema bootstrap. */
public final class Database {
    private static final String URL = "jdbc:sqlite:ccecs.db";
    private Database() {}
    public static Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(URL);
        try (Statement s=c.createStatement()) { s.execute("PRAGMA foreign_keys = ON"); }
        return c;
    }
    public static void initialize() throws SQLException, IOException {
        executeScript("database/schema_v2.sql");
        ensureColumns();
        migrateLegacy();
    }
    public static void migrateLegacy() throws SQLException, IOException {
        ensureColumns();
        executeScript("database/migration_v2.sql");
    }
    public static void ensureColumns() {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            boolean hasCrimeType = false;
            try (ResultSet r = s.executeQuery("PRAGMA table_info(cases)")) {
                while (r.next()) {
                    if ("crime_type".equalsIgnoreCase(r.getString("name"))) {
                        hasCrimeType = true;
                        break;
                    }
                }
            }
            if (!hasCrimeType) {
                s.execute("ALTER TABLE cases ADD COLUMN crime_type TEXT DEFAULT 'BURGLARY'");
            }

            boolean hasTruthLevel = false;
            try (ResultSet r = s.executeQuery("PRAGMA table_info(case_events)")) {
                while (r.next()) {
                    if ("truth_level".equalsIgnoreCase(r.getString("name"))) {
                        hasTruthLevel = true;
                        break;
                    }
                }
            }
            if (!hasTruthLevel) {
                s.execute("ALTER TABLE case_events ADD COLUMN truth_level TEXT NOT NULL DEFAULT 'REPORTED'");
            }

            boolean hasFilePath = false;
            try (ResultSet r = s.executeQuery("PRAGMA table_info(evidence)")) {
                while (r.next()) {
                    if ("file_path".equalsIgnoreCase(r.getString("name"))) {
                        hasFilePath = true;
                        break;
                    }
                }
            }
            if (!hasFilePath) {
                s.execute("ALTER TABLE evidence ADD COLUMN file_path TEXT");
                s.execute("ALTER TABLE evidence ADD COLUMN file_hash_sha256 TEXT");
                s.execute("ALTER TABLE evidence ADD COLUMN hash_algorithm TEXT DEFAULT 'SHA-256'");
                s.execute("ALTER TABLE evidence ADD COLUMN hash_verified_at TEXT");
                s.execute("ALTER TABLE evidence ADD COLUMN file_size_bytes INTEGER");
            }

            s.execute("CREATE TABLE IF NOT EXISTS investigation_hypotheses (" +
                      "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                      "case_id TEXT NOT NULL," +
                      "hypothesis_code TEXT NOT NULL," +
                      "title TEXT NOT NULL," +
                      "description TEXT," +
                      "status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','UNDER_REVIEW','RETAINED','REJECTED_BY_INVESTIGATOR','INACTIVE'))," +
                      "created_by TEXT," +
                      "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                      "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                      "UNIQUE(case_id, hypothesis_code)," +
                      "FOREIGN KEY(case_id) REFERENCES cases(id) ON DELETE CASCADE," +
                      "FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE SET NULL)");

            s.execute("CREATE TABLE IF NOT EXISTS hypothesis_evidence (" +
                      "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                      "hypothesis_id INTEGER NOT NULL," +
                      "evidence_id TEXT NOT NULL," +
                      "relationship_type TEXT NOT NULL CHECK(relationship_type IN ('SUPPORTS','CONTRADICTS','CONTEXT','UNASSESSED'))," +
                      "notes TEXT," +
                      "created_by TEXT," +
                      "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                      "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                      "UNIQUE(hypothesis_id, evidence_id)," +
                      "FOREIGN KEY(hypothesis_id) REFERENCES investigation_hypotheses(id) ON DELETE CASCADE," +
                      "FOREIGN KEY(evidence_id) REFERENCES evidence(id) ON DELETE CASCADE)");
        } catch (Exception ignored) {}
    }
    private static void executeScript(String file) throws SQLException, IOException {
        String sql=Files.readString(Path.of(file));
        try(Connection c=connect(); Statement s=c.createStatement()) {
            StringBuilder current=new StringBuilder();
            boolean inSingle=false, inDouble=false;
            for(int i=0;i<sql.length();i++){
                char ch=sql.charAt(i);
                if(ch=='\'' && !inDouble){
                    if(inSingle && i+1<sql.length() && sql.charAt(i+1)=='\''){ current.append(ch).append(sql.charAt(++i)); continue; }
                    inSingle=!inSingle;
                } else if(ch=='\"' && !inSingle){ inDouble=!inDouble; }
                if(ch==';' && !inSingle && !inDouble){
                    executeStatement(s,current.toString()); current.setLength(0);
                } else current.append(ch);
            }
            executeStatement(s,current.toString());
        }
    }
    private static void executeStatement(Statement s,String statement)throws SQLException{
        String trimmed=statement.trim();
        if(trimmed.isEmpty()) return;
        // Remove SQL-only comment lines before deciding whether a statement is executable.
        String[] lines=trimmed.split("\\R");
        StringBuilder executable=new StringBuilder();
        for(String line:lines){String t=line.trim();if(!t.startsWith("--")){if(executable.length()>0)executable.append('\n');executable.append(line);}}
        if(executable.toString().trim().isEmpty()) return;
        s.execute(executable.toString());
    }
}

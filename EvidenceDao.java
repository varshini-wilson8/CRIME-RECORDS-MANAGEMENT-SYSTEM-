import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Read model and cryptographic hash verifier for evidence. */
public final class EvidenceDao {

    public List<String[]> rows() throws SQLException {
        List<String[]> r = new ArrayList<>();
        String q = "SELECT e.id, e.case_id, e.description, e.collection_date, e.current_custodian, " +
                "COUNT(l.id) logs, e.file_path, e.file_hash_sha256, e.hash_algorithm, e.hash_verified_at, e.file_size_bytes " +
                "FROM evidence e " +
                "LEFT JOIN evidence_logs l ON l.evidence_id = e.id " +
                "GROUP BY e.id " +
                "ORDER BY e.collection_date DESC";
        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet x = s.executeQuery(q)) {
            while (x.next()) {
                String fPath = x.getString(7);
                String fName = fPath != null && !fPath.isBlank() ? Path.of(fPath).getFileName().toString() : "";
                r.add(new String[]{
                        x.getString(1),
                        x.getString(2),
                        x.getString(3),
                        x.getString(4),
                        x.getString(5),
                        String.valueOf(x.getInt(6)),
                        fName,
                        x.getString(8) != null ? x.getString(8) : "",
                        x.getString(9) != null ? x.getString(9) : "SHA-256",
                        x.getString(10) != null ? x.getString(10) : "",
                        x.getString(11) != null ? x.getString(11) : ""
                });
            }
        }
        return r;
    }

    public static String computeSha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException("SHA-256 digest unavailable", ex);
        }
    }

    public static String computeFileSha256(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return computeSha256(bytes);
    }

    public static final class HashResult {
        public final String status; // VERIFIED_MATCH, INTEGRITY_MISMATCH, NO_FILE_ATTACHED, FILE_MISSING
        public final String storedHash;
        public final String computedHash;
        public final String fileName;
        public final Long fileSizeBytes;
        public final String message;
        public final String verifiedAt;

        public HashResult(String status, String storedHash, String computedHash, String fileName, Long fileSizeBytes, String message, String verifiedAt) {
            this.status = status;
            this.storedHash = storedHash;
            this.computedHash = computedHash;
            this.fileName = fileName;
            this.fileSizeBytes = fileSizeBytes;
            this.message = message;
            this.verifiedAt = verifiedAt;
        }
    }

    public HashResult verifyEvidenceHash(String evidenceId) throws SQLException {
        String sql = "SELECT file_path, file_hash_sha256, hash_algorithm, hash_verified_at, file_size_bytes FROM evidence WHERE id = ?";
        try (Connection c = Database.connect(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, evidenceId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) {
                    return new HashResult("NOT_FOUND", null, null, null, null, "Evidence record not found", null);
                }
                String filePath = r.getString("file_path");
                String storedHash = r.getString("file_hash_sha256");
                if (filePath == null || filePath.isBlank()) {
                    return new HashResult("NO_FILE_ATTACHED", null, null, null, null,
                            "Hash unavailable — no evidence file attached", null);
                }

                Path pth = Path.of(filePath);
                String fileName = pth.getFileName().toString();
                if (!Files.isRegularFile(pth)) {
                    return new HashResult("FILE_MISSING", storedHash, null, fileName, null,
                            "Evidence file not found on disk at registered path", null);
                }

                try {
                    byte[] bytes = Files.readAllBytes(pth);
                    long size = bytes.length;
                    String computed = computeSha256(bytes);

                    if (storedHash != null && storedHash.equalsIgnoreCase(computed)) {
                        String now = java.time.Instant.now().toString();
                        try (PreparedStatement u = c.prepareStatement("UPDATE evidence SET hash_verified_at = ?, file_size_bytes = ? WHERE id = ?")) {
                            u.setString(1, now);
                            u.setLong(2, size);
                            u.setString(3, evidenceId);
                            u.executeUpdate();
                        }
                        return new HashResult("VERIFIED_MATCH", storedHash, computed, fileName, size,
                                "Cryptographic hash verified: file matches recorded baseline SHA-256", now);
                    } else {
                        // Integrity mismatch! DO NOT OVERWRITE STORED HASH!
                        return new HashResult("INTEGRITY_MISMATCH", storedHash, computed, fileName, size,
                                "CRITICAL: Current file SHA-256 does not match recorded baseline hash!", null);
                    }
                } catch (IOException ioe) {
                    return new HashResult("READ_ERROR", storedHash, null, fileName, null,
                            "Error reading evidence file from disk: " + ioe.getMessage(), null);
                }
            }
        }
    }
}

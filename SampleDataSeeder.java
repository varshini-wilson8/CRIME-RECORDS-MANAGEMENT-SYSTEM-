import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/** Idempotent fictional development data for CRMS 2.0. */
public final class SampleDataSeeder {
 public static void seed() throws SQLException, IOException {
  try(Connection c=Database.connect(); Statement s=c.createStatement()) {
   if(c.createStatement().executeQuery("SELECT COUNT(*) FROM cases").next() && countCases(c)==0){
    s.executeUpdate("INSERT OR IGNORE INTO cases VALUES('C-2026-041','Riverside Gallery Break-in','Fictional overnight gallery break-in.', 'CRITICAL','UNDER_INVESTIGATION','2026-09-14','O1',46,'BURGLARY')");
    s.executeUpdate("INSERT OR IGNORE INTO cases VALUES('C-2026-09-02','Vendor Invoice Review','Fictional invoice irregularity review.', 'MEDIUM','COLD','2026-09-02','O3',38,'FRAUD')");
    s.executeUpdate("INSERT OR IGNORE INTO cases VALUES('C-2026-042','Missing Person Welfare Check','Fictional welfare-check investigation.', 'HIGH','OPEN','2026-09-17','O2',33,'MISSING_PERSON')");
    s.executeUpdate("INSERT OR IGNORE INTO cases VALUES('C-2026-043','Recovered Vehicle Report','Fictional recovered vehicle report.', 'LOW','CLOSED','2026-09-09','O1',21,'THEFT')");
    s.executeUpdate("INSERT OR IGNORE INTO suspects VALUES('S-1001','Jordan Taylor','555-0148','Medium build, dark jacket','MEDIUM',1)");
    s.executeUpdate("INSERT OR IGNORE INTO case_suspects VALUES('C-2026-041','S-1001')");
    s.executeUpdate("INSERT OR IGNORE INTO case_suspects VALUES('C-2026-042','S-1001')");
    s.executeUpdate("INSERT OR IGNORE INTO evidence(id,case_id,description,collection_date,current_custodian) VALUES('E-041-A','C-2026-041','Gallery entrance camera footage','2026-09-14','Digital Forensics Unit')");
    insertEvidenceLogIfMissing(c, "E-041-A", "2026-09-14T09:00:00", "COLLECTION", "COLLECTION", "Officer Raghav", "Initial collection");
    insertEvidenceLogIfMissing(c, "E-041-A", "2026-09-14T12:00:00", "TRANSFER", "Officer Raghav", "Digital Forensics Unit", "Submitted for authenticity review");

    // Physical evidence item with NULL file_path and unbroken custody history
    s.executeUpdate("INSERT OR IGNORE INTO evidence(id,case_id,description,collection_date,current_custodian) VALUES('E-041-B','C-2026-041','Exterior perimeter gate padlock fragment','2026-09-14','Property Room Vault')");
    insertEvidenceLogIfMissing(c, "E-041-B", "2026-09-14T09:30:00", "COLLECTION", "COLLECTION", "Officer Arjun", "Physical fragment recovered from perimeter gate");
    insertEvidenceLogIfMissing(c, "E-041-B", "2026-09-14T11:15:00", "TRANSFER", "Officer Arjun", "Property Room Vault", "Secured in physical property vault #4B");
   }
   if(hasCase(c,"C-2026-041")) {
    insertEvent(c,"C-2026-041","INCIDENT","Reported break-in at Riverside Gallery","Initial incident report recorded for the fictional case.","2026-09-14T18:47:00","Initial incident report","PENDING_REVIEW","REPORTED");
    insertEvent(c,"C-2026-041","OBSERVATION","Vehicle observed near gallery entrance","A vehicle was reported near the gallery entrance before the incident.","2026-09-14T18:32:00","Development scenario","PENDING_REVIEW","REPORTED");
    insertEvent(c,"C-2026-041","OBSERVATION","Person reported entering gallery","A person was reported entering the gallery shortly before the incident.","2026-09-14T18:41:00","Witness account","PENDING_REVIEW","REPORTED");
    insertEvent(c,"C-2026-041","SENSOR_ALERT","System recorded perimeter motion sensor trigger near rear loading bay","Automated security logging recorded a motion sensor pulse at rear loading bay (LOC-004). Fact status applies to the existence of the recorded sensor log.","2026-09-14T18:56:00","Security system log (LOC-004)","VERIFIED","FACT");
    insertEvent(c,"C-2026-041","EVIDENCE_REVIEW","CCTV evidence submitted for review","Gallery entrance footage is available for authenticity review.","2026-09-14T20:00:00","Evidence E-041-A","VERIFIED","FACT");
   }
   if(hasCase(c,"C-2026-042")) {
    insertEvent(c,"C-2026-042","CASE_OPENED","Welfare-check case opened","Initial welfare-check record created.","2026-09-17T09:00:00","Case intake","VERIFIED","FACT");
   }
  }
  // Fresh development data is created after the initial schema bootstrap, so migrate it too.
  Database.migrateLegacy();
  seedDevelopmentVehicle();
  linkDevelopmentPeopleToEvents();
  seedDevelopmentLocations();
  linkDevelopmentLocationsToEvents();
  seedDevelopmentTasks();
  seedDevelopmentFindings();
  seedEvidenceFixture();
  seedDevelopmentHypotheses();
  seedDemoCase044();
 }

 private static void seedDevelopmentLocations() throws SQLException {
  try(Connection c=Database.connect(); Statement s=c.createStatement()){
   s.executeUpdate("INSERT OR IGNORE INTO locations(id,name,address,city,state,country,latitude,longitude,location_type) VALUES('LOC-001','Riverside Gallery','44 Victoria Embankment, Riverside Precinct','Chennai','Tamil Nadu','India',13.0827,80.2707,'COMMERCIAL')");
   s.executeUpdate("INSERT OR IGNORE INTO locations(id,name,address,city,state,country,latitude,longitude,location_type) VALUES('LOC-002','Central Station Terminal','Station Road, North District','Chennai','Tamil Nadu','India',13.0800,80.2750,'PUBLIC_SPACE')");
   s.executeUpdate("INSERT OR IGNORE INTO locations(id,name,address,city,state,country,latitude,longitude,location_type) VALUES('LOC-003','North Overpass Junction','Harbor Express Highway, KM 4','Chennai','Tamil Nadu','India',13.0890,80.2800,'VEHICLE_STOP')");
   s.executeUpdate("INSERT OR IGNORE INTO locations(id,name,address,city,state,country,latitude,longitude,location_type) VALUES('LOC-004','Victoria Warehouse Complex','12 Port Road, Riverside Precinct','Chennai','Tamil Nadu','India',13.0845,80.2730,'COMMERCIAL')");

   s.executeUpdate("INSERT OR IGNORE INTO case_locations(case_id,location_id,relationship_type,notes) VALUES('C-2026-041','LOC-001','INCIDENT_LOCATION','Primary scene of reported break-in at gallery vault')");
   s.executeUpdate("INSERT OR IGNORE INTO case_locations(case_id,location_id,relationship_type,notes) VALUES('C-2026-041','LOC-004','EVIDENCE_LOCATION','Suspect vehicle spotted parked nearby warehouse loading bay')");
   s.executeUpdate("INSERT OR IGNORE INTO case_locations(case_id,location_id,relationship_type,notes) VALUES('C-2026-042','LOC-002','INCIDENT_LOCATION','Subject last observed near east terminal ticket counter')");
   s.executeUpdate("INSERT OR IGNORE INTO case_locations(case_id,location_id,relationship_type,notes) VALUES('C-2026-043','LOC-003','INCIDENT_LOCATION','Abandoned vehicle recovered on shoulder of overpass')");
   s.executeUpdate("INSERT OR IGNORE INTO case_locations(case_id,location_id,relationship_type,notes) VALUES('C-2026-043','LOC-004','OTHER','Warehouse security reported similar vehicle idling on prior day')");
  }
 }

 private static void linkDevelopmentLocationsToEvents() throws SQLException {
  try(Connection c=Database.connect()){
   try(PreparedStatement p=c.prepareStatement("UPDATE case_events SET location_id=? WHERE case_id=? AND title=?")){
    p.setString(1,"LOC-004"); p.setString(2,"C-2026-041"); p.setString(3,"System recorded perimeter motion sensor trigger near rear loading bay"); p.executeUpdate();
   }
   try(PreparedStatement p=c.prepareStatement("UPDATE case_events SET location_id=? WHERE case_id=? AND location_id IS NULL")){
    p.setString(1,"LOC-001"); p.setString(2,"C-2026-041"); p.executeUpdate();
   }
   try(PreparedStatement p=c.prepareStatement("UPDATE case_events SET location_id=? WHERE case_id=? AND location_id IS NULL")){
    p.setString(1,"LOC-002"); p.setString(2,"C-2026-042"); p.executeUpdate();
   }
  }
 }

 private static void seedDevelopmentVehicle() throws SQLException {
  try(Connection c=Database.connect(); Statement s=c.createStatement()){
   s.executeUpdate("INSERT OR IGNORE INTO vehicles(id,registration_number,make,model,vehicle_type,color,owner_person_id) VALUES('V-041','TN-01-CR-2041','Toyota','Corolla','CAR','Silver','S-1001')");
   s.executeUpdate("INSERT OR IGNORE INTO case_vehicles(case_id,vehicle_id,relationship_type,notes) VALUES('C-2026-041','V-041','SEEN_AT_SCENE','Vehicle reported near gallery entrance in development scenario.')");
  }
 }

 private static void linkDevelopmentPeopleToEvents() throws SQLException {
  try(Connection c=Database.connect(); PreparedStatement p=c.prepareStatement("UPDATE case_events SET person_id=? WHERE case_id=? AND title=? AND person_id IS NULL")){
   p.setString(1,"S-1001"); p.setString(2,"C-2026-041"); p.setString(3,"Person reported entering gallery"); p.executeUpdate();
   p.setString(3,"Vehicle observed near gallery entrance"); p.executeUpdate();
  }
 }

 private static void seedDevelopmentTasks() throws SQLException {
  try(Connection c=Database.connect()){
   insertTask(c,"C-2026-041","Verify gallery CCTV evidence","Confirm authenticity, time alignment and completeness of the gallery entrance footage.","O1","HIGH","2026-09-18");
   insertTask(c,"C-2026-041","Review witness identification and interview status","Check whether the reported person entering the gallery has been followed up with witness identification/interview work.","O2","MEDIUM","2026-09-19");
   insertTask(c,"C-2026-041","Document scene observations","Record verified observations from the gallery scene and distinguish them from reported information.","O1","MEDIUM","2026-09-20");
  }
 }

 private static void insertTask(Connection c,String caseId,String title,String desc,String officer,String priority,String due)throws SQLException{
  String check="SELECT 1 FROM investigation_tasks WHERE case_id=? AND title=?";
  try(PreparedStatement q=c.prepareStatement(check)){q.setString(1,caseId);q.setString(2,title);try(ResultSet r=q.executeQuery()){if(r.next())return;}}
  String sql="INSERT INTO investigation_tasks(case_id,title,description,assigned_to,priority,status,due_date) VALUES(?,?,?,?,?,'PENDING',?)";
  try(PreparedStatement p=c.prepareStatement(sql)){p.setString(1,caseId);p.setString(2,title);p.setString(3,desc);p.setString(4,officer);p.setString(5,priority);p.setString(6,due);p.executeUpdate();}
 }

 private static void seedDevelopmentFindings() throws SQLException {
  try(Connection c=Database.connect()){
   long f1=findOrCreateFinding(c,"C-2026-041","CCTV footage is available for authenticity review","The gallery entrance footage has been submitted for review and is currently the main recorded evidence item.","FACT");
   long f2=findOrCreateFinding(c,"C-2026-041","Reported person entry requires corroboration","A person was reported entering the gallery shortly before the incident; corroborating evidence should be reviewed before drawing conclusions.","LEAD");
   linkFinding(c,f1,"E-041-A","CONTEXT","CCTV is the source referenced by the evidence review event.");
   linkFinding(c,f2,"E-041-A","SUPPORTS","Review whether the footage corroborates the reported entry time and description.");
  }
 }

 private static void seedEvidenceFixture() {
  try {
   Path dir = Path.of("data", "evidence");
   Files.createDirectories(dir);
   Path fixture = dir.resolve("E-041-A_demo_cctv_frame.dat");
   if (!Files.exists(fixture)) {
    String content = "[DEMO/TEST DATA - FICTIONAL EVIDENCE FIXTURE FOR CRMS INTEGRITY TESTING]\n" +
            "Evidence ID: E-041-A\n" +
            "Case ID: C-2026-041\n" +
            "Device: Gallery Entrance Camera 01 (Digital Frame Export)\n" +
            "Capture Window: 2026-09-14T18:30:00 to 18:45:00\n" +
            "Classification: Fictional demonstration fixture for technical integrity verification\n";
    Files.writeString(fixture, content);
   }
   String hash = EvidenceDao.computeFileSha256(fixture);
   long sz = Files.size(fixture);
   String now = java.time.Instant.now().toString();

   try (Connection c = Database.connect();
        PreparedStatement p = c.prepareStatement("UPDATE evidence SET file_path=?, file_hash_sha256=?, hash_algorithm='SHA-256', hash_verified_at=?, file_size_bytes=? WHERE id='E-041-A' AND (file_path IS NULL OR file_path='')")) {
    p.setString(1, fixture.toString());
    p.setString(2, hash);
    p.setString(3, now);
    p.setLong(4, sz);
    p.executeUpdate();
   }
   try (Connection c = Database.connect()) {
    try (Statement st = c.createStatement()) {
     st.executeUpdate("INSERT OR IGNORE INTO evidence(id,case_id,description,collection_date,current_custodian) " +
             "VALUES('E-041-B','C-2026-041','Exterior perimeter gate padlock fragment','2026-09-14','Property Room Vault')");
    }
    insertEvidenceLogIfMissing(c, "E-041-B", "2026-09-14T09:30:00", "COLLECTION", "COLLECTION", "Officer Arjun", "Physical fragment recovered from perimeter gate");
    insertEvidenceLogIfMissing(c, "E-041-B", "2026-09-14T11:15:00", "TRANSFER", "Officer Arjun", "Property Room Vault", "Secured in physical property vault #4B");
   }
  } catch (Exception ex) {
   System.err.println("Warning: could not seed evidence fixture: " + ex.getMessage());
  }
 }

 private static void seedDevelopmentHypotheses() throws SQLException {
  try(Connection c=Database.connect()){
   long h1 = findOrCreateHypothesis(c, "C-2026-041", "H1", "External intruder entry through front gallery entrance",
           "Investigates possibility of entry via main gallery front perimeter during recorded observation window.");
   long h2 = findOrCreateHypothesis(c, "C-2026-041", "H2", "Intruder entry/activity via rear loading bay",
           "Investigates whether perimeter sensor trigger at rear loading bay correlates with scene access.");
   long h3 = findOrCreateHypothesis(c, "C-2026-041", "H3", "Incident time precedes recorded entrance observations",
           "Analytical proposition evaluating whether unauthorized entry occurred prior to recorded 18:30 camera window. Alternative timing hypothesis for investigator review.");

   // Link E-041-A using only facts in the database:
   // H1 -> CONTEXT: footage covers entrance area around reported time; requires visual confirmation
   // H2 -> CONTEXT: front camera does not cover rear loading bay sensor trigger location
   // H3 -> CONTRADICTS: camera shows routine operational window at 18:30-18:45 without visible disturbance at front door, challenging pre-18:30 front breach; does not establish actual incident timing
   linkHypothesisEvidence(c, h1, "E-041-A", "CONTEXT", "Front camera footage covers entrance area around reported time; requires visual review.");
   linkHypothesisEvidence(c, h2, "E-041-A", "CONTEXT", "Front camera does not cover rear loading bay area where perimeter sensor triggered.");
   linkHypothesisEvidence(c, h3, "E-041-A", "CONTRADICTS", "Front camera shows routine operational window at 18:30-18:45 without visible breach, challenging pre-18:30 front entry hypothesis; discovery at 18:47 does not prove actual incident timing.");
  }
 }

 private static long findOrCreateHypothesis(Connection c, String caseId, String code, String title, String desc) throws SQLException {
  try (PreparedStatement q = c.prepareStatement("SELECT id FROM investigation_hypotheses WHERE case_id=? AND hypothesis_code=?")) {
   q.setString(1, caseId);
   q.setString(2, code);
   try (ResultSet r = q.executeQuery()) {
    if (r.next()) return r.getLong(1);
   }
  }
  try (PreparedStatement p = c.prepareStatement(
          "INSERT INTO investigation_hypotheses(case_id, hypothesis_code, title, description, status, created_by) VALUES(?,?,?,?,'ACTIVE',NULL)",
          Statement.RETURN_GENERATED_KEYS)) {
   p.setString(1, caseId);
   p.setString(2, code);
   p.setString(3, title);
   p.setString(4, desc);
   p.executeUpdate();
   try (ResultSet r = p.getGeneratedKeys()) {
    return r.next() ? r.getLong(1) : 0;
   }
  }
 }

 private static void linkHypothesisEvidence(Connection c, long hypothesisId, String evidenceId, String rel, String notes) throws SQLException {
  String sql = "INSERT INTO hypothesis_evidence(hypothesis_id, evidence_id, relationship_type, notes, created_by, updated_at) " +
          "VALUES(?,?,?,?,NULL,CURRENT_TIMESTAMP) " +
          "ON CONFLICT(hypothesis_id, evidence_id) DO UPDATE SET relationship_type=excluded.relationship_type, notes=excluded.notes";
  try (PreparedStatement p = c.prepareStatement(sql)) {
   p.setLong(1, hypothesisId);
   p.setString(2, evidenceId);
   p.setString(3, rel);
   p.setString(4, notes);
   p.executeUpdate();
  }
 }

 private static long findOrCreateFinding(Connection c,String caseId,String title,String desc,String type)throws SQLException{
  try(PreparedStatement q=c.prepareStatement("SELECT id FROM investigation_findings WHERE case_id=? AND title=?")){q.setString(1,caseId);q.setString(2,title);try(ResultSet r=q.executeQuery()){if(r.next())return r.getLong(1);}}
  try(PreparedStatement p=c.prepareStatement("INSERT INTO investigation_findings(case_id,title,description,type,status) VALUES(?,?,?,?, 'OPEN')",Statement.RETURN_GENERATED_KEYS)){p.setString(1,caseId);p.setString(2,title);p.setString(3,desc);p.setString(4,type);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){return r.next()?r.getLong(1):0;}}
 }
 private static void linkFinding(Connection c,long findingId,String evidenceId,String rel,String notes)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO finding_evidence(finding_id,evidence_id,relationship,notes) VALUES(?,?,?,?)")){p.setLong(1,findingId);p.setString(2,evidenceId);p.setString(3,rel);p.setString(4,notes);p.executeUpdate();}}
 private static int countCases(Connection c)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM cases");ResultSet r=p.executeQuery()){return r.next()?r.getInt(1):0;}}
 private static boolean hasCase(Connection c,String id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM cases WHERE id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){return r.next();}}}

 private static void insertEvent(Connection c,String caseId,String type,String title,String desc,String time,String source,String status,String truthLevel)throws SQLException{
  String check="SELECT id FROM case_events WHERE case_id=? AND title=?";
  try(PreparedStatement q=c.prepareStatement(check)){
   q.setString(1,caseId);q.setString(2,title);
   try(ResultSet r=q.executeQuery()){
    if(r.next()){
     long id = r.getLong(1);
     try (PreparedStatement u = c.prepareStatement("UPDATE case_events SET truth_level=?, event_time=?, source=?, verification_status=? WHERE id=?")) {
      u.setString(1, truthLevel); u.setString(2, time); u.setString(3, source); u.setString(4, status); u.setLong(5, id);
      u.executeUpdate();
     }
     return;
    }
   }
  }
  String sql="INSERT INTO case_events(case_id,event_type,title,description,event_time,source,verification_status,truth_level) VALUES(?,?,?,?,?,?,?,?)";
  try(PreparedStatement p=c.prepareStatement(sql)){
   p.setString(1,caseId);p.setString(2,type);p.setString(3,title);p.setString(4,desc);p.setString(5,time);p.setString(6,source);p.setString(7,status);p.setString(8,truthLevel);
   p.executeUpdate();
  }
 }

  public static void seedDemoCase044() throws SQLException, IOException {
   // 1. Generate real demonstration fixtures and compute genuine SHA-256 digests
   Path dir = Path.of("data", "evidence");
   Files.createDirectories(dir);
   Path fixtureA = dir.resolve("E-044-A_demo_cctv_export.dat");
   if (!Files.exists(fixtureA)) {
    String contentA = "[DEMO/TEST DATA - FICTIONAL EVIDENCE FIXTURE FOR CRMS INTEGRITY TESTING]\n" +
            "Evidence ID: E-044-A\n" +
            "Case ID: C-2026-044\n" +
            "Device: Warehouse Entrance Camera 02 (Export Stream)\n" +
            "Capture Window: 2026-03-20T20:20:00 to 2026-03-20T20:50:00\n" +
            "Classification: Fictional demonstration fixture for technical integrity verification\n";
    Files.writeString(fixtureA, contentA);
   }
   String hashA = EvidenceDao.computeFileSha256(fixtureA);
   long sizeA = Files.size(fixtureA);

   Path fixtureB = dir.resolve("E-044-B_demo_access_log.dat");
   if (!Files.exists(fixtureB)) {
    String contentB = "[DEMO/TEST DATA - FICTIONAL EVIDENCE FIXTURE FOR CRMS INTEGRITY TESTING]\n" +
            "Evidence ID: E-044-B\n" +
            "Case ID: C-2026-044\n" +
            "System: Warehouse Electronic Access Control Log\n" +
            "Export Window: 2026-03-20T20:00:00 to 2026-03-20T21:00:00\n" +
            "Classification: Fictional demonstration fixture for technical integrity verification\n" +
            "Events:\n" +
            "20:10:00 - Card Reader 01 (Front Entrance) - Employee Badge #4021 - ACCESS_GRANTED\n" +
            "20:32:00 - Card Reader 04 (Rear Service Entrance) - Emergency Push Bar Active - SENSOR_TRIPPED\n";
    Files.writeString(fixtureB, contentB);
   }
   String hashB = EvidenceDao.computeFileSha256(fixtureB);
   long sizeB = Files.size(fixtureB);

   // 2. Perform all database operations within a strict ACID transaction with foreign_keys enabled
   try (Connection c = Database.connect()) {
    try (Statement s = c.createStatement()) { s.execute("PRAGMA foreign_keys = ON"); }
    c.setAutoCommit(false);
    try {
     // A. Primary Case Record
     String caseSql = "INSERT OR IGNORE INTO cases (id, title, description, severity, status, opening_date, assigned_officer_id, priority, crime_type) " +
             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
     try (PreparedStatement p = c.prepareStatement(caseSql)) {
      p.setString(1, "C-2026-044");
      p.setString(2, "Harbor Point Warehouse Break-in");
      p.setString(3, "DEMO/TEST DATA — Reported unauthorized entry into a warehouse during evening operating hours. Several electronic inventory items were reported missing. Available records include access-control logs, CCTV footage, witness observations, and a recovered physical item. Investigation remains open pending verification and corroboration.");
      p.setString(4, "HIGH");
      p.setString(5, "UNDER_INVESTIGATION");
      p.setString(6, "2026-03-20");
      p.setString(7, "O3");
      p.setInt(8, 48);
      p.setString(9, "BURGLARY");
      p.executeUpdate();
     }

     // B. Location and Case-Location link
     String locSql = "INSERT OR IGNORE INTO locations (id, name, address, city, state, country, latitude, longitude, location_type) " +
             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
     try (PreparedStatement p = c.prepareStatement(locSql)) {
      p.setString(1, "LOC-005");
      p.setString(2, "Harbor Point Logistics Warehouse");
      p.setString(3, "108 Harbor Road, Port Zone");
      p.setString(4, "Chennai");
      p.setString(5, "Tamil Nadu");
      p.setString(6, "India");
      p.setDouble(7, 13.0910);
      p.setDouble(8, 80.2850);
      p.setString(9, "COMMERCIAL");
      p.executeUpdate();
     }
     try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO case_locations (case_id, location_id, relationship_type, notes) VALUES (?, ?, ?, ?)")) {
      p.setString(1, "C-2026-044");
      p.setString(2, "LOC-005");
      p.setString(3, "INCIDENT_LOCATION");
      p.setString(4, "Primary scene of reported warehouse break-in");
      p.executeUpdate();
     }

     // C. People & Suspect Registry
     try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO persons (id, first_name, last_name, phone, physical_description) VALUES (?, ?, ?, ?, ?)")) {
      p.setString(1, "S-1002");
      p.setString(2, "Alex");
      p.setString(3, "Morgan");
      p.setString(4, "555-0192");
      p.setString(5, "Slim build, light jacket");
      p.executeUpdate();
     }
     try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO suspects (id, name, contact, physical_description, risk_level, repeat_offender) VALUES (?, ?, ?, ?, ?, ?)")) {
      p.setString(1, "S-1002");
      p.setString(2, "Alex Morgan");
      p.setString(3, "555-0192");
      p.setString(4, "Slim build, light jacket");
      p.setString(5, "LOW");
      p.setInt(6, 0);
      p.executeUpdate();
     }
     try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO persons (id, first_name, last_name, phone, physical_description) VALUES (?, ?, ?, ?, ?)")) {
      p.setString(1, "P-1003");
      p.setString(2, "Priya");
      p.setString(3, "Raman");
      p.setString(4, "555-0234");
      p.setString(5, "Warehouse perimeter eyewitness");
      p.executeUpdate();
     }

     // Case People links
     try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO case_persons (case_id, person_id, role, notes) VALUES (?, ?, ?, ?)")) {
      p.setString(1, "C-2026-044");
      p.setString(2, "S-1001");
      p.setString(3, "PERSON_OF_INTEREST");
      p.setString(4, "Alibi: Claims to have been at a nearby restaurant | Alibi status: PENDING_VERIFICATION");
      p.executeUpdate();

      p.setString(1, "C-2026-044");
      p.setString(2, "S-1002");
      p.setString(3, "PERSON_OF_INTEREST");
      p.setString(4, "Alibi: Claims to have been working at another location | Alibi status: PENDING_VERIFICATION");
      p.executeUpdate();

      p.setString(1, "C-2026-044");
      p.setString(2, "P-1003");
      p.setString(3, "WITNESS");
      p.setString(4, "DEMO/TEST DATA — Witness reported seeing an unfamiliar person near the warehouse entrance around 20:35. Verification: PENDING_REVIEW");
      p.executeUpdate();
     }

     // Case Suspects links
     try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO case_suspects (case_id, suspect_id) VALUES (?, ?)")) {
      p.setString(1, "C-2026-044");
      p.setString(2, "S-1001");
      p.executeUpdate();

      p.setString(1, "C-2026-044");
      p.setString(2, "S-1002");
      p.executeUpdate();
     }

     // D. Evidence
     String evUpsert = "INSERT INTO evidence (id, case_id, description, collection_date, current_custodian, file_path, file_hash_sha256, hash_algorithm, hash_verified_at, file_size_bytes) " +
             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
             "ON CONFLICT(id) DO UPDATE SET file_path=excluded.file_path, file_hash_sha256=excluded.file_hash_sha256, hash_verified_at=excluded.hash_verified_at, file_size_bytes=excluded.file_size_bytes";
     try (PreparedStatement p = c.prepareStatement(evUpsert)) {
      // E-044-A (Digital CCTV)
      p.setString(1, "E-044-A");
      p.setString(2, "C-2026-044");
      p.setString(3, "Warehouse Entrance CCTV Recording — DEMO/TEST DATA: CCTV recording covering the main warehouse entrance between 20:20 and 20:50.");
      p.setString(4, "2026-03-20");
      p.setString(5, "Digital Forensics Unit");
      p.setString(6, fixtureA.toString());
      p.setString(7, hashA);
      p.setString(8, "SHA-256");
      p.setString(9, "2026-03-20T22:30:00");
      p.setLong(10, sizeA);
      p.executeUpdate();

      // E-044-B (Digital Access Log)
      p.setString(1, "E-044-B");
      p.setString(2, "C-2026-044");
      p.setString(3, "Electronic Access Control Log — DEMO/TEST DATA: Exported access-control records for the warehouse entrance.");
      p.setString(4, "2026-03-20");
      p.setString(5, "Digital Forensics Unit");
      p.setString(6, fixtureB.toString());
      p.setString(7, hashB);
      p.setString(8, "SHA-256");
      p.setString(9, "2026-03-20T22:30:00");
      p.setLong(10, sizeB);
      p.executeUpdate();
     }

     // E-044-C (Physical) & E-044-D (Document)
     try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO evidence (id, case_id, description, collection_date, current_custodian, file_path, file_hash_sha256) VALUES (?, ?, ?, ?, ?, NULL, NULL)")) {
      p.setString(1, "E-044-C");
      p.setString(2, "C-2026-044");
      p.setString(3, "Recovered Key Fragment — DEMO/TEST DATA: Small metal key fragment recovered near the rear service entrance.");
      p.setString(4, "2026-03-20");
      p.setString(5, "Property Room Vault");
      p.executeUpdate();

      p.setString(1, "E-044-D");
      p.setString(2, "C-2026-044");
      p.setString(3, "Witness Statement – Priya Raman — DEMO/TEST DATA: Witness statement concerning an unfamiliar person observed near the warehouse entrance.");
      p.setString(4, "2026-03-20");
      p.setString(5, "Officer Arjun Kumar");
      p.executeUpdate();
     }

     // Evidence Custody Logs
     insertEvidenceLogIfMissing(c, "E-044-A", "2026-03-20T21:35:00", "COLLECTION", "COLLECTION", "Officer Arjun Kumar", "Initial export retrieval from entrance CCTV console");
     insertEvidenceLogIfMissing(c, "E-044-A", "2026-03-20T22:30:00", "TRANSFER", "Officer Arjun Kumar", "Digital Forensics Unit", "Submitted for forensic hash calculation and storage");

     insertEvidenceLogIfMissing(c, "E-044-B", "2026-03-20T21:40:00", "COLLECTION", "COLLECTION", "Officer Arjun Kumar", "Server export of electronic access control log");
     insertEvidenceLogIfMissing(c, "E-044-B", "2026-03-20T22:30:00", "TRANSFER", "Officer Arjun Kumar", "Digital Forensics Unit", "Submitted for timeline audit correlation");

     insertEvidenceLogIfMissing(c, "E-044-C", "2026-03-20T21:20:00", "COLLECTION", "COLLECTION", "Officer Arjun Kumar", "Small metal key fragment recovered near rear service entrance");
     insertEvidenceLogIfMissing(c, "E-044-C", "2026-03-20T22:45:00", "TRANSFER", "Officer Arjun Kumar", "Property Room Vault", "Physical item secured in property vault #12");

     insertEvidenceLogIfMissing(c, "E-044-D", "2026-03-20T21:15:00", "COLLECTION", "COLLECTION", "Officer Arjun Kumar", "Witness statement recorded from Priya Raman at scene");

     // E. Timeline Events (10 events)
     insertEventFull(c, "C-2026-044", "OBSERVATION", "Employee access recorded at warehouse", "Electronic card access logged for authorized employee at front entrance.", "2026-03-20T20:10:00", "LOC-005", null, null, "Electronic access control log", "VERIFIED", "FACT");
     insertEventFull(c, "C-2026-044", "OBSERVATION", "Vehicle reported near loading entrance", "Vehicle observed stationary near warehouse loading entrance.", "2026-03-20T20:25:00", "LOC-005", null, null, "Witness account", "PENDING_REVIEW", "REPORTED");
     insertEventFull(c, "C-2026-044", "ACCESS_ALERT", "Access-control record shows rear-door activity", "System records show access-control activity near the rear entrance at 20:32.", "2026-03-20T20:32:00", "LOC-005", null, "E-044-B", "Access Control System (E-044-B)", "VERIFIED", "FACT");
     insertEventFull(c, "C-2026-044", "WITNESS_STATEMENT", "Witness reports unfamiliar person near entrance", "Witness reported observing an unfamiliar person near the entrance around 20:35.", "2026-03-20T20:35:00", "LOC-005", "P-1003", "E-044-D", "Witness statement (E-044-D)", "PENDING_REVIEW", "REPORTED");
     insertEventFull(c, "C-2026-044", "EVIDENCE_REVIEW", "CCTV records movement near warehouse entrance", "Available CCTV records movement near warehouse entrance window.", "2026-03-20T20:41:00", "LOC-005", null, "E-044-A", "CCTV export (E-044-A)", "VERIFIED", "FACT");
     insertEventFull(c, "C-2026-044", "ALARM", "Warehouse alarm event recorded", "Internal warehouse sensor registered perimeter intrusion alarm event.", "2026-03-20T20:46:00", "LOC-005", null, null, "Perimeter security alarm", "VERIFIED", "FACT");
     insertEventFull(c, "C-2026-044", "INCIDENT", "Employee reports missing inventory", "Warehouse supervisor reported electronic inventory missing from bay.", "2026-03-20T20:49:00", "LOC-005", null, null, "Supervisor report", "PENDING_REVIEW", "REPORTED");
     insertEventFull(c, "C-2026-044", "OFFICER_DISPATCH", "Officer arrives and begins initial inspection", "Investigating officer arrived at warehouse scene and commenced inspection.", "2026-03-20T21:05:00", "LOC-005", null, null, "Officer dispatch log", "VERIFIED", "FACT");
     insertEventFull(c, "C-2026-044", "EVIDENCE_COLLECTION", "Key fragment recovered near rear entrance", "A metal key fragment was recorded as recovered near the rear service entrance.", "2026-03-20T21:20:00", "LOC-005", null, "E-044-C", "Officer Arjun Kumar", "VERIFIED", "FACT");
     insertEventFull(c, "C-2026-044", "EVIDENCE_COLLECTION", "CCTV evidence submitted to evidence register", "Entrance CCTV export submitted and hashed into evidence register.", "2026-03-20T21:35:00", "LOC-005", null, "E-044-A", "Digital Forensics Unit", "VERIFIED", "FACT");

     // F. Investigation Tasks (6 tasks)
     insertTaskFull(c, "C-2026-044", "Verify CCTV footage", "Review available CCTV footage for the warehouse entrance and document observations.", "O3", "HIGH", "PENDING", "2026-03-24");
     insertTaskFull(c, "C-2026-044", "Verify access-control records", "Compare recorded access events with known personnel access.", "O3", "HIGH", "IN_PROGRESS", "2026-03-23");
     insertTaskFull(c, "C-2026-044", "Follow up with witness", "Obtain clarification regarding the reported person observed near the entrance.", "O3", "MEDIUM", "PENDING", "2026-03-25");
     insertTaskFull(c, "C-2026-044", "Verify alibi – Jordan Taylor", "Review available records relevant to the stated alibi.", "O3", "MEDIUM", "PENDING", "2026-03-26");
     insertTaskFull(c, "C-2026-044", "Verify alibi – Alex Morgan", "Review available records relevant to the stated alibi.", "O3", "MEDIUM", "PENDING", "2026-03-26");
     insertTaskFull(c, "C-2026-044", "Process recovered key fragment", "Document custody and arrange appropriate examination of the recovered physical item.", "O3", "HIGH", "PENDING", "2026-03-24");

     // G. Investigation Findings (4 findings)
     long f1 = findOrCreateFinding(c, "C-2026-044", "Access activity recorded near rear entrance", "System records show access-control activity near the rear entrance at 20:32.", "FACT");
     linkFinding(c, f1, "E-044-B", "CONTEXT", "Access control export E-044-B corroborates rear-door sensor activity.");

     long f2 = findOrCreateFinding(c, "C-2026-044", "Person reported near warehouse entrance", "Witness reported observing an unfamiliar person near the entrance around 20:35.", "LEAD");
     linkFinding(c, f2, "E-044-D", "SUPPORTS", "Witness statement E-044-D provides eyewitness account of individual near entrance.");

     long f3 = findOrCreateFinding(c, "C-2026-044", "CCTV requires detailed review", "Available CCTV should be reviewed to determine what movement is actually visible during the relevant period.", "LEAD");
     linkFinding(c, f3, "E-044-A", "CONTEXT", "Export file E-044-A contains entrance footage covering 20:20 to 20:50.");

     long f4 = findOrCreateFinding(c, "C-2026-044", "Physical item recovered near rear entrance", "A metal key fragment was recorded as recovered near the rear service entrance.", "FACT");
     linkFinding(c, f4, "E-044-C", "SUPPORTS", "Physical key fragment E-044-C was recovered directly adjacent to rear entrance.");

     // H. ACH Hypotheses (3 hypotheses)
     long h1 = findOrCreateHypothesis(c, "C-2026-044", "H1", "External entry through the main warehouse entrance",
             "The available records are consistent with a possibility that entry occurred through the main warehouse entrance.");
     linkHypothesisEvidence(c, h1, "E-044-A", "CONTEXT", "CCTV covers entrance window; requires detailed review to confirm whether entry occurred.");
     linkHypothesisEvidence(c, h1, "E-044-D", "SUPPORTS", "Witness statement reports unfamiliar person observed near main entrance at 20:35.");

     long h2 = findOrCreateHypothesis(c, "C-2026-044", "H2", "Activity occurred through the rear service entrance",
             "The recorded rear-door access event and recovered physical item warrant examination of the rear entrance as a possible activity location.");
     linkHypothesisEvidence(c, h2, "E-044-B", "SUPPORTS", "Access-control log recorded emergency push bar activity at rear service door at 20:32.");
     linkHypothesisEvidence(c, h2, "E-044-C", "CONTEXT", "Physical key fragment recovered near rear service entrance requires examination.");

     long h3 = findOrCreateHypothesis(c, "C-2026-044", "H3", "Reported incident timing differs from the actual unauthorized-entry timing",
             "The recorded discovery time and earlier system events do not by themselves establish the precise time unauthorized entry occurred.");
     linkHypothesisEvidence(c, h3, "E-044-A", "CONTEXT", "CCTV recording duration covers 20:20-20:50; does not eliminate earlier or later access.");
     linkHypothesisEvidence(c, h3, "E-044-B", "CONTEXT", "Access records establish discrete sensor trip events but not total occupancy duration.");

     // I. Explicit Entity Relationships
     insertRelationshipIfMissing(c, "PERSON", "P-1003", "EVIDENCE", "E-044-D", "WITNESS_STATEMENT", 1.0, "CONFIRMED", "Priya Raman is the recorded provider of witness statement E-044-D.");
     insertRelationshipIfMissing(c, "EVIDENCE", "E-044-C", "LOCATION", "LOC-005", "RECOVERED_AT", 1.0, "CONFIRMED", "Key fragment recovered near rear service entrance of warehouse.");
     insertRelationshipIfMissing(c, "PERSON", "S-1001", "CASE", "C-2026-044", "PERSON_OF_INTEREST", 0.8, "PENDING_REVIEW", "DEMO/TEST DATA — Proposed cross-case relationship surfaced from existing recorded data; requires investigator review.");

     // J. Audit Logs
     insertAuditIfMissing(c, "U-ADMIN", "CREATE_CASE", "CASE", "C-2026-044", "Created case record: Harbor Point Warehouse Break-in | CASE_ID=C-2026-044");
     insertAuditIfMissing(c, "U-ADMIN", "REGISTER_EVIDENCE", "EVIDENCE", "E-044-A", "Registered digital evidence: Warehouse Entrance CCTV Recording | CASE_ID=C-2026-044");
     insertAuditIfMissing(c, "U-ADMIN", "REGISTER_EVIDENCE", "EVIDENCE", "E-044-B", "Registered digital evidence: Electronic Access Control Log | CASE_ID=C-2026-044");
     insertAuditIfMissing(c, "U-ADMIN", "REGISTER_EVIDENCE", "EVIDENCE", "E-044-C", "Registered physical evidence: Recovered Key Fragment | CASE_ID=C-2026-044");
     insertAuditIfMissing(c, "U-ADMIN", "REGISTER_EVIDENCE", "EVIDENCE", "E-044-D", "Registered document evidence: Witness Statement – Priya Raman | CASE_ID=C-2026-044");
     insertAuditIfMissing(c, "U-ADMIN", "CREATE_TASK", "TASK", "C-2026-044-T1", "Created investigation task: Verify CCTV footage | CASE_ID=C-2026-044");
     insertAuditIfMissing(c, "U-ADMIN", "RECORD_FINDING", "FINDING", "C-2026-044-F1", "Recorded finding: Access activity recorded near rear entrance | CASE_ID=C-2026-044");
     insertAuditIfMissing(c, "U-ADMIN", "CREATE_HYPOTHESIS", "HYPOTHESIS", "C-2026-044-H1", "Formulated ACH hypothesis: External entry through main warehouse entrance | CASE_ID=C-2026-044");
     insertAuditIfMissing(c, "U-ADMIN", "LINK_ACH_EVIDENCE", "HYPOTHESIS_EVIDENCE", "H1:E-044-A", "Linked evidence E-044-A to H1 as CONTEXT | CASE_ID=C-2026-044");

     // K. Case Report Draft
     try (PreparedStatement p = c.prepareStatement(
             "INSERT INTO case_reports (case_id, report_type, title, content, status, created_by) " +
             "SELECT 'C-2026-044', 'INCIDENT_REPORT', 'Initial Incident Report - Harbor Point Warehouse', " +
             "'DEMO/TEST DATA — Preliminary summary of warehouse break-in at Harbor Point Logistics Warehouse on 2026-03-20. Physical and digital evidence secured for forensic analysis.', " +
             "'DRAFT', 'U-ADMIN' WHERE NOT EXISTS (SELECT 1 FROM case_reports WHERE case_id='C-2026-044')")) {
      p.executeUpdate();
     }

     c.commit();
    } catch (Exception ex) {
     c.rollback();
     throw ex;
    } finally {
     c.setAutoCommit(true);
    }
   }
  }

  private static void insertEventFull(Connection c, String caseId, String type, String title, String desc, String time, String locId, String personId, String evId, String source, String status, String truthLevel) throws SQLException {
   String check = "SELECT id FROM case_events WHERE case_id=? AND title=?";
   try (PreparedStatement q = c.prepareStatement(check)) {
    q.setString(1, caseId);
    q.setString(2, title);
    try (ResultSet r = q.executeQuery()) {
     if (r.next()) {
      long id = r.getLong(1);
      try (PreparedStatement u = c.prepareStatement(
              "UPDATE case_events SET event_type=?, description=?, event_time=?, location_id=?, person_id=?, evidence_id=?, source=?, verification_status=?, truth_level=? WHERE id=?")) {
       u.setString(1, type);
       u.setString(2, desc);
       u.setString(3, time);
       u.setString(4, locId);
       u.setString(5, personId);
       u.setString(6, evId);
       u.setString(7, source);
       u.setString(8, status);
       u.setString(9, truthLevel);
       u.setLong(10, id);
       u.executeUpdate();
      }
      return;
     }
    }
   }
   String sql = "INSERT INTO case_events(case_id, event_type, title, description, event_time, location_id, person_id, evidence_id, source, verification_status, truth_level) " +
           "VALUES(?,?,?,?,?,?,?,?,?,?,?)";
   try (PreparedStatement p = c.prepareStatement(sql)) {
    p.setString(1, caseId);
    p.setString(2, type);
    p.setString(3, title);
    p.setString(4, desc);
    p.setString(5, time);
    p.setString(6, locId);
    p.setString(7, personId);
    p.setString(8, evId);
    p.setString(9, source);
    p.setString(10, status);
    p.setString(11, truthLevel);
    p.executeUpdate();
   }
  }

  private static void insertTaskFull(Connection c, String caseId, String title, String desc, String officer, String priority, String status, String due) throws SQLException {
   String check = "SELECT id FROM investigation_tasks WHERE case_id=? AND title=?";
   try (PreparedStatement q = c.prepareStatement(check)) {
    q.setString(1, caseId);
    q.setString(2, title);
    try (ResultSet r = q.executeQuery()) {
     if (r.next()) return;
    }
   }
   String sql = "INSERT INTO investigation_tasks(case_id, title, description, assigned_to, priority, status, due_date) VALUES(?,?,?,?,?,?,?)";
   try (PreparedStatement p = c.prepareStatement(sql)) {
    p.setString(1, caseId);
    p.setString(2, title);
    p.setString(3, desc);
    p.setString(4, officer);
    p.setString(5, priority);
    p.setString(6, status);
    p.setString(7, due);
    p.executeUpdate();
   }
  }

  private static void insertEvidenceLogIfMissing(Connection c, String evidenceId, String timestamp, String actionType, String fromCust, String toCust, String remarks) throws SQLException {
   String check = "SELECT 1 FROM evidence_logs WHERE evidence_id=? AND timestamp=? AND action_type=?";
   try (PreparedStatement q = c.prepareStatement(check)) {
    q.setString(1, evidenceId);
    q.setString(2, timestamp);
    q.setString(3, actionType);
    try (ResultSet r = q.executeQuery()) {
     if (r.next()) return;
    }
   }
   String sql = "INSERT INTO evidence_logs(evidence_id, timestamp, action_type, from_custodian, to_custodian, remarks) VALUES(?,?,?,?,?,?)";
   try (PreparedStatement p = c.prepareStatement(sql)) {
    p.setString(1, evidenceId);
    p.setString(2, timestamp);
    p.setString(3, actionType);
    p.setString(4, fromCust);
    p.setString(5, toCust);
    p.setString(6, remarks);
    p.executeUpdate();
   }
  }

  private static void insertRelationshipIfMissing(Connection c, String sType, String sId, String tType, String tId, String rel, double conf, String status, String reason) throws SQLException {
   String check = "SELECT 1 FROM entity_relationships WHERE source_type=? AND source_id=? AND target_type=? AND target_id=? AND relationship_type=?";
   try (PreparedStatement q = c.prepareStatement(check)) {
    q.setString(1, sType); q.setString(2, sId); q.setString(3, tType); q.setString(4, tId); q.setString(5, rel);
    try (ResultSet r = q.executeQuery()) {
     if (r.next()) return;
    }
   }
   String sql = "INSERT INTO entity_relationships(source_type, source_id, target_type, target_id, relationship_type, confidence, status, reason) VALUES(?,?,?,?,?,?,?,?)";
   try (PreparedStatement p = c.prepareStatement(sql)) {
    p.setString(1, sType); p.setString(2, sId); p.setString(3, tType); p.setString(4, tId);
    p.setString(5, rel); p.setDouble(6, conf); p.setString(7, status); p.setString(8, reason);
    p.executeUpdate();
   }
  }

  private static void insertAuditIfMissing(Connection c, String userId, String action, String entityType, String entityId, String details) throws SQLException {
   String check = "SELECT 1 FROM audit_logs WHERE action=? AND entity_type=? AND entity_id=? AND details=?";
   try (PreparedStatement q = c.prepareStatement(check)) {
    q.setString(1, action); q.setString(2, entityType); q.setString(3, entityId); q.setString(4, details);
    try (ResultSet r = q.executeQuery()) {
     if (r.next()) return;
    }
   }
   String sql = "INSERT INTO audit_logs(user_id, action, entity_type, entity_id, details) VALUES(?,?,?,?,?)";
   try (PreparedStatement p = c.prepareStatement(sql)) {
    p.setString(1, userId); p.setString(2, action); p.setString(3, entityType); p.setString(4, entityId); p.setString(5, details);
    p.executeUpdate();
   }
  }
}

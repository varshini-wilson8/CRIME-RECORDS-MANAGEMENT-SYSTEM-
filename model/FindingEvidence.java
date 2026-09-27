package model;
public final class FindingEvidence {
 private final long id, findingId; private final String evidenceId, evidenceDescription, relationship, notes, custodian;
 public FindingEvidence(long id,long findingId,String evidenceId,String evidenceDescription,String relationship,String notes,String custodian){this.id=id;this.findingId=findingId;this.evidenceId=evidenceId;this.evidenceDescription=evidenceDescription;this.relationship=relationship;this.notes=notes;this.custodian=custodian;}
 public long getId(){return id;} public long getFindingId(){return findingId;} public String getEvidenceId(){return evidenceId;} public String getEvidenceDescription(){return evidenceDescription;} public String getRelationship(){return relationship;} public String getNotes(){return notes;} public String getCustodian(){return custodian;}
}

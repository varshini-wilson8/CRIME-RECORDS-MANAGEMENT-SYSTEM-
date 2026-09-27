package model;
public final class InvestigationFinding {
 private final long id; private final String caseId,title,description,type,status,createdBy,createdAt;
 public InvestigationFinding(long id,String caseId,String title,String description,String type,String status,String createdBy,String createdAt){this.id=id;this.caseId=caseId;this.title=title;this.description=description;this.type=type;this.status=status;this.createdBy=createdBy;this.createdAt=createdAt;}
 public long getId(){return id;} public String getCaseId(){return caseId;} public String getTitle(){return title;} public String getDescription(){return description;} public String getType(){return type;} public String getStatus(){return status;} public String getCreatedBy(){return createdBy;} public String getCreatedAt(){return createdAt;}
}

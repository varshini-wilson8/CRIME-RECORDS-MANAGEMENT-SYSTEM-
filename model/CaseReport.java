package model;
public class CaseReport {
 private long id; private String caseId, reportType, title, content, status, createdBy, approvedBy, createdAt, updatedAt;
 public CaseReport(long id,String caseId,String reportType,String title,String content,String status,String createdBy,String approvedBy,String createdAt,String updatedAt){this.id=id;this.caseId=caseId;this.reportType=reportType;this.title=title;this.content=content;this.status=status;this.createdBy=createdBy;this.approvedBy=approvedBy;this.createdAt=createdAt;this.updatedAt=updatedAt;}
 public long getId(){return id;} public String getCaseId(){return caseId;} public String getReportType(){return reportType;} public String getTitle(){return title;} public String getContent(){return content;} public String getStatus(){return status;} public String getCreatedBy(){return createdBy;} public String getApprovedBy(){return approvedBy;} public String getCreatedAt(){return createdAt;} public String getUpdatedAt(){return updatedAt;}
}

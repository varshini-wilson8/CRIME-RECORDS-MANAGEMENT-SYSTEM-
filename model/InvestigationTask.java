package model;

public final class InvestigationTask {
    private final long id; private final String caseId,title,description,assignedTo,assignedOfficerName,priority,status,dueDate,completedAt;
    public InvestigationTask(long id,String caseId,String title,String description,String assignedTo,String assignedOfficerName,String priority,String status,String dueDate,String completedAt){this.id=id;this.caseId=caseId;this.title=title;this.description=description;this.assignedTo=assignedTo;this.assignedOfficerName=assignedOfficerName;this.priority=priority;this.status=status;this.dueDate=dueDate;this.completedAt=completedAt;}
    public long getId(){return id;} public String getCaseId(){return caseId;} public String getTitle(){return title;} public String getDescription(){return description;} public String getAssignedTo(){return assignedTo;} public String getAssignedOfficerName(){return assignedOfficerName;} public String getPriority(){return priority;} public String getStatus(){return status;} public String getDueDate(){return dueDate;} public String getCompletedAt(){return completedAt;}
}

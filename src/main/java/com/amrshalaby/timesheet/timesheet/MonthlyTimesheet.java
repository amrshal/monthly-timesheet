package com.amrshalaby.timesheet.timesheet;

import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import java.time.Instant;

@MappedEntity("monthly_timesheet")
public class MonthlyTimesheet {
 @Id @GeneratedValue private Long id; @MappedProperty("user_id") private Long userId; @MappedProperty("timesheet_year") private int year; @MappedProperty("timesheet_month") private int month;
 @MappedProperty @TypeDef(type=DataType.STRING) private TimesheetStatus status = TimesheetStatus.DRAFT;
 @MappedProperty("submitted_at") private Instant submittedAt; @MappedProperty("submitted_by_user_id") private Long submittedByUserId; @MappedProperty("approved_at") private Instant approvedAt; @MappedProperty("approved_by_user_id") private Long approvedByUserId;
 @DateCreated @MappedProperty("created_at") private Instant createdAt; @DateUpdated @MappedProperty("updated_at") private Instant updatedAt; @Version private Long version;
 public Long getId(){return id;} public void setId(Long id){this.id=id;} public Long getUserId(){return userId;} public void setUserId(Long userId){this.userId=userId;} public int getYear(){return year;} public void setYear(int year){this.year=year;} public int getMonth(){return month;} public void setMonth(int month){this.month=month;} public TimesheetStatus getStatus(){return status;} public void setStatus(TimesheetStatus status){this.status=status;} public Instant getSubmittedAt(){return submittedAt;} public void setSubmittedAt(Instant submittedAt){this.submittedAt=submittedAt;} public Long getSubmittedByUserId(){return submittedByUserId;} public void setSubmittedByUserId(Long submittedByUserId){this.submittedByUserId=submittedByUserId;} public Instant getApprovedAt(){return approvedAt;} public void setApprovedAt(Instant approvedAt){this.approvedAt=approvedAt;} public Long getApprovedByUserId(){return approvedByUserId;} public void setApprovedByUserId(Long approvedByUserId){this.approvedByUserId=approvedByUserId;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public Long getVersion(){return version;} public void setVersion(Long version){this.version=version;}
}

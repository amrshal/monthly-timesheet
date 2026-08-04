package com.amrshalaby.timesheet.timesheet;

import io.micronaut.data.annotation.*;
import java.time.Instant; import java.time.LocalDate;

@MappedEntity("daily_time_entry")
public class DailyTimeEntry {
 @Id @GeneratedValue private Long id; @MappedProperty("timesheet_id") private Long timesheetId; @MappedProperty("work_date") private LocalDate workDate; @MappedProperty("duration_minutes") private int durationMinutes; @MappedProperty private String note;
 @DateCreated @MappedProperty("created_at") private Instant createdAt; @DateUpdated @MappedProperty("updated_at") private Instant updatedAt; @Version private Long version;
 public Long getId(){return id;} public void setId(Long id){this.id=id;} public Long getTimesheetId(){return timesheetId;} public void setTimesheetId(Long timesheetId){this.timesheetId=timesheetId;} public LocalDate getWorkDate(){return workDate;} public void setWorkDate(LocalDate workDate){this.workDate=workDate;} public int getDurationMinutes(){return durationMinutes;} public void setDurationMinutes(int durationMinutes){this.durationMinutes=durationMinutes;} public String getNote(){return note;} public void setNote(String note){this.note=note;} public Long getVersion(){return version;} public void setVersion(Long version){this.version=version;}
}

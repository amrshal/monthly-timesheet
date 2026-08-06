package com.amrshalaby.timesheet.audit;

public record AuditEventView(
    String eventTime,
    String actor,
    String subject,
    String eventType,
    String entity,
    String details
) {
}

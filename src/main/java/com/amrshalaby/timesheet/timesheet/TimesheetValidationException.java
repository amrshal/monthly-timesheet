package com.amrshalaby.timesheet.timesheet;

import java.util.Map;

public class TimesheetValidationException extends RuntimeException {
    private final SaveTimesheetCommand command;
    private final Map<String, String> fieldErrors;

    public TimesheetValidationException(SaveTimesheetCommand command, Map<String, String> fieldErrors) {
        super("Please correct the highlighted timesheet entries.");
        this.command = command;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public SaveTimesheetCommand command() {
        return command;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}

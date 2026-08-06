package com.amrshalaby.timesheet.common;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Singleton
public class BusinessTimeFormatter {
    private final ZoneId businessZone;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    public BusinessTimeFormatter(@Value("${app.timezone:Europe/London}") String businessTimezone) {
        this.businessZone = ZoneId.of(businessTimezone);
    }

    public String format(Instant instant) {
        if (instant == null) {
            return "";
        }

        return formatter.format(instant.atZone(businessZone));
    }
}

package com.amrshalaby.timesheet.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.amrshalaby.timesheet.common.EmailNormalizer;
import org.junit.jupiter.api.Test;

class EmailNormalizerTest {
    @Test
    void trimsAndLowercases() {
        assertEquals("person@example.com", EmailNormalizer.normalize(" Person@Example.COM "));
    }
}

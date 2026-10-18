package com.insidertrans.service.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class Form4BackfillJobTest {

    @Test
    void parseArgsReadsEqualsForm() {
        Form4BackfillJob.DateRange range = Form4BackfillJob.parseArgs(
                new String[] {"--startDate=2026-10-01", "--endDate=2026-10-15"});
        assertEquals(LocalDate.of(2026, 10, 1), range.startDate());
        assertEquals(LocalDate.of(2026, 10, 15), range.endDate());
    }

    @Test
    void parseArgsReadsSeparateTokens() {
        Form4BackfillJob.DateRange range = Form4BackfillJob.parseArgs(
                new String[] {"--backfill", "--startDate", "2026-07-01", "--endDate", "2026-09-30"});
        assertEquals(LocalDate.of(2026, 7, 1), range.startDate());
        assertEquals(LocalDate.of(2026, 9, 30), range.endDate());
    }

    @Test
    void parseArgsRequiresBothDates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Form4BackfillJob.parseArgs(new String[] {"--startDate=2026-10-01"}));
    }
}

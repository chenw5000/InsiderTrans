package com.insidertrans.service.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class Form4FetchServiceTest {

    @Test
    void requireAllowedIntervalAcceptsConfiguredChoices() {
        for (int minutes : Form4FetchService.ALLOWED_INTERVALS) {
            assertEquals(minutes, Form4FetchService.requireAllowedInterval(minutes));
        }
    }

    @Test
    void requireAllowedIntervalRejectsOtherValues() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class, () -> Form4FetchService.requireAllowedInterval(45));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void summarizeIncludesSkipCount() {
        Form4BackfillJob.Result result = new Form4BackfillJob.Result(
                LocalDate.of(2024, 10, 1),
                LocalDate.of(2024, 10, 31),
                22,
                9,
                1174,
                10,
                1160,
                4,
                40,
                java.util.List.of());
        String summary = Form4FetchService.summarize("backfill", result);
        assertEquals(
                "backfill 2024-10-01 to 2024-10-31: 1174 filing(s), 10 imported, 1160 already stored, 4 failed, 40 row(s)",
                summary);
    }
}

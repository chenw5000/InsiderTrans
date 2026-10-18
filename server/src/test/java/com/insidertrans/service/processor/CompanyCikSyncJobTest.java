package com.insidertrans.service.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CompanyCikSyncJobTest {

    @Test
    void lookupCikMatchesExactAndHyphenVariants() {
        Map<String, String> map = Map.of(
                "AAPL", "320193",
                "PBR.A", "11199",
                "GOOGL", "1652044");

        assertEquals("320193", CompanyCikSyncJob.lookupCik(map, "aapl"));
        assertEquals("11199", CompanyCikSyncJob.lookupCik(map, "PBR-A"));
        assertEquals("1652044", CompanyCikSyncJob.lookupCik(map, "GOOGL"));
        assertNull(CompanyCikSyncJob.lookupCik(map, "NOSUCH"));
    }
}

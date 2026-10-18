package com.insidertrans.web.dto;

import java.time.Instant;
import java.util.List;

public record FetcherStatusResponse(
        boolean enabled,
        int intervalMinutes,
        List<Integer> allowedIntervals,
        boolean jobRunning,
        String jobKind,
        Instant lastRunAt,
        String lastSummary,
        String lastError) {
}

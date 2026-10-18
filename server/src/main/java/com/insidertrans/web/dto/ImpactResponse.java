package com.insidertrans.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ImpactResponse(
        String ticker,
        LocalDate asOfDate,
        boolean filingsFound,
        int historyWindowDays,
        int scoreWindowDays,
        int barCountUsed,
        BigDecimal medianDollarVolume,
        String trend,
        int scoredTradeCount,
        double flowScore,
        String flowLabel,
        Double meanReturn5d,
        Double meanReturn10d,
        Double meanReturn20d,
        String caveat,
        List<ImpactTradeResponse> transactions) {
}

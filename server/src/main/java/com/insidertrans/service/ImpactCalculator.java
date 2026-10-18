package com.insidertrans.service;

import com.insidertrans.domain.TransactionCode;
import com.insidertrans.web.dto.OhlcBar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Job 1 scoring: annotate recent open-market P/S against the tape. Not a trained forecast.
 */
final class ImpactCalculator {

    static final int HISTORY_DAYS = 90;
    static final int SCORE_DAYS = 30;
    static final double BUY_CASH_CAP = 1_000_000d;
    static final double SELL_CASH_CAP = 5_000_000d;
    static final int MA_SHORT = 20;
    static final int MA_LONG = 60;
    static final int DOLLAR_VOLUME_WINDOW = 20;
    /** Cash equal to 2% of typical daily dollar volume saturates the tape-size factor. */
    static final double TAPE_FULL_FRACTION = 0.02;

    private ImpactCalculator() {
    }

    static double typeFactor(TransactionCode code) {
        if (code == TransactionCode.P) {
            return 1;
        }
        if (code == TransactionCode.S) {
            return -1;
        }
        return 0;
    }

    static double amountFactor(TransactionCode code, BigDecimal cash) {
        if (cash == null || cash.signum() <= 0) {
            return 0;
        }
        double cap = code == TransactionCode.S ? SELL_CASH_CAP : BUY_CASH_CAP;
        if (code != TransactionCode.P && code != TransactionCode.S) {
            return 0;
        }
        return clamp(cash.doubleValue() / cap, 0, 1);
    }

    static double timeFactor(LocalDate knownDate, LocalDate asOfDate) {
        if (knownDate == null || asOfDate == null || knownDate.isAfter(asOfDate)) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(knownDate, asOfDate);
        return clamp(1d - (days / (double) SCORE_DAYS), 0, 1);
    }

    static double impact(double typeFactor, double amountFactor, double timeFactor) {
        return typeFactor * amountFactor * timeFactor;
    }

    /**
     * Mean impact times log(1+N) so many similar prints grow slowly instead of unbounded sum.
     */
    static double flowScore(List<Double> impacts) {
        if (impacts == null || impacts.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double value : impacts) {
            sum += value;
        }
        double mean = sum / impacts.size();
        return mean * Math.log1p(impacts.size());
    }

    static String flowLabel(double score) {
        if (score > 2) {
            return "VERY_POSITIVE";
        }
        if (score > 0.5) {
            return "POSITIVE";
        }
        if (score >= -0.5) {
            return "NEUTRAL";
        }
        if (score >= -2) {
            return "NEGATIVE";
        }
        return "VERY_NEGATIVE";
    }

    static List<OhlcBar> usableBars(List<OhlcBar> bars, LocalDate asOfDate) {
        List<OhlcBar> usable = new ArrayList<>();
        if (bars == null) {
            return usable;
        }
        for (OhlcBar bar : bars) {
            if (bar == null || bar.date() == null || bar.close() == null) {
                continue;
            }
            if (!bar.date().isAfter(asOfDate)) {
                usable.add(bar);
            }
        }
        usable.sort(Comparator.comparing(OhlcBar::date));
        return usable;
    }

    static BigDecimal medianDollarVolume(List<OhlcBar> bars) {
        if (bars.isEmpty()) {
            return null;
        }
        int from = Math.max(0, bars.size() - DOLLAR_VOLUME_WINDOW);
        List<BigDecimal> values = new ArrayList<>();
        for (int i = from; i < bars.size(); i++) {
            OhlcBar bar = bars.get(i);
            if (bar.volume() == null || bar.volume().signum() < 0) {
                continue;
            }
            values.add(bar.close().multiply(bar.volume()));
        }
        if (values.isEmpty()) {
            return null;
        }
        values.sort(Comparator.naturalOrder());
        int mid = values.size() / 2;
        if (values.size() % 2 == 1) {
            return values.get(mid);
        }
        return values.get(mid - 1).add(values.get(mid)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    static String trend(List<OhlcBar> bars) {
        if (bars.size() < MA_SHORT) {
            return "UNKNOWN";
        }
        double close = bars.getLast().close().doubleValue();
        double sma20 = sma(bars, MA_SHORT);
        if (bars.size() < MA_LONG) {
            if (close > sma20 * 1.005) {
                return "UP";
            }
            if (close < sma20 * 0.995) {
                return "DOWN";
            }
            return "SIDEWAYS";
        }
        double sma60 = sma(bars, MA_LONG);
        if (close > sma20 && sma20 > sma60) {
            return "UP";
        }
        if (close < sma20 && sma20 < sma60) {
            return "DOWN";
        }
        return "SIDEWAYS";
    }

    private static double sma(List<OhlcBar> bars, int window) {
        double sum = 0;
        for (int i = bars.size() - window; i < bars.size(); i++) {
            sum += bars.get(i).close().doubleValue();
        }
        return sum / window;
    }

    static Double sessionReturn(List<OhlcBar> bars, LocalDate fromDate, int sessions) {
        int start = firstIndexOnOrAfter(bars, fromDate);
        if (start < 0) {
            return null;
        }
        int end = start + sessions;
        if (end >= bars.size()) {
            return null;
        }
        double startClose = bars.get(start).close().doubleValue();
        double endClose = bars.get(end).close().doubleValue();
        if (startClose == 0) {
            return null;
        }
        return endClose / startClose - 1d;
    }

    static Double cashToMedianVolume(BigDecimal cash, BigDecimal medianDollarVolume) {
        if (cash == null || cash.signum() <= 0 || medianDollarVolume == null || medianDollarVolume.signum() <= 0) {
            return null;
        }
        return cash.doubleValue() / medianDollarVolume.doubleValue();
    }

    static double tapeAmountFactor(BigDecimal cash, BigDecimal medianDollarVolume) {
        if (cash == null || cash.signum() <= 0 || medianDollarVolume == null || medianDollarVolume.signum() <= 0) {
            return 0;
        }
        double cap = medianDollarVolume.doubleValue() * TAPE_FULL_FRACTION;
        if (cap <= 0) {
            return 0;
        }
        return clamp(cash.doubleValue() / cap, 0, 1);
    }

    static Double mean(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    static String tickerKey(String ticker) {
        return ticker.trim().toUpperCase(Locale.ROOT);
    }

    private static int firstIndexOnOrAfter(List<OhlcBar> bars, LocalDate date) {
        for (int i = 0; i < bars.size(); i++) {
            if (!bars.get(i).date().isBefore(date)) {
                return i;
            }
        }
        return -1;
    }

    static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }
}

package com.insidertrans.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.insidertrans.domain.TransactionCode;
import com.insidertrans.web.dto.OhlcBar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImpactCalculatorTest {

    @Test
    void typeAndAmountAndTimeMatchSpec() {
        assertEquals(1d, ImpactCalculator.typeFactor(TransactionCode.P));
        assertEquals(-1d, ImpactCalculator.typeFactor(TransactionCode.S));
        assertEquals(0d, ImpactCalculator.typeFactor(TransactionCode.A));

        assertEquals(0d, ImpactCalculator.amountFactor(TransactionCode.P, BigDecimal.ZERO));
        assertEquals(0.5d, ImpactCalculator.amountFactor(TransactionCode.P, new BigDecimal("500000")), 1e-9);
        assertEquals(1d, ImpactCalculator.amountFactor(TransactionCode.P, new BigDecimal("2000000")));
        assertEquals(0.4d, ImpactCalculator.amountFactor(TransactionCode.S, new BigDecimal("2000000")), 1e-9);

        LocalDate asOf = LocalDate.of(2026, 6, 30);
        assertEquals(1d, ImpactCalculator.timeFactor(asOf, asOf));
        assertEquals(0d, ImpactCalculator.timeFactor(asOf.minusDays(30), asOf));
        assertEquals(0.5d, ImpactCalculator.timeFactor(asOf.minusDays(15), asOf), 1e-9);
        assertEquals(0d, ImpactCalculator.timeFactor(asOf.plusDays(1), asOf));
    }

    @Test
    void flowScoreAveragesThenLogCompresses() {
        assertEquals(0d, ImpactCalculator.flowScore(List.of()));
        double one = ImpactCalculator.flowScore(List.of(1d));
        assertEquals(Math.log1p(1) * 1d, one, 1e-9);
        double ten = ImpactCalculator.flowScore(List.of(1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d));
        assertTrue(ten > one);
        assertTrue(ten < 10d);
        assertEquals("POSITIVE", ImpactCalculator.flowLabel(1.0));
        assertEquals("NEUTRAL", ImpactCalculator.flowLabel(0.2));
        assertEquals("VERY_NEGATIVE", ImpactCalculator.flowLabel(-2.5));
    }

    @Test
    void sessionReturnNeedsEnoughBarsAfterFiling() {
        List<OhlcBar> bars = risingBars(LocalDate.of(2026, 1, 2), 30, new BigDecimal("100"));
        assertEquals(0.05d, ImpactCalculator.sessionReturn(bars, LocalDate.of(2026, 1, 2), 5), 1e-9);
        assertNull(ImpactCalculator.sessionReturn(bars, LocalDate.of(2026, 1, 2), 40));
        assertNull(ImpactCalculator.sessionReturn(bars, LocalDate.of(2026, 3, 1), 5));
    }

    @Test
    void barsAfterAsOfAreDropped() {
        List<OhlcBar> bars = new ArrayList<>();
        bars.add(bar(LocalDate.of(2026, 6, 1), "10", "100"));
        bars.add(bar(LocalDate.of(2026, 6, 2), "10", "110"));
        bars.add(bar(LocalDate.of(2026, 6, 3), "10", "999"));
        List<OhlcBar> usable = ImpactCalculator.usableBars(bars, LocalDate.of(2026, 6, 2));
        assertEquals(2, usable.size());
        assertEquals(LocalDate.of(2026, 6, 2), usable.getLast().date());
    }

    private static List<OhlcBar> risingBars(LocalDate start, int n, BigDecimal firstClose) {
        List<OhlcBar> bars = new ArrayList<>();
        BigDecimal close = firstClose;
        for (int i = 0; i < n; i++) {
            bars.add(bar(start.plusDays(i), "1000", close.toPlainString()));
            close = close.add(new BigDecimal("1"));
        }
        return bars;
    }

    private static OhlcBar bar(LocalDate date, String volume, String close) {
        BigDecimal c = new BigDecimal(close);
        return new OhlcBar(date, c, c, c, c, new BigDecimal(volume));
    }
}

package com.insidertrans.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.insidertrans.domain.AcquisitionDisposition;
import com.insidertrans.domain.Company;
import com.insidertrans.domain.Insider;
import com.insidertrans.domain.InsiderTransaction;
import com.insidertrans.domain.TransactionCode;
import com.insidertrans.repo.InsiderTransactionRepository;
import com.insidertrans.web.dto.ImpactRequest;
import com.insidertrans.web.dto.ImpactResponse;
import com.insidertrans.web.dto.OhlcBar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InsiderImpactServiceTest {

    @Mock
    private InsiderTransactionRepository transactions;

    private InsiderImpactService service;

    @BeforeEach
    void setUp() {
        service = new InsiderImpactService(transactions);
    }

    @Test
    void emptyHistoryIsNeutralAndFilingsFoundFalse() {
        when(transactions.findKnownAsOf(eq("nvda"), any(), any())).thenReturn(List.of());
        ImpactResponse response = service.evaluate(new ImpactRequest("nvda", LocalDate.of(2026, 6, 30), bars(120)));
        assertFalse(response.filingsFound());
        assertEquals("NEUTRAL", response.flowLabel());
        assertEquals(0, response.scoredTradeCount());
        assertTrue(response.transactions().isEmpty());
    }

    @Test
    void scoresRecentPurchaseAndIgnoresGrant() {
        LocalDate asOf = LocalDate.of(2026, 6, 30);
        InsiderTransaction buy = trade(1L, TransactionCode.P, asOf.minusDays(5), "10000", "200");
        InsiderTransaction grant = trade(2L, TransactionCode.A, asOf.minusDays(5), "50000", "0");
        when(transactions.findKnownAsOf(eq("nvda"), any(), eq(asOf))).thenReturn(List.of(buy, grant));

        ImpactResponse response = service.evaluate(new ImpactRequest("NVDA", asOf, bars(120)));
        assertTrue(response.filingsFound());
        assertEquals(1, response.scoredTradeCount());
        assertEquals(2, response.transactions().size());
        assertTrue(response.transactions().getFirst().includedInScore());
        assertFalse(response.transactions().get(1).includedInScore());
        assertEquals("POSITIVE", response.flowLabel());
    }

    private static InsiderTransaction trade(
            long id, TransactionCode code, LocalDate filed, String shares, String price) {
        Company company = new Company();
        company.setTicker("NVDA");
        company.setName("NVIDIA CORP");
        Insider insider = new Insider();
        insider.setName("STEVENS MARK A");
        InsiderTransaction row = new InsiderTransaction();
        row.setId(id);
        row.setCompany(company);
        row.setInsider(insider);
        row.setTransactionDate(filed.minusDays(1));
        row.setFilingDate(filed);
        row.setCode(code);
        row.setAcquiredOrDisposed(code == TransactionCode.S ? AcquisitionDisposition.D : AcquisitionDisposition.A);
        row.setShares(new BigDecimal(shares));
        row.setPricePerShare(new BigDecimal(price));
        row.setSecurityTitle("Common Stock");
        row.setInsiderRole("Director");
        return row;
    }

    private static List<OhlcBar> bars(int n) {
        List<OhlcBar> list = new ArrayList<>();
        LocalDate day = LocalDate.of(2026, 1, 5);
        BigDecimal close = new BigDecimal("100");
        int added = 0;
        while (added < n) {
            if (day.getDayOfWeek().getValue() <= 5) {
                list.add(new OhlcBar(day, close, close, close, close, new BigDecimal("1000000")));
                close = close.add(new BigDecimal("0.10"));
                added++;
            }
            day = day.plusDays(1);
        }
        return list;
    }
}

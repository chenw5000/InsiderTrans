package com.insidertrans.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insidertrans.domain.AcquisitionDisposition;
import com.insidertrans.domain.Company;
import com.insidertrans.domain.Insider;
import com.insidertrans.domain.InsiderTransaction;
import com.insidertrans.domain.TransactionCode;
import com.insidertrans.repo.CompanyRepository;
import com.insidertrans.repo.InsiderRepository;
import com.insidertrans.repo.InsiderTransactionRepository;
import com.insidertrans.web.dto.TransactionRequest;
import com.insidertrans.web.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private CompanyRepository companies;

    @Mock
    private InsiderRepository insiders;

    @Mock
    private InsiderTransactionRepository transactions;

    @InjectMocks
    private LedgerService ledger;

    @Test
    void purchaseStoresUppercaseTickerAndAcquisition() {
        when(companies.findByTickerIgnoreCase("AAPL")).thenReturn(Optional.empty());
        when(companies.save(any(Company.class))).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            company.setId(1L);
            return company;
        });
        when(insiders.findByNameIgnoreCase("Ada Lovelace")).thenReturn(Optional.empty());
        when(insiders.save(any(Insider.class))).thenAnswer(invocation -> {
            Insider insider = invocation.getArgument(0);
            insider.setId(2L);
            return insider;
        });
        when(transactions.save(any(InsiderTransaction.class))).thenAnswer(invocation -> {
            InsiderTransaction transaction = invocation.getArgument(0);
            transaction.setId(3L);
            return transaction;
        });

        TransactionResponse response = ledger.record(sample(TransactionCode.P, null));

        assertEquals("AAPL", response.ticker());
        assertEquals("Apple Inc.", response.companyName());
        assertEquals("Ada Lovelace", response.insiderName());
        assertEquals(AcquisitionDisposition.A, response.acquiredOrDisposed());
        assertEquals(0, response.value().compareTo(new BigDecimal("270375")));

        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companies).save(companyCaptor.capture());
        assertEquals("AAPL", companyCaptor.getValue().getTicker());
    }

    @Test
    void giftWithoutDirectionIsRejected() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> ledger.record(sample(TransactionCode.G, null)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private static TransactionRequest sample(TransactionCode code, AcquisitionDisposition disposition) {
        return new TransactionRequest(
                "aapl",
                "Apple Inc.",
                null,
                "Ada Lovelace",
                null,
                "Officer",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 3),
                code,
                disposition,
                new BigDecimal("1500.0000"),
                new BigDecimal("180.2500"),
                new BigDecimal("15000.0000"),
                "Common Stock");
    }
}

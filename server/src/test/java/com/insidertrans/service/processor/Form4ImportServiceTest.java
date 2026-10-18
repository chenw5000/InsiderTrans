package com.insidertrans.service.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.insidertrans.domain.Company;
import com.insidertrans.domain.Insider;
import com.insidertrans.domain.InsiderTransaction;
import com.insidertrans.domain.TransactionCode;
import com.insidertrans.repo.CompanyRepository;
import com.insidertrans.repo.InsiderRepository;
import com.insidertrans.repo.InsiderTransactionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Form4ImportServiceTest {

    @Mock
    private CompanyRepository companies;

    @Mock
    private InsiderRepository insiders;

    @Mock
    private InsiderTransactionRepository transactions;

    private Form4ImportService importer;
    private Company nvidia;

    @BeforeEach
    void setUp() {
        nvidia = new Company();
        nvidia.setId(10L);
        nvidia.setTicker("NVDA");
        nvidia.setName("NVIDIA CORP");
        nvidia.setCik("1045810");

        importer = new Form4ImportService(companies, insiders, transactions);
        when(companies.findAll()).thenReturn(List.of(nvidia));
        importer.refreshCompanyIndex();
    }

    @Test
    void importFilingCreatesInsiderAndTransaction() {
        when(transactions.existsByAccessionNumber("0001045810-24-000294")).thenReturn(false);
        when(insiders.findByCik("1199039")).thenReturn(Optional.empty());
        when(insiders.save(any(Insider.class))).thenAnswer(invocation -> {
            Insider insider = invocation.getArgument(0);
            insider.setId(20L);
            return insider;
        });
        when(transactions.save(any(InsiderTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Form4Filing filing = new Form4Filing(
                "1045810",
                "NVIDIA CORP",
                "4",
                LocalDate.of(2024, 10, 1),
                "Archives/edgar/data/1045810/0001045810-24-000294.txt",
                "https://www.sec.gov/Archives/edgar/data/1045810/0001045810-24-000294.txt");

        int inserted = importer.importFiling(filing, samplePackage());

        assertEquals(1, inserted);
        ArgumentCaptor<Insider> insiderCaptor = ArgumentCaptor.forClass(Insider.class);
        verify(insiders).save(insiderCaptor.capture());
        assertEquals("STEVENS MARK A", insiderCaptor.getValue().getName());
        assertEquals("1199039", insiderCaptor.getValue().getCik());

        ArgumentCaptor<InsiderTransaction> txCaptor = ArgumentCaptor.forClass(InsiderTransaction.class);
        verify(transactions).save(txCaptor.capture());
        InsiderTransaction row = txCaptor.getValue();
        assertSame(nvidia, row.getCompany());
        assertEquals(TransactionCode.S, row.getCode());
        assertEquals(LocalDate.of(2024, 9, 27), row.getTransactionDate());
        assertEquals(LocalDate.of(2024, 10, 1), row.getFilingDate());
        assertEquals("0001045810-24-000294", row.getAccessionNumber());
        assertEquals("Director", row.getInsiderRole());
    }

    @Test
    void importFilingSkipsWhenAccessionAlreadyStored() {
        when(transactions.existsByAccessionNumber("0001045810-24-000294")).thenReturn(true);

        Form4Filing filing = new Form4Filing(
                "1045810",
                "NVIDIA CORP",
                "4",
                LocalDate.of(2024, 10, 1),
                "Archives/edgar/data/1045810/0001045810-24-000294.txt",
                "https://www.sec.gov/Archives/edgar/data/1045810/0001045810-24-000294.txt");

        assertEquals(0, importer.importFiling(filing, samplePackage()));
        verify(insiders, never()).save(any());
        verify(transactions, never()).save(any());
    }

    private static String samplePackage() {
        return """
                <SEC-DOCUMENT>
                <XML>
                <ownershipDocument>
                    <issuer>
                        <issuerCik>0001045810</issuerCik>
                        <issuerName>NVIDIA CORP</issuerName>
                        <issuerTradingSymbol>NVDA</issuerTradingSymbol>
                    </issuer>
                    <reportingOwner>
                        <reportingOwnerId>
                            <rptOwnerCik>0001199039</rptOwnerCik>
                            <rptOwnerName>STEVENS MARK A</rptOwnerName>
                        </reportingOwnerId>
                        <reportingOwnerRelationship>
                            <isDirector>1</isDirector>
                            <isOfficer>0</isOfficer>
                        </reportingOwnerRelationship>
                    </reportingOwner>
                    <nonDerivativeTransaction>
                        <securityTitle><value>Common Stock</value></securityTitle>
                        <transactionDate><value>2024-09-27</value></transactionDate>
                        <transactionCoding><transactionCode>S</transactionCode></transactionCoding>
                        <transactionAmounts>
                            <transactionShares><value>40000</value></transactionShares>
                            <transactionPricePerShare><value>121.0089</value></transactionPricePerShare>
                            <transactionAcquiredDisposedCode><value>D</value></transactionAcquiredDisposedCode>
                        </transactionAmounts>
                        <postTransactionAmounts>
                            <sharesOwnedFollowingTransaction><value>8380117</value></sharesOwnedFollowingTransaction>
                        </postTransactionAmounts>
                    </nonDerivativeTransaction>
                </ownershipDocument>
                </XML>
                """;
    }
}

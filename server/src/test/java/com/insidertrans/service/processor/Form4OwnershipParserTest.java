package com.insidertrans.service.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.insidertrans.domain.AcquisitionDisposition;
import com.insidertrans.domain.TransactionCode;
import com.insidertrans.service.processor.Form4OwnershipParser.ParsedOwnership;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class Form4OwnershipParserTest {

    @Test
    void parseOwnershipXmlReadsIssuerOwnerAndSale() {
        String xml = """
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
                            <officerTitle></officerTitle>
                        </reportingOwnerRelationship>
                    </reportingOwner>
                    <nonDerivativeTable>
                        <nonDerivativeTransaction>
                            <securityTitle><value>Common Stock</value></securityTitle>
                            <transactionDate><value>2024-09-27</value></transactionDate>
                            <transactionCoding>
                                <transactionCode>S</transactionCode>
                            </transactionCoding>
                            <transactionAmounts>
                                <transactionShares><value>40000</value></transactionShares>
                                <transactionPricePerShare><value>121.0089</value></transactionPricePerShare>
                                <transactionAcquiredDisposedCode><value>D</value></transactionAcquiredDisposedCode>
                            </transactionAmounts>
                            <postTransactionAmounts>
                                <sharesOwnedFollowingTransaction><value>8380117</value></sharesOwnedFollowingTransaction>
                            </postTransactionAmounts>
                        </nonDerivativeTransaction>
                    </nonDerivativeTable>
                </ownershipDocument>
                """;

        ParsedOwnership parsed = Form4OwnershipParser.parseOwnershipXml(xml);

        assertEquals("1045810", parsed.issuerCik());
        assertEquals("NVDA", parsed.ticker());
        assertEquals("1199039", parsed.insiderCik());
        assertEquals("STEVENS MARK A", parsed.insiderName());
        assertEquals("Director", parsed.insiderRole());
        assertEquals(1, parsed.transactions().size());
        assertEquals(LocalDate.of(2024, 9, 27), parsed.transactions().getFirst().transactionDate());
        assertEquals(TransactionCode.S, parsed.transactions().getFirst().code());
        assertEquals(AcquisitionDisposition.D, parsed.transactions().getFirst().acquiredOrDisposed());
        assertEquals(0, parsed.transactions().getFirst().shares().compareTo(new BigDecimal("40000")));
        assertEquals(0, parsed.transactions().getFirst().pricePerShare().compareTo(new BigDecimal("121.0089")));
    }

    @Test
    void extractOwnershipXmlPullsDocumentFromSubmissionPackage() {
        String packageBody = """
                <SEC-DOCUMENT>0001045810-24-000294.txt
                <TEXT>
                <XML>
                <ownershipDocument>
                    <issuer>
                        <issuerCik>0001045810</issuerCik>
                        <issuerName>NVIDIA CORP</issuerName>
                        <issuerTradingSymbol>NVDA</issuerTradingSymbol>
                    </issuer>
                    <reportingOwner>
                        <rptOwnerCik>0001199039</rptOwnerCik>
                        <rptOwnerName>STEVENS MARK A</rptOwnerName>
                    </reportingOwner>
                    <nonDerivativeTable></nonDerivativeTable>
                </ownershipDocument>
                </XML>
                </TEXT>
                """;

        ParsedOwnership parsed = Form4OwnershipParser.parseFilingDocument(packageBody);
        assertEquals("1045810", parsed.issuerCik());
        assertEquals("STEVENS MARK A", parsed.insiderName());
        assertEquals(0, parsed.transactions().size());
    }

    @Test
    void skipsUnsupportedTransactionCodes() {
        String xml = """
                <ownershipDocument>
                    <issuer>
                        <issuerCik>1</issuerCik>
                        <issuerName>X</issuerName>
                        <issuerTradingSymbol>X</issuerTradingSymbol>
                    </issuer>
                    <reportingOwner>
                        <rptOwnerCik>2</rptOwnerCik>
                        <rptOwnerName>Owner</rptOwnerName>
                    </reportingOwner>
                    <nonDerivativeTransaction>
                        <securityTitle><value>Common Stock</value></securityTitle>
                        <transactionDate><value>2024-10-01</value></transactionDate>
                        <transactionCoding><transactionCode>J</transactionCode></transactionCoding>
                        <transactionAmounts>
                            <transactionShares><value>10</value></transactionShares>
                            <transactionAcquiredDisposedCode><value>A</value></transactionAcquiredDisposedCode>
                        </transactionAmounts>
                    </nonDerivativeTransaction>
                </ownershipDocument>
                """;

        ParsedOwnership parsed = Form4OwnershipParser.parseOwnershipXml(xml);
        assertEquals(0, parsed.transactions().size());
        assertNull(parsed.insiderRole());
    }
}

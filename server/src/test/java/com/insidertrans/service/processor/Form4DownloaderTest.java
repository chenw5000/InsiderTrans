package com.insidertrans.service.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Form4DownloaderTest {

    private Form4Downloader downloader;

    @BeforeEach
    void setUp() {
        downloader = new Form4Downloader(new ObjectMapper());
    }

    @Test
    void dailyMasterIndexPathUsesYearQuarterAndDay() {
        assertEquals(
                "/Archives/edgar/daily-index/2026/QTR4/master.20261016.idx",
                Form4Downloader.dailyMasterIndexPath(LocalDate.of(2026, 10, 16)));
        assertEquals(
                "/Archives/edgar/daily-index/2026/QTR1/master.20260301.idx",
                Form4Downloader.dailyMasterIndexPath(LocalDate.of(2026, 3, 1)));
    }

    @Test
    void parseForm4FilingsKeepsOnlyForm4Rows() {
        String index = """
                Description: Daily Index of EDGAR Filings
                Last Data Received: Oct 16, 2026
                ----------------------------------------
                CIK|Company Name|Form Type|Date Filed|File Name
                ----------------------------------------
                320193|Apple Inc.|4|20261016|edgar/data/320193/0000320193-26-000100.txt
                320193|Apple Inc.|10-K|20261016|edgar/data/320193/0000320193-26-000101.txt
                789019|MICROSOFT CORP|4/A|20261016|edgar/data/789019/0000789019-26-000050.txt
                1318605|Tesla, Inc.|8-K|20261016|edgar/data/1318605/0000950170-26-000001.txt
                """;

        List<Form4Filing> filings = downloader.parseForm4Filings(index, null);

        assertEquals(2, filings.size());
        assertEquals("320193", filings.get(0).cik());
        assertEquals("4", filings.get(0).formType());
        assertEquals(
                "https://www.sec.gov/Archives/edgar/data/320193/0000320193-26-000100.txt",
                filings.get(0).documentUrl());
        assertEquals("0000320193-26-000100", filings.get(0).accessionNumber());
        assertEquals("4/A", filings.get(1).formType());
    }

    @Test
    void parseForm4FilingsFiltersByCik() {
        String index = """
                ----------------------------------------
                320193|Apple Inc.|4|20261016|edgar/data/320193/0000320193-26-000100.txt
                789019|MICROSOFT CORP|4|20261016|edgar/data/789019/0000789019-26-000050.txt
                """;

        List<Form4Filing> filings = downloader.parseForm4Filings(index, Set.of("320193"));

        assertEquals(1, filings.size());
        assertEquals("Apple Inc.", filings.getFirst().companyName());
    }

    @Test
    void downloadLatestAvailableForm4FilingsFallsBackWhenDayMissing() {
        // Live SEC check: today's index may 403; lookback should still return Form 4 rows.
        List<Form4Filing> filings = downloader.downloadLatestAvailableForm4Filings(
                LocalDate.of(2026, 10, 16), 3, null);
        assertTrue(filings.size() > 0, "expected Form 4 rows from a recent published index day");
        assertTrue(filings.stream().allMatch(f -> f.formType().equals("4") || f.formType().equalsIgnoreCase("4/A")));
    }

    @Test
    void parseTickerCikMapReadsSecJson() throws Exception {
        String json = """
                {
                  "0": {"cik_str": 320193, "ticker": "AAPL", "title": "Apple Inc."},
                  "1": {"cik_str": 789019, "ticker": "MSFT", "title": "MICROSOFT CORP"}
                }
                """;

        Map<String, String> map = downloader.parseTickerCikMap(json);

        assertEquals("320193", map.get("AAPL"));
        assertEquals("789019", map.get("MSFT"));
        assertTrue(Form4Downloader.normalizeCik("0000320193").equals("320193"));
    }
}

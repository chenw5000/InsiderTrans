package com.insidertrans.service.processor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Downloads Form 4 filings from SEC EDGAR by reading the daily rolling master index.
 *
 * <p>Hourly worker flow:
 * <ol>
 *   <li>Download {@code master.YYYYMMDD.idx} for the current business day</li>
 *   <li>Stream pipe-delimited lines</li>
 *   <li>Keep rows whose form type is {@code 4} or {@code 4/A}</li>
 *   <li>Optionally keep only issuers that match a watchlist of tickers (via CIK)</li>
 *   <li>Return the direct archive path / URL for each matching filing</li>
 * </ol>
 *
 * <p>SEC fair-access policy requires a descriptive {@code User-Agent}. Index files live under
 * {@code /Archives/edgar/daily-index/{year}/QTR{n}/}.
 */
@Component
public class Form4Downloader {

    private static final Logger log = LoggerFactory.getLogger(Form4Downloader.class);

    static final String USER_AGENT = "ciacm.com chenw3000@yahoo.com";
    static final String SEC_BASE = "https://www.sec.gov";
    /** How many calendar days to walk backward when today's index is not published yet. */
    static final int DEFAULT_INDEX_LOOKBACK_DAYS = 7;

    private static final DateTimeFormatter INDEX_DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter FILED_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient secClient;
    private final ObjectMapper objectMapper;

    public Form4Downloader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // SEC fair-access: identify the caller, and avoid HTTP keep-alive.
        this.secClient = RestClient.builder()
                .baseUrl(SEC_BASE)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT, "text/plain, application/json, */*")
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
                .defaultHeader(HttpHeaders.CONNECTION, "close")
                .defaultHeader(HttpHeaders.HOST, "www.sec.gov")
                .build();
    }

    /**
     * Downloads the newest available daily master index (today, or the latest prior business day
     * when today's file has not been published yet) and returns every Form 4 / 4/A entry.
     */
    public List<Form4Filing> downloadTodaysForm4Filings() {
        return downloadLatestAvailableForm4Filings(LocalDate.now(), DEFAULT_INDEX_LOOKBACK_DAYS, null);
    }

    /**
     * Downloads the daily master index for {@code filingDay} and returns Form 4 / 4/A entries.
     */
    public List<Form4Filing> downloadForm4Filings(LocalDate filingDay) {
        String indexBody = downloadDailyMasterIndex(filingDay);
        return parseForm4Filings(indexBody, null);
    }

    /**
     * Same as {@link #downloadForm4Filings(LocalDate)}, but only keeps issuers whose ticker is in
     * {@code tickers}. Tickers are resolved to CIKs through SEC {@code company_tickers.json}.
     */
    public List<Form4Filing> downloadForm4Filings(LocalDate filingDay, Collection<String> tickers) {
        Set<String> cikFilter = resolveTickersToCiks(tickers);
        if (cikFilter.isEmpty()) {
            log.warn("No CIKs resolved for tickers {}; returning no Form 4 filings", tickers);
            return List.of();
        }
        String indexBody = downloadDailyMasterIndex(filingDay);
        return parseForm4Filings(indexBody, cikFilter);
    }

    /**
     * Same as {@link #downloadForm4Filings(LocalDate)}, filtered by issuer CIKs already on file.
     */
    public List<Form4Filing> downloadForm4FilingsForCiks(LocalDate filingDay, Set<String> ciks) {
        Set<String> cikFilter = ciks == null
                ? Set.of()
                : ciks.stream().map(Form4Downloader::normalizeCik).filter(cik -> !cik.isEmpty()).collect(Collectors.toSet());
        if (cikFilter.isEmpty()) {
            log.warn("No CIKs provided; returning no Form 4 filings");
            return List.of();
        }
        String indexBody = downloadDailyMasterIndex(filingDay);
        return parseForm4Filings(indexBody, cikFilter);
    }

    /**
     * Walks backward from {@code startDay} until an index file is found (SEC often returns 403/404
     * for a day whose rolling index has not been published yet).
     */
    public List<Form4Filing> downloadLatestAvailableForm4Filings(
            LocalDate startDay, int maxLookbackDays, Collection<String> tickers) {
        Set<String> cikFilter = null;
        if (tickers != null && !tickers.isEmpty()) {
            cikFilter = resolveTickersToCiks(tickers);
            if (cikFilter.isEmpty()) {
                log.warn("No CIKs resolved for tickers {}; returning no Form 4 filings", tickers);
                return List.of();
            }
        }

        IllegalStateException lastFailure = null;
        for (int offset = 0; offset <= maxLookbackDays; offset++) {
            LocalDate day = startDay.minusDays(offset);
            try {
                String indexBody = downloadDailyMasterIndex(day);
                if (offset > 0) {
                    log.info(
                            "Daily index for {} was not available; using {} instead",
                            startDay,
                            day);
                }
                return parseForm4Filings(indexBody, cikFilter);
            } catch (IllegalStateException ex) {
                if (isIndexUnavailable(ex)) {
                    log.warn("SEC daily index not available for {}: {}", day, rootMessage(ex));
                    lastFailure = ex;
                    continue;
                }
                throw ex;
            }
        }
        throw new IllegalStateException(
                "No SEC daily master index found from "
                        + startDay
                        + " back "
                        + maxLookbackDays
                        + " day(s). The current day's file is often unpublished until later in the"
                        + " business day; SEC may respond with HTTP 403 until it appears.",
                lastFailure);
    }

    /**
     * Fetches the raw daily master index text for the given calendar day.
     */
    public String downloadDailyMasterIndex(LocalDate filingDay) {
        String path = dailyMasterIndexPath(filingDay);
        log.info("Downloading SEC daily master index {}", path);
        try {
            String body = secClient.get()
                    .uri(path)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("Empty response for SEC daily index " + path);
            }
            return body;
        } catch (HttpClientErrorException ex) {
            HttpStatusCode status = ex.getStatusCode();
            String hint = status.value() == 403 || status.value() == 404
                    ? " (index for this day may not be published yet; SEC often returns 403 until it is)"
                    : "";
            throw new IllegalStateException(
                    "Failed to download SEC daily index " + path + " HTTP " + status.value() + hint,
                    ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to download SEC daily index " + path, ex);
        }
    }

    private static boolean isIndexUnavailable(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof HttpClientErrorException http) {
                int code = http.getStatusCode().value();
                return code == 403 || code == 404;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    /**
     * Downloads the filing document (usually a .txt submission package) referenced by an index row.
     */
    public String downloadFilingDocument(Form4Filing filing) {
        Objects.requireNonNull(filing, "filing");
        String path = filing.fileName().startsWith("/") ? filing.fileName() : "/" + filing.fileName();
        log.info("Downloading Form 4 document {}", path);
        try {
            String body = secClient.get()
                    .uri(path)
                    .retrieve()
                    .body(String.class);
            if (body == null) {
                throw new IllegalStateException("Empty response for Form 4 document " + path);
            }
            return body;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to download Form 4 document " + path, ex);
        }
    }

    /**
     * Builds the relative path under sec.gov for that day's master index.
     * Example: {@code /Archives/edgar/daily-index/2026/QTR4/master.20261016.idx}
     */
    public static String dailyMasterIndexPath(LocalDate filingDay) {
        int quarter = ((filingDay.getMonthValue() - 1) / 3) + 1;
        String day = filingDay.format(INDEX_DAY);
        return "/Archives/edgar/daily-index/" + filingDay.getYear() + "/QTR" + quarter + "/master." + day + ".idx";
    }

    /**
     * Parses a daily/full master.idx body into Form 4 filings.
     *
     * @param cikFilter normalized zero-padded CIKs to keep, or {@code null} to keep every Form 4
     */
    List<Form4Filing> parseForm4Filings(String indexBody, Set<String> cikFilter) {
        List<Form4Filing> filings = new ArrayList<>();
        boolean inData = false;

        try (BufferedReader reader = new BufferedReader(new StringReader(indexBody))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!inData) {
                    // Data section starts after the dashed separator, or once a pipe-row appears.
                    if (line.startsWith("-----") || looksLikeDataRow(line)) {
                        inData = true;
                        if (line.startsWith("-----")) {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }

                if (line.isBlank() || line.startsWith("-----")) {
                    continue;
                }
                if (!line.contains("|")) {
                    continue;
                }

                Form4Filing filing = parseDataRow(line);
                if (filing == null) {
                    continue;
                }
                if (cikFilter != null && !cikFilter.contains(normalizeCik(filing.cik()))) {
                    continue;
                }
                filings.add(filing);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse SEC daily index", ex);
        }

        log.info("Parsed {} Form 4 filing(s) from daily index", filings.size());
        return filings;
    }

    private static Form4Filing parseDataRow(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 5) {
            return null;
        }

        String formType = parts[2].trim();
        if (!isForm4(formType)) {
            return null;
        }

        String cik = parts[0].trim();
        String companyName = parts[1].trim();
        String filedRaw = parts[3].trim();
        String fileName = parts[4].trim().replace('\\', '/');
        if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }

        LocalDate filedDate;
        try {
            filedDate = LocalDate.parse(filedRaw, FILED_DATE);
        } catch (Exception ex) {
            log.debug("Skipping Form 4 row with bad date '{}': {}", filedRaw, line);
            return null;
        }

        String documentUrl = SEC_BASE + "/Archives/" + fileName;
        return new Form4Filing(cik, companyName, formType, filedDate, "Archives/" + fileName, documentUrl);
    }

    private static boolean looksLikeDataRow(String line) {
        if (!line.contains("|")) {
            return false;
        }
        String[] parts = line.split("\\|", -1);
        return parts.length >= 5 && parts[0].chars().allMatch(Character::isDigit);
    }

    private static boolean isForm4(String formType) {
        return "4".equals(formType) || "4/A".equalsIgnoreCase(formType);
    }

    /**
     * Loads SEC ticker → CIK map and returns zero-padded CIKs for the requested tickers.
     */
    public Set<String> resolveTickersToCiks(Collection<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return Set.of();
        }
        Set<String> wanted = tickers.stream()
                .filter(Objects::nonNull)
                .map(ticker -> ticker.trim().toUpperCase(Locale.ROOT))
                .filter(ticker -> !ticker.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        if (wanted.isEmpty()) {
            return Set.of();
        }

        Map<String, String> tickerToCik = downloadTickerCikMap();
        Set<String> ciks = new HashSet<>();
        for (String ticker : wanted) {
            String cik = tickerToCik.get(ticker);
            if (cik == null) {
                log.warn("No SEC CIK found for ticker {}", ticker);
            } else {
                ciks.add(cik);
            }
        }
        return ciks;
    }

    Map<String, String> downloadTickerCikMap() {
        try {
            String body = secClient.get()
                    .uri("/files/company_tickers.json")
                    .retrieve()
                    .body(String.class);
            return parseTickerCikMap(body);
        } catch (RestClientException | IOException ex) {
            throw new IllegalStateException("Failed to download SEC company_tickers.json", ex);
        }
    }

    Map<String, String> parseTickerCikMap(String json) throws IOException {
        Map<String, String> map = new HashMap<>();
        JsonNode root = objectMapper.readTree(json);
        root.properties().forEach(entry -> {
            JsonNode node = entry.getValue();
            String ticker = textOrNull(node.get("ticker"));
            JsonNode cikNode = node.get("cik_str");
            if (ticker == null || cikNode == null || cikNode.isNull()) {
                return;
            }
            map.put(ticker.toUpperCase(Locale.ROOT), normalizeCik(cikNode.asString()));
        });
        return map;
    }

    static String normalizeCik(String cik) {
        if (cik == null) {
            return "";
        }
        String digits = cik.trim();
        if (digits.isEmpty()) {
            return "";
        }
        // Index rows and company_tickers both compare as unpadded CIK strings.
        return digits.replaceFirst("^0+(?!$)", "");
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asString();
        return value == null || value.isBlank() ? null : value.trim();
    }
    public static void main(String[] args) {
        Form4Downloader downloader = new Form4Downloader(new ObjectMapper());
        List<Form4Filing> filings = downloader.downloadTodaysForm4Filings();
        System.out.println("Form 4 filings found: " + filings.size());
        filings.stream().limit(20).forEach(System.out::println);
        if (filings.size() > 20) {
            System.out.println("... and " + (filings.size() - 20) + " more");
        }
    }
}

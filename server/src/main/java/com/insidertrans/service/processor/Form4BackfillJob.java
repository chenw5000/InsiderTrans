package com.insidertrans.service.processor;

import com.insidertrans.InsiderTransApplication;
import com.insidertrans.domain.Company;
import com.insidertrans.repo.CompanyRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Walks SEC daily master indexes from {@code startDate} through {@code endDate}, downloads each
 * matching Form 4 package, and inserts reporting owners / transaction rows.
 *
 * <p>Run:
 * {@code mvn -f server/pom.xml -DskipTests exec:java
 * -Dexec.mainClass=com.insidertrans.service.processor.Form4BackfillJob
 * "-Dexec.args=--startDate=2026-10-01 --endDate=2026-10-15"}
 */
@Component
public class Form4BackfillJob {

    private static final Logger log = LoggerFactory.getLogger(Form4BackfillJob.class);
    static final long DEFAULT_PAUSE_MILLIS = 200;

    private final CompanyRepository companies;
    private final Form4Downloader downloader;
    private final Form4ImportService importer;

    public Form4BackfillJob(
            CompanyRepository companies, Form4Downloader downloader, Form4ImportService importer) {
        this.companies = companies;
        this.downloader = downloader;
        this.importer = importer;
    }

    public Result backfill(LocalDate startDate, LocalDate endDate) {
        return backfill(startDate, endDate, DEFAULT_PAUSE_MILLIS);
    }

    public Result backfill(LocalDate startDate, LocalDate endDate, long pauseMillis) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required (yyyy-MM-dd)");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate " + endDate + " is before startDate " + startDate);
        }

        Set<String> ciks = watchlistCiks();
        if (ciks.isEmpty()) {
            throw new IllegalStateException(
                    "No company CIKs in the database. Run CompanyCikSyncJob before backfill.");
        }
        importer.refreshCompanyIndex();
        log.info("Backfilling Form 4 filings from {} to {} for {} issuer CIKs", startDate, endDate, ciks.size());

        int daysProcessed = 0;
        int daysSkipped = 0;
        int filingCount = 0;
        int importedFilings = 0;
        int skippedExisting = 0;
        int failedFilings = 0;
        int transactionRows = 0;
        List<Form4Filing> filings = new ArrayList<>();

        LocalDate day = startDate;
        while (!day.isAfter(endDate)) {
            try {
                List<Form4Filing> dayFilings = downloader.downloadForm4FilingsForCiks(day, ciks);
                filings.addAll(dayFilings);
                filingCount += dayFilings.size();
                daysProcessed++;
                log.info("{}: {} Form 4 filing(s) for watchlist issuers", day, dayFilings.size());

                for (int i = 0; i < dayFilings.size(); i++) {
                    Form4Filing filing = dayFilings.get(i);
                    if (importer.alreadyImported(filing.accessionNumber())) {
                        skippedExisting++;
                        continue;
                    }
                    try {
                        String body = downloader.downloadFilingDocument(filing);
                        int inserted = importer.importFiling(filing, body);
                        if (inserted > 0) {
                            importedFilings++;
                            transactionRows += inserted;
                        }
                    } catch (RuntimeException ex) {
                        failedFilings++;
                        log.warn("Failed to import {}: {}", filing.documentUrl(), ex.getMessage());
                    }
                    if (i + 1 < dayFilings.size() && pauseMillis > 0) {
                        sleep(pauseMillis);
                    }
                }
            } catch (IllegalStateException ex) {
                daysSkipped++;
                log.warn("Skipping {}: {}", day, ex.getMessage());
            }
            day = day.plusDays(1);
            if (!day.isAfter(endDate) && pauseMillis > 0) {
                sleep(pauseMillis);
            }
        }

        Result result = new Result(
                startDate,
                endDate,
                daysProcessed,
                daysSkipped,
                filingCount,
                importedFilings,
                skippedExisting,
                failedFilings,
                transactionRows,
                List.copyOf(filings));
        log.info(
                "Backfill finished {} to {}: {} day(s) with an index, {} skipped, {} Form 4 filing(s), "
                        + "{} imported, {} already stored, {} failed, {} transaction row(s)",
                startDate,
                endDate,
                result.daysProcessed(),
                result.daysSkipped(),
                result.filingCount(),
                result.importedFilings(),
                result.skippedExisting(),
                result.failedFilings(),
                result.transactionRows());
        return result;
    }

    Set<String> watchlistCiks() {
        Set<String> ciks = new LinkedHashSet<>();
        for (Company company : companies.findAllByOrderByTickerAsc()) {
            String cik = Form4Downloader.normalizeCik(company.getCik());
            if (!cik.isEmpty()) {
                ciks.add(cik);
            }
        }
        return ciks;
    }

    static DateRange parseArgs(String[] args) {
        String startRaw = namedValue(args, "startDate");
        String endRaw = namedValue(args, "endDate");
        if (startRaw == null || endRaw == null) {
            throw new IllegalArgumentException(
                    "Backfill requires --startDate=yyyy-MM-dd and --endDate=yyyy-MM-dd");
        }
        try {
            return new DateRange(LocalDate.parse(startRaw), LocalDate.parse(endRaw));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Dates must be yyyy-MM-dd, got startDate=" + startRaw + " endDate=" + endRaw, ex);
        }
    }

    private static String namedValue(String[] args, String name) {
        if (args == null) {
            return null;
        }
        String prefix = "--" + name + "=";
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null) {
                continue;
            }
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length()).trim();
            }
            if (arg.equals("--" + name) && i + 1 < args.length) {
                return args[i + 1].trim();
            }
        }
        return null;
    }

    private static void sleep(long pauseMillis) {
        try {
            Thread.sleep(pauseMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Backfill interrupted", ex);
        }
    }

    public record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    public record Result(
            LocalDate startDate,
            LocalDate endDate,
            int daysProcessed,
            int daysSkipped,
            int filingCount,
            int importedFilings,
            int skippedExisting,
            int failedFilings,
            int transactionRows,
            List<Form4Filing> filings) {
        public Result {
            filings = filings == null ? List.of() : List.copyOf(filings);
        }
    }

    public static void main(String[] args) {
        DateRange range = parseArgs(args);
        SpringApplication app = new SpringApplication(InsiderTransApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = app.run(args)) {
            Result result = context.getBean(Form4BackfillJob.class).backfill(range.startDate(), range.endDate());
            System.out.println("Form 4 backfill " + result.startDate() + " to " + result.endDate()
                    + ": " + result.filingCount() + " filing(s), "
                    + result.importedFilings() + " imported, "
                    + result.skippedExisting() + " already stored, "
                    + result.failedFilings() + " failed, "
                    + result.transactionRows() + " transaction row(s), "
                    + result.daysProcessed() + " day(s) with an index, "
                    + result.daysSkipped() + " skipped");
        }
    }
}

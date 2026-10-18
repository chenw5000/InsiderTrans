package com.insidertrans.service.processor;

import com.insidertrans.domain.FetcherSettings;
import com.insidertrans.repo.FetcherSettingsRepository;
import com.insidertrans.web.dto.FetcherConfigRequest;
import com.insidertrans.web.dto.FetcherStatusResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-controlled Form 4 poller. Interval and start/stop persist in {@code fetcher_settings}.
 * Accessions already in {@code insider_transactions} are skipped by {@link Form4ImportService}.
 */
@Service
public class Form4FetchService {

    private static final Logger log = LoggerFactory.getLogger(Form4FetchService.class);
    public static final List<Integer> ALLOWED_INTERVALS = List.of(15, 30, 60, 180, 360);
    private static final Set<Integer> ALLOWED_INTERVAL_SET = Set.copyOf(ALLOWED_INTERVALS);
    static final int POLL_LOOKBACK_DAYS = 1;

    private final Form4BackfillJob backfill;
    private final FetcherSettingsRepository settings;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    private final AtomicBoolean jobRunning = new AtomicBoolean(false);
    private final AtomicReference<String> jobKind = new AtomicReference<>("idle");
    private ScheduledFuture<?> scheduled;

    public Form4FetchService(Form4BackfillJob backfill, FetcherSettingsRepository settings) {
        this.backfill = backfill;
        this.settings = settings;
    }

    @PostConstruct
    void start() {
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("form4-fetch-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        applySchedule(loadSettings());
    }

    @PreDestroy
    void stop() {
        cancelSchedule();
        scheduler.shutdown();
    }

    public FetcherStatusResponse status() {
        FetcherSettings row = loadSettings();
        return new FetcherStatusResponse(
                row.isEnabled(),
                row.getIntervalMinutes(),
                ALLOWED_INTERVALS,
                jobRunning.get(),
                jobKind.get(),
                row.getLastRunAt(),
                row.getLastSummary(),
                row.getLastError());
    }

    public FetcherStatusResponse update(FetcherConfigRequest request) {
        FetcherSettings row = loadSettings();
        if (request.intervalMinutes() != null) {
            row.setIntervalMinutes(requireAllowedInterval(request.intervalMinutes()));
        }
        if (request.enabled() != null) {
            row.setEnabled(request.enabled());
        }
        settings.save(row);
        applySchedule(row);
        log.info("Fetcher config: enabled={} interval={}m", row.isEnabled(), row.getIntervalMinutes());
        return status();
    }

    public FetcherStatusResponse startBackfill(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate is before startDate");
        }
        if (!jobRunning.compareAndSet(false, true)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A fetch or backfill is already running");
        }
        jobKind.set("backfill");
        scheduler.execute(() -> runLocked("backfill", () -> backfill.backfill(startDate, endDate), true));
        return status();
    }

    static int requireAllowedInterval(int minutes) {
        if (!ALLOWED_INTERVAL_SET.contains(minutes)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "intervalMinutes must be one of " + ALLOWED_INTERVALS);
        }
        return minutes;
    }

    private void applySchedule(FetcherSettings row) {
        cancelSchedule();
        if (!row.isEnabled()) {
            return;
        }
        Duration period = Duration.ofMinutes(row.getIntervalMinutes());
        scheduled = scheduler.scheduleAtFixedRate(
                this::pollIfIdle, Instant.now().plusSeconds(5), period);
    }

    private void cancelSchedule() {
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
    }

    private void pollIfIdle() {
        if (!jobRunning.compareAndSet(false, true)) {
            log.info("Skipping scheduled poll; another Form 4 job is running");
            return;
        }
        jobKind.set("poll");
        runLocked("poll", () -> {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(POLL_LOOKBACK_DAYS);
            return backfill.backfill(start, end);
        }, true);
    }

    private void runLocked(
            String kind, java.util.function.Supplier<Form4BackfillJob.Result> work, boolean alreadyMarked) {
        if (!alreadyMarked && !jobRunning.compareAndSet(false, true)) {
            return;
        }
        jobKind.set(kind);
        try {
            Form4BackfillJob.Result result = work.get();
            recordSuccess(summarize(kind, result));
        } catch (RuntimeException ex) {
            log.warn("Form 4 {} failed: {}", kind, ex.getMessage());
            recordFailure(clip(ex.getMessage()));
        } finally {
            jobKind.set("idle");
            jobRunning.set(false);
        }
    }

    private void recordSuccess(String summary) {
        FetcherSettings row = loadSettings();
        row.setLastRunAt(Instant.now());
        row.setLastSummary(clip(summary));
        row.setLastError(null);
        settings.save(row);
    }

    private void recordFailure(String error) {
        FetcherSettings row = loadSettings();
        row.setLastRunAt(Instant.now());
        row.setLastError(clip(error));
        settings.save(row);
    }

    private FetcherSettings loadSettings() {
        return settings.findById(FetcherSettings.SINGLETON_ID).orElseGet(() -> {
            FetcherSettings created = new FetcherSettings();
            created.setId(FetcherSettings.SINGLETON_ID);
            created.setEnabled(false);
            created.setIntervalMinutes(60);
            return settings.save(created);
        });
    }

    static String summarize(String kind, Form4BackfillJob.Result result) {
        return kind + " " + result.startDate() + " to " + result.endDate()
                + ": " + result.filingCount() + " filing(s), "
                + result.importedFilings() + " imported, "
                + result.skippedExisting() + " already stored, "
                + result.failedFilings() + " failed, "
                + result.transactionRows() + " row(s)";
    }

    private static String clip(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }
}

package com.insidertrans.service.processor;

import com.insidertrans.InsiderTransApplication;
import com.insidertrans.domain.Company;
import com.insidertrans.repo.CompanyRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills {@code companies.cik} from the SEC ticker map ({@code company_tickers.json}).
 */
@Component
public class CompanyCikSyncJob {

    private static final Logger log = LoggerFactory.getLogger(CompanyCikSyncJob.class);

    private final CompanyRepository companies;
    private final Form4Downloader downloader;

    public CompanyCikSyncJob(CompanyRepository companies, Form4Downloader downloader) {
        this.companies = companies;
        this.downloader = downloader;
    }

    @Transactional
    public Result sync() {
        Map<String, String> tickerToCik = downloader.downloadTickerCikMap();
        log.info("Loaded {} ticker-to-CIK mappings from SEC", tickerToCik.size());

        List<Company> rows = companies.findAll();
        int updated = 0;
        int unchanged = 0;
        List<String> missing = new ArrayList<>();

        for (Company company : rows) {
            String cik = lookupCik(tickerToCik, company.getTicker());
            if (cik == null || cik.isBlank()) {
                missing.add(company.getTicker());
                continue;
            }
            if (cik.equals(company.getCik())) {
                unchanged++;
                continue;
            }
            company.setCik(cik);
            companies.save(company);
            updated++;
        }

        Result result = new Result(rows.size(), updated, unchanged, List.copyOf(missing));
        log.info(
                "CIK sync finished: {} companies, {} updated, {} already set, {} unmatched: {}",
                result.total(),
                result.updated(),
                result.unchanged(),
                result.missing().size(),
                result.missing());
        return result;
    }

    /**
     * Resolves a listing ticker to a CIK. Tries exact match, then hyphen/dot variants used by SEC.
     */
    static String lookupCik(Map<String, String> tickerToCik, String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        String key = ticker.trim().toUpperCase(Locale.ROOT);
        String cik = tickerToCik.get(key);
        if (cik != null) {
            return cik;
        }
        cik = tickerToCik.get(key.replace('-', '.'));
        if (cik != null) {
            return cik;
        }
        cik = tickerToCik.get(key.replace('.', '-'));
        if (cik != null) {
            return cik;
        }
        return tickerToCik.get(key.replace("-", "").replace(".", ""));
    }

    public record Result(int total, int updated, int unchanged, List<String> missing) {
        public Result {
            missing = missing == null ? List.of() : List.copyOf(missing);
        }
    }

    /**
     * One-shot runner: {@code mvn -f server/pom.xml -DskipTests exec:java
     * -Dexec.mainClass=com.insidertrans.service.processor.CompanyCikSyncJob}
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(InsiderTransApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = app.run(args)) {
            Result result = context.getBean(CompanyCikSyncJob.class).sync();
            System.out.println("CIK sync: " + result.updated() + " updated, "
                    + result.unchanged() + " already set, "
                    + result.missing().size() + " unmatched of "
                    + result.total());
            if (!result.missing().isEmpty()) {
                System.out.println("Unmatched tickers: " + String.join(", ", result.missing()));
            }
            int exit = result.missing().size() == result.total() ? 1 : 0;
            System.exit(SpringApplication.exit(context, () -> exit));
        }
    }
}

package com.insidertrans.service.processor;

import com.insidertrans.domain.Company;
import com.insidertrans.domain.Insider;
import com.insidertrans.domain.InsiderTransaction;
import com.insidertrans.repo.CompanyRepository;
import com.insidertrans.repo.InsiderRepository;
import com.insidertrans.repo.InsiderTransactionRepository;
import com.insidertrans.service.processor.Form4OwnershipParser.ParsedOwnership;
import com.insidertrans.service.processor.Form4OwnershipParser.ParsedTransaction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a downloaded Form 4 submission into {@code insiders} / {@code insider_transactions} rows.
 */
@Service
public class Form4ImportService {

    private static final Logger log = LoggerFactory.getLogger(Form4ImportService.class);

    private final CompanyRepository companies;
    private final InsiderRepository insiders;
    private final InsiderTransactionRepository transactions;

    private Map<String, List<Company>> companiesByCik = Map.of();
    private Map<String, Company> companiesByTicker = Map.of();

    public Form4ImportService(
            CompanyRepository companies,
            InsiderRepository insiders,
            InsiderTransactionRepository transactions) {
        this.companies = companies;
        this.insiders = insiders;
        this.transactions = transactions;
    }

    public boolean alreadyImported(String accessionNumber) {
        return accessionNumber != null && !accessionNumber.isBlank()
                && transactions.existsByAccessionNumber(accessionNumber);
    }

    public void refreshCompanyIndex() {
        Map<String, List<Company>> byCik = new HashMap<>();
        Map<String, Company> byTicker = new HashMap<>();
        for (Company company : companies.findAll()) {
            if (company.getTicker() != null && !company.getTicker().isBlank()) {
                byTicker.put(company.getTicker().trim().toUpperCase(Locale.ROOT), company);
            }
            String cik = Form4Downloader.normalizeCik(company.getCik());
            if (!cik.isEmpty()) {
                byCik.computeIfAbsent(cik, key -> new ArrayList<>()).add(company);
            }
        }
        this.companiesByCik = byCik;
        this.companiesByTicker = byTicker;
    }

    /**
     * Imports one filing. Returns how many transaction rows were inserted (0 if skipped).
     */
    @Transactional
    public int importFiling(Form4Filing filing, String documentBody) {
        String accession = filing.accessionNumber();
        if (accession != null && transactions.existsByAccessionNumber(accession)) {
            log.debug("Skipping already-imported accession {}", accession);
            return 0;
        }

        ParsedOwnership parsed = Form4OwnershipParser.parseFilingDocument(documentBody);
        if (parsed.transactions().isEmpty()) {
            log.info("No storeable transaction rows in accession {}", accession);
            return 0;
        }
        if (parsed.insiderName() == null) {
            log.warn("Skipping accession {}: no reporting owner name", accession);
            return 0;
        }

        Company company = resolveCompany(filing, parsed);
        if (company == null) {
            log.warn(
                    "Skipping accession {}: issuer CIK {} / ticker {} is not in companies",
                    accession,
                    parsed.issuerCik(),
                    parsed.ticker());
            return 0;
        }

        Insider insider = upsertInsider(parsed);
        int inserted = 0;
        for (ParsedTransaction row : parsed.transactions()) {
            InsiderTransaction transaction = new InsiderTransaction();
            transaction.setCompany(company);
            transaction.setInsider(insider);
            transaction.setTransactionDate(row.transactionDate());
            transaction.setFilingDate(filing.filedDate());
            transaction.setCode(row.code());
            transaction.setAcquiredOrDisposed(row.acquiredOrDisposed());
            transaction.setShares(row.shares());
            transaction.setPricePerShare(row.pricePerShare());
            transaction.setSharesOwnedFollowing(row.sharesOwnedFollowing());
            transaction.setSecurityTitle(row.securityTitle());
            transaction.setInsiderRole(parsed.insiderRole());
            transaction.setAccessionNumber(accession);
            transaction.setDocumentUrl(filing.documentUrl());
            transaction.setFormType(filing.formType());
            transactions.save(transaction);
            inserted++;
        }
        log.info(
                "Imported accession {} ({} {}): {} transaction row(s) for {}",
                accession,
                filing.formType(),
                company.getTicker(),
                inserted,
                insider.getName());
        return inserted;
    }

    private Company resolveCompany(Form4Filing filing, ParsedOwnership parsed) {
        if (parsed.ticker() != null) {
            Company byTicker = companiesByTicker.get(parsed.ticker());
            if (byTicker != null) {
                return byTicker;
            }
        }
        String cik = parsed.issuerCik();
        if (cik == null || cik.isEmpty()) {
            cik = Form4Downloader.normalizeCik(filing.cik());
        }
        List<Company> matches = companiesByCik.getOrDefault(cik, List.of());
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        if (parsed.ticker() != null) {
            for (Company company : matches) {
                if (parsed.ticker().equalsIgnoreCase(company.getTicker())) {
                    return company;
                }
            }
        }
        return matches.getFirst();
    }

    Insider upsertInsider(ParsedOwnership parsed) {
        String cik = blankToNull(parsed.insiderCik());
        String name = parsed.insiderName().trim();
        Insider insider = cik == null
                ? insiders.findByNameIgnoreCase(name).orElseGet(Insider::new)
                : insiders.findByCik(cik).orElseGet(Insider::new);
        insider.setName(name);
        if (cik != null) {
            insider.setCik(cik);
        }
        return insiders.save(insider);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

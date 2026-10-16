package com.insidertrans.service;

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
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class LedgerService {

    private final CompanyRepository companies;
    private final InsiderRepository insiders;
    private final InsiderTransactionRepository transactions;

    public LedgerService(
            CompanyRepository companies,
            InsiderRepository insiders,
            InsiderTransactionRepository transactions) {
        this.companies = companies;
        this.insiders = insiders;
        this.transactions = transactions;
    }

    public List<TransactionResponse> search(
            String ticker, String insider, TransactionCode code, LocalDate from, LocalDate to) {
        return transactions.search(blankToNull(ticker), likePattern(insider), code, from, to).stream()
                .map(LedgerService::toResponse)
                .toList();
    }

    public TransactionResponse get(Long id) {
        InsiderTransaction transaction = transactions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Transaction " + id + " was not found"));
        return toResponse(transaction);
    }

    @Transactional
    public TransactionResponse record(TransactionRequest request) {
        AcquisitionDisposition disposition = request.acquiredOrDisposed();
        if (disposition == null) {
            disposition = request.code().defaultDisposition();
        }
        if (disposition == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Gift transactions need acquiredOrDisposed set to A or D");
        }

        Company company = upsertCompany(request);
        Insider insider = upsertInsider(request);

        InsiderTransaction transaction = new InsiderTransaction();
        transaction.setCompany(company);
        transaction.setInsider(insider);
        transaction.setTransactionDate(request.transactionDate());
        transaction.setFilingDate(request.filingDate());
        transaction.setCode(request.code());
        transaction.setAcquiredOrDisposed(disposition);
        transaction.setShares(request.shares());
        transaction.setPricePerShare(request.pricePerShare());
        transaction.setSharesOwnedFollowing(request.sharesOwnedFollowing());
        transaction.setSecurityTitle(request.securityTitle().trim());
        transaction.setInsiderRole(blankToNull(request.insiderRole()));

        return toResponse(transactions.save(transaction));
    }

    @Transactional
    public void delete(Long id) {
        if (!transactions.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Transaction " + id + " was not found");
        }
        transactions.deleteById(id);
    }

    private Company upsertCompany(TransactionRequest request) {
        String ticker = request.ticker().trim().toUpperCase(Locale.ROOT);
        Company company = companies.findByTickerIgnoreCase(ticker).orElseGet(Company::new);
        company.setTicker(ticker);
        company.setName(request.companyName().trim());
        String cik = blankToNull(request.companyCik());
        if (cik != null) {
            company.setCik(cik);
        }
        return companies.save(company);
    }

    private Insider upsertInsider(TransactionRequest request) {
        String name = request.insiderName().trim();
        String cik = blankToNull(request.insiderCik());
        Insider insider = cik == null
                ? insiders.findByNameIgnoreCase(name).orElseGet(Insider::new)
                : insiders.findByCik(cik).orElseGet(Insider::new);
        insider.setName(name);
        if (cik != null) {
            insider.setCik(cik);
        }
        return insiders.save(insider);
    }

    private static TransactionResponse toResponse(InsiderTransaction transaction) {
        BigDecimal price = transaction.getPricePerShare();
        BigDecimal value = price == null ? null : transaction.getShares().multiply(price);
        return new TransactionResponse(
                transaction.getId(),
                transaction.getCompany().getTicker(),
                transaction.getCompany().getName(),
                transaction.getInsider().getName(),
                transaction.getInsiderRole(),
                transaction.getTransactionDate(),
                transaction.getFilingDate(),
                transaction.getCode(),
                transaction.getCode().getLabel(),
                transaction.getAcquiredOrDisposed(),
                transaction.getShares(),
                price,
                value,
                transaction.getSharesOwnedFollowing(),
                transaction.getSecurityTitle());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String likePattern(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        String escaped = normalized
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}

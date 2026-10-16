package com.insidertrans.web.dto;

import com.insidertrans.domain.AcquisitionDisposition;
import com.insidertrans.domain.TransactionCode;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        String ticker,
        String companyName,
        String insiderName,
        String insiderRole,
        LocalDate transactionDate,
        LocalDate filingDate,
        TransactionCode code,
        String codeLabel,
        AcquisitionDisposition acquiredOrDisposed,
        BigDecimal shares,
        BigDecimal pricePerShare,
        BigDecimal value,
        BigDecimal sharesOwnedFollowing,
        String securityTitle
) {
}

package com.insidertrans.web.dto;

import com.insidertrans.domain.AcquisitionDisposition;
import com.insidertrans.domain.TransactionCode;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ImpactTradeResponse(
        Long id,
        String insiderName,
        String insiderRole,
        LocalDate transactionDate,
        LocalDate filingDate,
        LocalDate knownDate,
        TransactionCode code,
        String codeLabel,
        AcquisitionDisposition acquiredOrDisposed,
        BigDecimal shares,
        BigDecimal pricePerShare,
        BigDecimal cash,
        String securityTitle,
        double typeFactor,
        double amountFactor,
        double timeFactor,
        double impact,
        Double cashToMedianDollarVolume,
        Double return5d,
        Double return10d,
        Double return20d,
        boolean includedInScore) {
}

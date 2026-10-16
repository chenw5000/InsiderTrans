package com.insidertrans.web.dto;

import com.insidertrans.domain.AcquisitionDisposition;
import com.insidertrans.domain.TransactionCode;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotBlank @Size(max = 16) String ticker,
        @NotBlank @Size(max = 200) String companyName,
        @Size(max = 20) String companyCik,
        @NotBlank @Size(max = 200) String insiderName,
        @Size(max = 20) String insiderCik,
        @Size(max = 120) String insiderRole,
        @NotNull LocalDate transactionDate,
        LocalDate filingDate,
        @NotNull TransactionCode code,
        AcquisitionDisposition acquiredOrDisposed,
        @NotNull @Positive @Digits(integer = 14, fraction = 4) BigDecimal shares,
        @PositiveOrZero @Digits(integer = 14, fraction = 4) BigDecimal pricePerShare,
        @PositiveOrZero @Digits(integer = 14, fraction = 4) BigDecimal sharesOwnedFollowing,
        @NotBlank @Size(max = 120) String securityTitle
) {
}

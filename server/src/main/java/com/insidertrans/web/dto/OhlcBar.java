package com.insidertrans.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

public record OhlcBar(
        @NotNull LocalDate date,
        @NotNull @Positive BigDecimal open,
        @NotNull @Positive BigDecimal high,
        @NotNull @Positive BigDecimal low,
        @NotNull @Positive BigDecimal close,
        @NotNull @PositiveOrZero BigDecimal volume) {
}

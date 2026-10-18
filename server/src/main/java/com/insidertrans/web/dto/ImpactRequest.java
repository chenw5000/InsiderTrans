package com.insidertrans.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ImpactRequest(
        @NotBlank @Size(max = 16) String ticker,
        @NotNull LocalDate asOfDate,
        @NotEmpty @Size(max = 200) List<@Valid OhlcBar> bars) {
}

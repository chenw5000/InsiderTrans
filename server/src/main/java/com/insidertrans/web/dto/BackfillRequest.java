package com.insidertrans.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record BackfillRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {
}

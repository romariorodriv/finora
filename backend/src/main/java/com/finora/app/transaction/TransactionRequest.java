package com.finora.app.transaction;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
    @NotBlank String description,
    @DecimalMin("0.01") BigDecimal amount,
    @NotNull LocalDate date,
    @NotBlank String category,
    String type,
    String merchant,
    boolean recurring
) {}

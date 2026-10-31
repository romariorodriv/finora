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
    boolean recurring,
    @Pattern(regexp="[A-Z]{3}") String currency
) {
 public TransactionRequest(String d,BigDecimal a,LocalDate date,String c,String type,String merchant,boolean recurring){this(d,a,date,c,type,merchant,recurring,null);}
}

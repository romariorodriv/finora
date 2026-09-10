package com.finora.app.dashboard;

import com.finora.app.transaction.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public record DashboardResponse(
    String month,
    BigDecimal expenses,
    BigDecimal income,
    BigDecimal balance,
    BigDecimal dailyAverage,
    BigDecimal projection,
    Map<String, BigDecimal> categories,
    Map<LocalDate, BigDecimal> daily,
    List<TransactionResponse> transactions,
    List<TransactionResponse> recurring
) {}

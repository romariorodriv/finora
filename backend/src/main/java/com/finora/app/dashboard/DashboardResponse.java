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
    List<CategorySummary> macroCategories,
    String insightTitle,
    String insightBody,
    Map<LocalDate, BigDecimal> daily,
    List<TransactionResponse> transactions,
    List<TransactionResponse> recurring
) {
  public record CategorySummary(String macroCategory, String label, BigDecimal total, double percentage) {}
  public record SubcategorySummary(String name, BigDecimal total) {}
  public record CategoryDetail(
      String macroCategory,
      String label,
      BigDecimal total,
      List<SubcategorySummary> subcategories,
      List<TransactionResponse> transactions
  ) {}
}

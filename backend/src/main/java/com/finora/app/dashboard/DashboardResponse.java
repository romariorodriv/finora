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
    int expenseCount,
    BigDecimal averageTicket,
    BigDecimal dailyAverage,
    BigDecimal projection,
    Map<String, BigDecimal> categories,
    List<CategorySummary> macroCategories,
    List<MerchantSummary> merchants,
    String insightTitle,
    String insightBody,
    Map<LocalDate, BigDecimal> daily,
    List<TransactionResponse> transactions,
    List<TransactionResponse> recurring
) {
  public record CategorySummary(String macroCategory, String label, BigDecimal total, double percentage) {}
  public record MerchantSummary(String merchant, String label, BigDecimal total, double percentage) {}
  public record SubcategorySummary(String name, BigDecimal total) {}
  public record CategoryDetail(
      String macroCategory,
      String label,
      BigDecimal total,
      List<SubcategorySummary> subcategories,
      List<TransactionResponse> transactions
  ) {}
  public record MerchantDetail(
      String merchant,
      String label,
      BigDecimal total,
      List<TransactionResponse> transactions
  ) {}
}

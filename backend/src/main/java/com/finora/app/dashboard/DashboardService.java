package com.finora.app.dashboard;

import com.finora.app.category.*;
import com.finora.app.shared.error.ApiException;
import com.finora.app.transaction.*;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

@Service
public class DashboardService {
  private final TransactionRepository repo;
  private final CategoryClassifier classifier;

  public DashboardService(TransactionRepository repo, CategoryClassifier classifier) {
    this.repo = repo;
    this.classifier = classifier;
  }

  public DashboardResponse dashboard(Long userId, Integer year, Integer month) {
    YearMonth ym = YearMonth.of(year == null ? LocalDate.now().getYear() : year,
        month == null ? LocalDate.now().getMonthValue() : month);
    List<Transaction> tx = repo.findByUserIdAndDateBetweenOrderByDateDesc(userId, ym.atDay(1), ym.atEndOfMonth());
    BigDecimal expenses = sum(tx, "EXPENSE");
    BigDecimal income = sum(tx, "INCOME");
    List<Transaction> expensesOnly = tx.stream().filter(this::isExpense).toList();
    Map<MacroCategory, BigDecimal> macroTotals = expensesOnly.stream().collect(Collectors
        .groupingBy(this::macroCategory, () -> new EnumMap<>(MacroCategory.class),
            Collectors.reducing(BigDecimal.ZERO, t -> t.amount, BigDecimal::add)));
    Map<String, BigDecimal> cats = macroTotals.entrySet().stream()
        .sorted(Map.Entry.<MacroCategory, BigDecimal>comparingByValue().reversed())
        .collect(Collectors.toMap(e -> e.getKey().label(), Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    List<DashboardResponse.CategorySummary> macroCategories = macroTotals.entrySet().stream()
        .sorted(Map.Entry.<MacroCategory, BigDecimal>comparingByValue().reversed())
        .map(e -> new DashboardResponse.CategorySummary(e.getKey().name(), e.getKey().label(), e.getValue(),
            percentage(e.getValue(), expenses)))
        .toList();
    Map<LocalDate, BigDecimal> daily = tx.stream().filter(t -> "EXPENSE".equals(t.type))
        .collect(Collectors.groupingBy(t -> t.date, TreeMap::new,
            Collectors.reducing(BigDecimal.ZERO, t -> t.amount, BigDecimal::add)));
    int elapsedDays = ym.equals(YearMonth.now()) ? LocalDate.now().getDayOfMonth() : ym.lengthOfMonth();
    BigDecimal dailyAverage = expenses.divide(BigDecimal.valueOf(Math.max(1, elapsedDays)), 2, RoundingMode.HALF_UP);
    BigDecimal projection = ym.equals(YearMonth.now())
        ? dailyAverage.multiply(BigDecimal.valueOf(ym.lengthOfMonth())).setScale(2, RoundingMode.HALF_UP)
        : expenses;
    String[] insight = insight(macroCategories);
    return new DashboardResponse(ym.toString(), expenses, income, income.subtract(expenses), dailyAverage, projection,
        cats, macroCategories, insight[0], insight[1], daily, tx.stream().limit(8).map(TransactionResponse::from).toList(),
        tx.stream().filter(t -> t.recurring).map(TransactionResponse::from).toList());
  }

  public DashboardResponse.CategoryDetail categoryDetail(Long userId, String macroCategory, Integer year, Integer month) {
    MacroCategory requested;
    try {
      requested = MacroCategory.valueOf(macroCategory);
    } catch (IllegalArgumentException e) {
      throw new ApiException(400, "INVALID_MACRO_CATEGORY", "Categoria no valida");
    }
    YearMonth ym = YearMonth.of(year == null ? LocalDate.now().getYear() : year,
        month == null ? LocalDate.now().getMonthValue() : month);
    List<Transaction> matching = repo.findByUserIdAndDateBetweenOrderByDateDesc(userId, ym.atDay(1), ym.atEndOfMonth())
        .stream()
        .filter(this::isExpense)
        .filter(t -> requested == macroCategory(t))
        .toList();
    BigDecimal total = matching.stream().map(t -> t.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    List<DashboardResponse.SubcategorySummary> subcategories = matching.stream()
        .collect(Collectors.groupingBy(t -> t.category == null || t.category.isBlank() ? "Otros" : t.category,
            Collectors.reducing(BigDecimal.ZERO, t -> t.amount, BigDecimal::add)))
        .entrySet().stream()
        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
        .map(e -> new DashboardResponse.SubcategorySummary(e.getKey(), e.getValue()))
        .toList();
    return new DashboardResponse.CategoryDetail(requested.name(), requested.label(), total, subcategories,
        matching.stream().map(TransactionResponse::from).toList());
  }

  private BigDecimal sum(List<Transaction> tx, String type) {
    return tx.stream().filter(t -> type.equals(t.type)).map(t -> t.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private boolean isExpense(Transaction transaction) {
    return "EXPENSE".equals(transaction.type);
  }

  private MacroCategory macroCategory(Transaction transaction) {
    if (transaction.macroCategory != null && !transaction.macroCategory.isBlank()) {
      try {
        return MacroCategory.valueOf(transaction.macroCategory);
      } catch (IllegalArgumentException ignored) {
        return MacroCategory.OTROS;
      }
    }
    return classifier.classify(transaction.merchant, transaction.description, transaction.category).macroCategory();
  }

  private double percentage(BigDecimal value, BigDecimal total) {
    if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
      return 0;
    }
    return value.multiply(BigDecimal.valueOf(100)).divide(total, 4, RoundingMode.HALF_UP).doubleValue();
  }

  private String[] insight(List<DashboardResponse.CategorySummary> categories) {
    if (categories.isEmpty()) {
      return new String[] {"Una señal para este mes", "No tenemos suficientes movimientos todavia para darte una recomendacion."};
    }
    DashboardResponse.CategorySummary largest = categories.get(0);
    if (MacroCategory.OTROS.name().equals(largest.macroCategory())) {
      return new String[] {"Podemos entender mejor tus gastos",
          "Hay movimientos que Sarela todavia no pudo clasificar."};
    }
    return new String[] {largest.label() + " lidera tus gastos",
        "Representa el " + Math.round(largest.percentage()) + "% de tus gastos de este mes."};
  }
}

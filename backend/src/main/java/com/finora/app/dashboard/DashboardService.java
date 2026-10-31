package com.finora.app.dashboard;

import com.finora.app.admin.ManagedCategory;
import com.finora.app.admin.ManagedCategoryRepository;
import com.finora.app.merchant.MerchantNormalizer;
import com.finora.app.shared.error.ApiException;
import com.finora.app.transaction.Transaction;
import com.finora.app.transaction.TransactionRepository;
import com.finora.app.transaction.TransactionResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  private final TransactionRepository repo;
  private static final String UNCATEGORIZED = "UNCATEGORIZED";
  private final ManagedCategoryRepository catalog;

  private Map<Long, ManagedCategory> categoryCatalog(List<Transaction> rows) {
    var ids = rows.stream().map(t -> t.categoryId).filter(java.util.Objects::nonNull).distinct().toList();
    var result = new LinkedHashMap<Long, ManagedCategory>();
    catalog.findAllById(ids).forEach(c -> result.put(c.id, c));
    return result;
  }

  private String categoryKey(Transaction t, Map<Long, ManagedCategory> categories) {
    return categories.containsKey(t.categoryId) ? "CAT_" + t.categoryId : UNCATEGORIZED;
  }

  private String label(String key, Map<Long, ManagedCategory> categories) {
    if (UNCATEGORIZED.equals(key)) return "Sin categoría";
    if (key != null && key.matches("CAT_[1-9][0-9]*")) {
      try {
        var category = categories.get(Long.parseLong(key.substring(4)));
        if (category != null) return category.name;
      } catch (NumberFormatException ignored) {}
    }
    throw new ApiException(400, "INVALID_CATEGORY", "Categoría no válida");
  }

  private TransactionResponse response(Transaction t, Map<Long, ManagedCategory> categories) {
    var original = TransactionResponse.from(t);
    if (!isExpense(t)) return original;
    var category = categories.get(t.categoryId);
    return new TransactionResponse(original.id(), original.userId(), original.description(),
        original.amount(), original.date(), category == null ? "Sin categoría" : category.name,
        categoryKey(t, categories), original.type(), original.source(), original.externalId(),
        original.merchant(), original.recurring(), original.createdAt(), original.currency(),
        category == null ? null : category.id, original.classificationSource(),
        category == null ? "PENDING" : original.reviewStatus());
  }

  private String currency(Transaction t){return t.currency==null?"UNK":t.currency;}
  private String selectedCurrency(List<Transaction> rows,String requested){var values=rows.stream().map(this::currency).distinct().sorted().toList();if(requested!=null&&!requested.isBlank()){if(!requested.matches("[A-Z]{3}"))throw new ApiException(400,"INVALID_CURRENCY","Moneda inválida");return requested;}return values.contains("PEN")?"PEN":values.isEmpty()?"PEN":values.get(0);}


  public DashboardService(TransactionRepository repo, ManagedCategoryRepository catalog) {
    this.repo = repo;
    this.catalog = catalog;
  }

  public DashboardResponse dashboard(Long userId,Integer year,Integer month){return dashboard(userId,year,month,null);}
  public DashboardResponse dashboard(Long userId, Integer year, Integer month, String requestedCurrency) {
    YearMonth ym = yearMonth(year, month);
    List<Transaction> all = repo.findByUserIdAndDateBetweenOrderByDateDesc(userId, ym.atDay(1), ym.atEndOfMonth());
    String chosen=selectedCurrency(all,requestedCurrency);
    List<Transaction> tx=all.stream().filter(t->chosen.equals(currency(t))).toList();
    var categoryCatalog = categoryCatalog(tx);
    BigDecimal expenses = sum(tx, "EXPENSE");
    BigDecimal income = sum(tx, "INCOME");
    List<Transaction> expensesOnly = tx.stream().filter(this::isExpense).toList();
    int expenseCount = expensesOnly.size();
    BigDecimal averageTicket = expenseCount == 0
        ? BigDecimal.ZERO
        : expenses.divide(BigDecimal.valueOf(expenseCount), 2, RoundingMode.HALF_UP);

    Map<String, BigDecimal> macroTotals = expensesOnly.stream().collect(Collectors
        .groupingBy(t -> categoryKey(t, categoryCatalog), LinkedHashMap::new,
            Collectors.reducing(BigDecimal.ZERO, t -> t.amount, BigDecimal::add)));
    Map<String, BigDecimal> cats = macroTotals.entrySet().stream()
        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
        .collect(Collectors.toMap(e -> label(e.getKey(), categoryCatalog), Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    List<DashboardResponse.CategorySummary> macroCategories = macroTotals.entrySet().stream()
        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
        .map(e -> new DashboardResponse.CategorySummary(e.getKey(), label(e.getKey(), categoryCatalog), e.getValue(),
            percentage(e.getValue(), expenses),
            UNCATEGORIZED.equals(e.getKey()) ? "#A4B5AE" : categoryCatalog.get(Long.parseLong(e.getKey().substring(4))).color))
        .toList();
    List<DashboardResponse.MerchantSummary> merchants = merchantSummaries(expensesOnly, expenses);

    Map<LocalDate, BigDecimal> daily = expensesOnly.stream()
        .collect(Collectors.groupingBy(t -> t.date, TreeMap::new,
            Collectors.reducing(BigDecimal.ZERO, t -> t.amount, BigDecimal::add)));
    int elapsedDays = ym.equals(YearMonth.now()) ? LocalDate.now().getDayOfMonth() : ym.lengthOfMonth();
    BigDecimal dailyAverage = expenses.divide(BigDecimal.valueOf(Math.max(1, elapsedDays)), 2, RoundingMode.HALF_UP);
    BigDecimal projection = ym.equals(YearMonth.now())
        ? dailyAverage.multiply(BigDecimal.valueOf(ym.lengthOfMonth())).setScale(2, RoundingMode.HALF_UP)
        : expenses;
    String[] insight = insight(macroCategories, merchants);
    return new DashboardResponse(ym.toString(), expenses, income, income.subtract(expenses), expenseCount, averageTicket,
        dailyAverage, projection,
        cats, macroCategories, merchants, insight[0], insight[1], daily,
        tx.stream().limit(8).map(t -> response(t, categoryCatalog)).toList(),
        tx.stream().filter(t -> t.recurring).map(t -> response(t, categoryCatalog)).toList(),chosen,all.stream().map(this::currency).distinct().sorted().toList());
  }

  public DashboardResponse.CategoryDetail categoryDetail(Long uid,String key,Integer year,Integer month){return categoryDetail(uid,key,year,month,null);}
  public DashboardResponse.CategoryDetail categoryDetail(Long uid,String key,Integer year,Integer month,String requestedCurrency){
    YearMonth ym = yearMonth(year, month);
    var all = repo.findByUserIdAndDateBetweenOrderByDateDesc(uid, ym.atDay(1), ym.atEndOfMonth());
    var categoryCatalog = categoryCatalog(all);
    // Resolve a valid empty catalog category too; never reconstruct a deleted label.
    if (key != null && key.matches("CAT_[1-9][0-9]*")) {
      try {
        catalog.findById(Long.parseLong(key.substring(4))).ifPresent(c -> categoryCatalog.put(c.id, c));
      } catch (NumberFormatException ignored) {}
    }
    String categoryLabel = label(key, categoryCatalog);
    String chosen = selectedCurrency(all, requestedCurrency);
    var matching = all.stream().filter(this::isExpense)
        .filter(t -> key.equals(categoryKey(t, categoryCatalog)) && chosen.equals(currency(t))).toList();
    var total = matching.stream().map(t -> t.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    var sub = matching.isEmpty() ? List.<DashboardResponse.SubcategorySummary>of()
        : List.of(new DashboardResponse.SubcategorySummary(categoryLabel, total));
    return new DashboardResponse.CategoryDetail(key, categoryLabel, total, sub,
        matching.stream().map(t -> response(t, categoryCatalog)).toList());
  }

  public DashboardResponse.MerchantDetail merchantDetail(Long uid,String merchant,Integer year,Integer month){return merchantDetail(uid,merchant,year,month,null);}
  public DashboardResponse.MerchantDetail merchantDetail(Long userId, String merchant, Integer year, Integer month,String requestedCurrency) {
    String requested = MerchantNormalizer.normalize(merchant);
    YearMonth ym = yearMonth(year, month);
    var all=repo.findByUserIdAndDateBetweenOrderByDateDesc(userId, ym.atDay(1), ym.atEndOfMonth());String chosen=selectedCurrency(all,requestedCurrency);
    List<Transaction> matching = all.stream().filter(t->chosen.equals(currency(t)))
        .filter(this::isExpense)
        .filter(t -> requested.equals(merchant(t)))
        .toList();
    var categoryCatalog = categoryCatalog(matching);
    BigDecimal total = matching.stream().map(t -> t.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new DashboardResponse.MerchantDetail(requested, requested, total,
        matching.stream().map(t -> response(t, categoryCatalog)).toList());
  }

  private YearMonth yearMonth(Integer year, Integer month) {
    return YearMonth.of(year == null ? LocalDate.now().getYear() : year,
        month == null ? LocalDate.now().getMonthValue() : month);
  }

  private BigDecimal sum(List<Transaction> tx, String type) {
    return tx.stream().filter(t -> type.equals(t.type)).map(t -> t.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private boolean isExpense(Transaction transaction) {
    return "EXPENSE".equals(transaction.type);
  }

  private String merchant(Transaction transaction) {
    return MerchantNormalizer.normalize(transaction.merchant == null || transaction.merchant.isBlank()
        ? transaction.description
        : transaction.merchant);
  }

  private List<DashboardResponse.MerchantSummary> merchantSummaries(List<Transaction> expensesOnly, BigDecimal expenses) {
    Map<String, BigDecimal> totals = expensesOnly.stream().collect(Collectors.groupingBy(this::merchant,
        Collectors.reducing(BigDecimal.ZERO, t -> t.amount, BigDecimal::add)));
    List<Map.Entry<String, BigDecimal>> sorted = totals.entrySet().stream()
        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
        .toList();
    List<DashboardResponse.MerchantSummary> result = new ArrayList<>();
    sorted.stream().limit(5).forEach(e -> result.add(new DashboardResponse.MerchantSummary(e.getKey(), e.getKey(),
        e.getValue(), percentage(e.getValue(), expenses))));
    BigDecimal rest = sorted.stream().skip(5).map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (rest.compareTo(BigDecimal.ZERO) > 0) {
      result.add(new DashboardResponse.MerchantSummary("__OTHER_MERCHANTS__", "Otros comercios", rest,
          percentage(rest, expenses)));
    }
    return result;
  }

  private double percentage(BigDecimal value, BigDecimal total) {
    if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
      return 0;
    }
    return value.multiply(BigDecimal.valueOf(100)).divide(total, 4, RoundingMode.HALF_UP).doubleValue();
  }

  private String[] insight(List<DashboardResponse.CategorySummary> categories,
      List<DashboardResponse.MerchantSummary> merchants) {
    if (categories.isEmpty()) {
      return new String[] {"Una senal para este mes",
          "No tenemos suficientes movimientos todavia para darte una recomendacion."};
    }
    DashboardResponse.CategorySummary largest = categories.get(0);
    if (UNCATEGORIZED.equals(largest.macroCategory())) {
      return new String[] {"Podemos entender mejor tus gastos",
          "Hay movimientos que Sarela todavia puede clasificar mejor."};
    }
    if (!merchants.isEmpty() && !"__OTHER_MERCHANTS__".equals(merchants.get(0).merchant())) {
      DashboardResponse.MerchantSummary merchant = merchants.get(0);
      return new String[] {"Tu mayor comercio fue " + merchant.label(),
          "El " + Math.round(merchant.percentage()) + "% de tus gastos se concentro en " + merchant.label() + "."};
    }
    return new String[] {"Tu mayor categoria es " + largest.label(),
        "Representa el " + Math.round(largest.percentage()) + "% de tus gastos de este mes."};
  }
}

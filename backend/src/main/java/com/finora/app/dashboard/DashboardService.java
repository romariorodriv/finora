package com.finora.app.dashboard;

import com.finora.app.category.CategoryClassifier;
import com.finora.app.category.MacroCategory;
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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  private final TransactionRepository repo;
  private final CategoryClassifier classifier;
  private com.finora.app.admin.ManagedCategoryRepository catalog;
  @org.springframework.beans.factory.annotation.Autowired public void setCatalog(com.finora.app.admin.ManagedCategoryRepository c){catalog=c;}
  private String categoryKey(Transaction t){return t.categoryId==null?macroCategory(t).name():"CAT_"+t.categoryId;}
  private String label(String key){if(key.startsWith("CAT_")&&catalog!=null)return catalog.findById(Long.parseLong(key.substring(4))).map(c->c.name).orElse("Otros");return MacroCategory.valueOf(key).label();}
  private String color(String key){return key.startsWith("CAT_")&&catalog!=null?catalog.findById(Long.parseLong(key.substring(4))).map(c->c.color).orElse(null):null;}
  private String currency(Transaction t){return t.currency==null?"UNK":t.currency;}
  private String selectedCurrency(List<Transaction> rows,String requested){var values=rows.stream().map(this::currency).distinct().sorted().toList();if(requested!=null&&!requested.isBlank()){if(!requested.matches("[A-Z]{3}"))throw new ApiException(400,"INVALID_CURRENCY","Moneda inválida");return requested;}return values.contains("PEN")?"PEN":values.isEmpty()?"PEN":values.get(0);}


  public DashboardService(TransactionRepository repo, CategoryClassifier classifier) {
    this.repo = repo;
    this.classifier = classifier;
  }

  public DashboardResponse dashboard(Long userId,Integer year,Integer month){return dashboard(userId,year,month,null);}
  public DashboardResponse dashboard(Long userId, Integer year, Integer month, String requestedCurrency) {
    YearMonth ym = yearMonth(year, month);
    List<Transaction> all = repo.findByUserIdAndDateBetweenOrderByDateDesc(userId, ym.atDay(1), ym.atEndOfMonth());
    String chosen=selectedCurrency(all,requestedCurrency);
    List<Transaction> tx=all.stream().filter(t->chosen.equals(currency(t))).toList();
    BigDecimal expenses = sum(tx, "EXPENSE");
    BigDecimal income = sum(tx, "INCOME");
    List<Transaction> expensesOnly = tx.stream().filter(this::isExpense).toList();
    int expenseCount = expensesOnly.size();
    BigDecimal averageTicket = expenseCount == 0
        ? BigDecimal.ZERO
        : expenses.divide(BigDecimal.valueOf(expenseCount), 2, RoundingMode.HALF_UP);

    Map<String, BigDecimal> macroTotals = expensesOnly.stream().collect(Collectors
        .groupingBy(this::categoryKey, LinkedHashMap::new,
            Collectors.reducing(BigDecimal.ZERO, t -> t.amount, BigDecimal::add)));
    Map<String, BigDecimal> cats = macroTotals.entrySet().stream()
        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
        .collect(Collectors.toMap(e -> label(e.getKey()), Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    List<DashboardResponse.CategorySummary> macroCategories = macroTotals.entrySet().stream()
        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
        .map(e -> new DashboardResponse.CategorySummary(e.getKey(), label(e.getKey()), e.getValue(),
            percentage(e.getValue(), expenses),color(e.getKey())))
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
        tx.stream().limit(8).map(TransactionResponse::from).toList(),
        tx.stream().filter(t -> t.recurring).map(TransactionResponse::from).toList(),chosen,all.stream().map(this::currency).distinct().sorted().toList());
  }

  public DashboardResponse.CategoryDetail categoryDetail(Long uid,String key,Integer year,Integer month){return categoryDetail(uid,key,year,month,null);}
  public DashboardResponse.CategoryDetail categoryDetail(Long uid,String key,Integer year,Integer month,String requestedCurrency){
    String categoryLabel;try{categoryLabel=label(key);}catch(Exception e){throw new ApiException(400,"INVALID_CATEGORY","Categoría no válida");}
    YearMonth ym=yearMonth(year,month);var all=repo.findByUserIdAndDateBetweenOrderByDateDesc(uid,ym.atDay(1),ym.atEndOfMonth());String chosen=selectedCurrency(all,requestedCurrency);
    var matching=all.stream().filter(this::isExpense).filter(t->key.equals(categoryKey(t))&&chosen.equals(currency(t))).toList();
    var total=matching.stream().map(t->t.amount).reduce(BigDecimal.ZERO,BigDecimal::add);
    var sub=matching.stream().collect(Collectors.groupingBy(t->key.startsWith("CAT_")?t.category:categoryLabel(macroCategory(t)),Collectors.reducing(BigDecimal.ZERO,t->t.amount,BigDecimal::add))).entrySet().stream().sorted(Map.Entry.<String,BigDecimal>comparingByValue().reversed()).map(e->new DashboardResponse.SubcategorySummary(e.getKey(),e.getValue())).toList();
    return new DashboardResponse.CategoryDetail(key,categoryLabel,total,sub,matching.stream().map(TransactionResponse::from).toList());
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
    BigDecimal total = matching.stream().map(t -> t.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new DashboardResponse.MerchantDetail(requested, requested, total,
        matching.stream().map(TransactionResponse::from).toList());
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

  private MacroCategory macroCategory(Transaction transaction) {
    if (transaction.macroCategory != null && !transaction.macroCategory.isBlank()) {
      try {
        return MacroCategory.valueOf(transaction.macroCategory);
      } catch (IllegalArgumentException ignored) {
        // Historical macro categories are intentionally re-derived from real transaction data.
      }
    }
    return classifier.classify(transaction.merchant, transaction.description, transaction.category).macroCategory();
  }

  private String categoryLabel(MacroCategory category) {
    return category.label();
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
    if (MacroCategory.OTROS.name().equals(largest.macroCategory())) {
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

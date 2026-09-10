package com.finora.app.dashboard;

import com.finora.app.transaction.*;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

@Service
public class DashboardService {
  private final TransactionRepository repo;

  public DashboardService(TransactionRepository repo) {
    this.repo = repo;
  }

  public DashboardResponse dashboard(Long userId, Integer year, Integer month) {
    YearMonth ym = YearMonth.of(year == null ? LocalDate.now().getYear() : year,
        month == null ? LocalDate.now().getMonthValue() : month);
    List<Transaction> tx = repo.findByUserIdAndDateBetweenOrderByDateDesc(userId, ym.atDay(1), ym.atEndOfMonth());
    BigDecimal expenses = sum(tx, "EXPENSE");
    BigDecimal income = sum(tx, "INCOME");
    Map<String, BigDecimal> cats = tx.stream().filter(t -> "EXPENSE".equals(t.type)).collect(Collectors
        .groupingBy(t -> t.category, Collectors.reducing(BigDecimal.ZERO, t -> t.amount, BigDecimal::add)));
    Map<LocalDate, BigDecimal> daily = tx.stream().filter(t -> "EXPENSE".equals(t.type))
        .collect(Collectors.groupingBy(t -> t.date, TreeMap::new,
            Collectors.reducing(BigDecimal.ZERO, t -> t.amount, BigDecimal::add)));
    int elapsedDays = ym.equals(YearMonth.now()) ? LocalDate.now().getDayOfMonth() : ym.lengthOfMonth();
    BigDecimal dailyAverage = expenses.divide(BigDecimal.valueOf(Math.max(1, elapsedDays)), 2, RoundingMode.HALF_UP);
    BigDecimal projection = ym.equals(YearMonth.now())
        ? dailyAverage.multiply(BigDecimal.valueOf(ym.lengthOfMonth())).setScale(2, RoundingMode.HALF_UP)
        : expenses;
    return new DashboardResponse(ym.toString(), expenses, income, income.subtract(expenses), dailyAverage, projection,
        cats, daily, tx.stream().limit(8).map(TransactionResponse::from).toList(),
        tx.stream().filter(t -> t.recurring).map(TransactionResponse::from).toList());
  }

  private BigDecimal sum(List<Transaction> tx, String type) {
    return tx.stream().filter(t -> type.equals(t.type)).map(t -> t.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}

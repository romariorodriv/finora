package com.finora.app.dashboard;
import com.finora.app.transaction.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import java.math.*; import java.time.*; import java.util.*; import java.util.stream.*;

@RestController @RequestMapping("/api/dashboard")
public class DashboardController {
 private final TransactionRepository repo; public DashboardController(TransactionRepository r){repo=r;}
 @GetMapping public Map<String,Object> dashboard(Authentication a,@RequestParam(required=false)Integer year,@RequestParam(required=false)Integer month){
  YearMonth ym=YearMonth.of(year==null?LocalDate.now().getYear():year,month==null?LocalDate.now().getMonthValue():month);Long uid=(Long)a.getPrincipal();
  List<Transaction> tx=repo.findByUserIdAndDateBetweenOrderByDateDesc(uid,ym.atDay(1),ym.atEndOfMonth());
  BigDecimal expenses=tx.stream().filter(t->"EXPENSE".equals(t.type)).map(t->t.amount).reduce(BigDecimal.ZERO,BigDecimal::add);
  BigDecimal income=tx.stream().filter(t->"INCOME".equals(t.type)).map(t->t.amount).reduce(BigDecimal.ZERO,BigDecimal::add);
  Map<String,BigDecimal> cats=tx.stream().filter(t->"EXPENSE".equals(t.type)).collect(Collectors.groupingBy(t->t.category,Collectors.reducing(BigDecimal.ZERO,t->t.amount,BigDecimal::add)));
  Map<LocalDate,BigDecimal> daily=tx.stream().filter(t->"EXPENSE".equals(t.type)).collect(Collectors.groupingBy(t->t.date,TreeMap::new,Collectors.reducing(BigDecimal.ZERO,t->t.amount,BigDecimal::add)));
  BigDecimal projection=expenses.multiply(BigDecimal.valueOf(ym.lengthOfMonth())).divide(BigDecimal.valueOf(Math.max(1,LocalDate.now().getDayOfMonth())),2,RoundingMode.HALF_UP);
  return Map.of("month",ym.toString(),"expenses",expenses,"income",income,"balance",income.subtract(expenses),"dailyAverage",expenses.divide(BigDecimal.valueOf(Math.max(1,LocalDate.now().getDayOfMonth())),2,RoundingMode.HALF_UP),"projection",projection,"categories",cats,"daily",daily,"transactions",tx.stream().limit(8).toList(),"recurring",tx.stream().filter(t->t.recurring).toList());
 }
}

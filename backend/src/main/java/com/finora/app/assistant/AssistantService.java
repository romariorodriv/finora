package com.finora.app.assistant;

import com.finora.app.transaction.*;
import com.finora.app.shared.error.ApiException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.*;

@Service
public class AssistantService {
  private final TransactionRepository repo;
  private final com.finora.app.admin.ManagedCategoryRepository catalog;
  public AssistantService(TransactionRepository repo, com.finora.app.admin.ManagedCategoryRepository catalog) { this.repo = repo; this.catalog = catalog; }
  private String normalize(String text) { return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "").trim().replaceAll(" +", " "); }


  public AssistantDtos.Answer ask(Long userId, AssistantDtos.Question question) {
    String message = question == null || question.message() == null ? "" : normalize(question.message());
    boolean totalQuestion = Set.of("cuanto llevo este mes", "cuanto gaste este mes", "cuanto he gastado este mes").contains(message);
    boolean categoryQuestion = Set.of("en que gaste mas", "cual es mi categoria mas alta", "en que categoria gaste mas").contains(message);
    boolean recurringQuestion = Set.of("que pagos se repiten", "que pagos recurrentes tengo").contains(message);
    if(!totalQuestion && !categoryQuestion && !recurringQuestion) return new AssistantDtos.Answer(
        "Esa pregunta no está soportada. Puedo resumir gastos del mes, la categoria con mas gastos y movimientos marcados como recurrentes, separados por moneda.",
        List.of("Cuanto llevo este mes?", "En que gaste mas?", "Que pagos se repiten?"), "LOCAL_ANALYTICS");
    String requested = question.currency();
    if (requested != null && !requested.isBlank()) {
      try { java.util.Currency.getInstance(requested); }
      catch (IllegalArgumentException e) { throw new ApiException(400,"INVALID_CURRENCY","Moneda ISO no valida."); }
    }
    YearMonth ym = YearMonth.now(ZoneId.of("America/Lima"));
    var expenses = repo.findByUserIdAndDateBetweenOrderByDateDesc(userId, ym.atDay(1), ym.atEndOfMonth())
        .stream().filter(t -> "EXPENSE".equals(t.type))
        .filter(t -> requested == null || requested.isBlank() || requested.equals(t.currency)).toList();
    var ids = expenses.stream().map(t -> t.categoryId).filter(Objects::nonNull).distinct().toList();
    var names = new HashMap<Long,String>();
    catalog.findAllById(ids).forEach(c -> names.put(c.id,c.name));
    if(recurringQuestion) {
      var recurring = expenses.stream().filter(t -> t.recurring).map(t ->
          (t.merchant == null ? t.description : t.merchant) + " (" + (t.currency == null ? "Moneda desconocida" : t.currency) + " " + t.amount.setScale(2) + ")").toList();
      String answer = recurring.isEmpty() ? "No hay gastos marcados como recurrentes este mes." :
          "Gastos registrados y marcados como recurrentes este mes: " + String.join(", ",recurring) + ". No confirma frecuencia ni pagos futuros.";
      return new AssistantDtos.Answer(answer,List.of("Cuanto llevo este mes?","En que gaste mas?"),"LOCAL_ANALYTICS");
    }
    // Unknown legacy currencies remain separate. Never assume PEN or convert amounts.
    var byCurrency = expenses.stream().collect(Collectors.groupingBy(t -> t.currency == null ? "Moneda desconocida" : t.currency, TreeMap::new, Collectors.toList()));
    var summaries = new ArrayList<String>();
    byCurrency.forEach((currency, rows) -> {
      Map<String, BigDecimal> categories = rows.stream().collect(Collectors.groupingBy(
          t -> names.getOrDefault(t.categoryId,"Sin categoría"),
          TreeMap::new, Collectors.reducing(BigDecimal.ZERO,t -> t.amount,BigDecimal::add)));
      var top = categories.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
      BigDecimal total = categories.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
      summaries.add("Este mes gastaste " + currency + " " + total.setScale(2) +
          (categoryQuestion ? ". Tu categoría más alta es " + top.getKey() + " con " + currency + " " + top.getValue().setScale(2) + "." : "."));
    });
    String answer = summaries.isEmpty() ? "No hay gastos registrados este mes" +
        (requested == null || requested.isBlank() ? "." : " en " + requested + ".") : String.join(" ",summaries);
    if(byCurrency.size()>1)answer += " Los importes se muestran por moneda, sin conversion ni suma entre monedas.";
    return new AssistantDtos.Answer(answer,List.of("En que gaste mas?","Cuanto llevo este mes?"),"LOCAL_ANALYTICS");
  }
}

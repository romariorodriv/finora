package com.finora.app.assistant;

import com.finora.app.transaction.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.*;

@Service
public class AssistantService {
  private final TransactionRepository repo;

  public AssistantService(TransactionRepository repo) {
    this.repo = repo;
  }

  public AssistantDtos.Answer ask(Long userId, AssistantDtos.Question question) {
    YearMonth ym = YearMonth.now();
    var tx = repo.findByUserIdAndDateBetweenOrderByDateDesc(userId, ym.atDay(1), ym.atEndOfMonth());
    Map<String, BigDecimal> categories = tx.stream().filter(t -> "EXPENSE".equals(t.type)).collect(Collectors
        .groupingBy(t -> t.category, Collectors.reducing(BigDecimal.ZERO, t -> t.amount, BigDecimal::add)));
    var top = categories.entrySet().stream().max(Map.Entry.comparingByValue());
    BigDecimal total = categories.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    String answer = top
        .map(e -> "Este mes gastaste S/ " + total + ". Tu categoría más alta es " + e.getKey() + " con S/ "
            + e.getValue() + ". Revisa los últimos movimientos de esa categoría antes de fijar un límite.")
        .orElse("Todavía no tengo movimientos suficientes. Agrega uno o importa una notificación bancaria.");
    return new AssistantDtos.Answer(answer,
        List.of("¿En qué gasté más?", "¿Cuánto llevo este mes?", "¿Qué pagos se repiten?"), "LOCAL_ANALYTICS");
  }
}

package com.finora.app.planning;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Read-only what-if calculation, based only on amounts explicitly confirmed by the user. */
@Service
public class PlanningService {
  public record Obligation(String name, LocalDate dueDate, BigDecimal amount) {}
  public record PreviewRequest(LocalDate asOf, LocalDate nextIncomeDate, BigDecimal confirmedBalance,
                               List<Obligation> obligations, BigDecimal reserve) {}
  public record PreviewResponse(BigDecimal confirmedBalance, BigDecimal committedPayments,
                                BigDecimal reserve, BigDecimal available, BigDecimal dailyLimit,
                                long days, boolean deficit, String note) {}

  public PreviewResponse preview(PreviewRequest request) {
    if (request == null || request.asOf() == null || request.nextIncomeDate() == null
        || request.confirmedBalance() == null) throw new IllegalArgumentException("Faltan datos obligatorios.");
    long days = ChronoUnit.DAYS.between(request.asOf(), request.nextIncomeDate());
    if (days < 1 || days > 366) throw new IllegalArgumentException("La fecha del siguiente ingreso debe estar entre 1 y 366 días después.");
    BigDecimal balance = money(request.confirmedBalance());
    BigDecimal reserve = money(request.reserve() == null ? BigDecimal.ZERO : request.reserve());
    if (balance.signum() < 0 || reserve.signum() < 0) throw new IllegalArgumentException("El saldo y la reserva no pueden ser negativos.");
    List<Obligation> obligations = request.obligations() == null ? List.of() : request.obligations();
    if (obligations.size() > 200) throw new IllegalArgumentException("Demasiadas obligaciones.");
    BigDecimal committed = BigDecimal.ZERO;
    for (Obligation obligation : obligations) {
      if (obligation == null || obligation.amount() == null || obligation.dueDate() == null
          || obligation.name() == null || obligation.name().isBlank())
        throw new IllegalArgumentException("Cada obligación requiere nombre, fecha y monto.");
      BigDecimal amount = money(obligation.amount());
      if (amount.signum() < 0) throw new IllegalArgumentException("No se admiten obligaciones negativas.");
      if (!obligation.dueDate().isBefore(request.asOf()) && obligation.dueDate().isBefore(request.nextIncomeDate()))
        committed = committed.add(amount);
    }
    BigDecimal available = balance.subtract(committed).subtract(reserve);
    BigDecimal daily = available.max(BigDecimal.ZERO).divide(BigDecimal.valueOf(days), 2, RoundingMode.DOWN);
    return new PreviewResponse(balance, committed, reserve, available, daily, days, available.signum() < 0,
        "Estimación basada exclusivamente en saldo y obligaciones confirmados. No incluye gastos o ingresos no informados; no es una garantía.");
  }

  private BigDecimal money(BigDecimal value) {
    if (value.abs().compareTo(new BigDecimal("999999999999.99")) > 0 || value.scale() > 2) throw new IllegalArgumentException("Importe inválido: use como máximo dos decimales.");
    return value.setScale(2, RoundingMode.UNNECESSARY);
  }
}

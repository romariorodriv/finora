package com.finora.app.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finora.app.transaction.Transaction;
import com.finora.app.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
  private final TransactionRepository repository = mock(TransactionRepository.class);
  private final DashboardService service = new DashboardService(repository);

  @Test
  void savingsWithdrawalAppearsInMovementsButDoesNotAffectAccountingMetrics() {
    Transaction expense = transaction("Compra", "100.00", "EXPENSE", "Comida");
    Transaction savings = transaction("Retiro de Wardadito", "22.00", "SAVINGS_WITHDRAWAL", "Ahorro");
    Transaction deposit = transaction("Aporte a Wardadito", "100.00", "SAVINGS_DEPOSIT", "Ahorro");
    Transaction transfer = transaction("Transferencia BCP", "15432.10", "TRANSFER", "Transferencias");
    Transaction cash = transaction("Retiro en cajero BCP", "1800.00", "CASH_WITHDRAWAL", "Efectivo");
    when(repository.findByUserIdAndDateBetweenOrderByDateDesc(any(), any(), any()))
        .thenReturn(List.of(cash, transfer, deposit, savings, expense));

    DashboardResponse dashboard = service.dashboard(7L, 2026, 9);

    assertEquals(new BigDecimal("100.00"), dashboard.expenses());
    assertEquals(BigDecimal.ZERO, dashboard.income());
    assertEquals(new BigDecimal("-100.00"), dashboard.balance());
    assertEquals(1, dashboard.categories().size());
    assertEquals(new BigDecimal("100.00"), dashboard.categories().get("Comida"));
    assertEquals(1, dashboard.daily().size());
    assertEquals(new BigDecimal("100.00"), dashboard.daily().get(LocalDate.of(2026, 9, 10)));
    assertTrue(dashboard.transactions().stream()
        .anyMatch(transaction -> "SAVINGS_WITHDRAWAL".equals(transaction.type())));
    assertTrue(dashboard.transactions().stream()
        .anyMatch(transaction -> "CASH_WITHDRAWAL".equals(transaction.type())));
    assertTrue(dashboard.transactions().stream()
        .anyMatch(transaction -> "TRANSFER".equals(transaction.type())));
  }

  private Transaction transaction(String description, String amount, String type, String category) {
    Transaction transaction = new Transaction();
    transaction.userId = 7L;
    transaction.description = description;
    transaction.amount = new BigDecimal(amount);
    transaction.date = LocalDate.of(2026, 9, 10);
    transaction.type = type;
    transaction.category = category;
    transaction.merchant = description;
    return transaction;
  }
}

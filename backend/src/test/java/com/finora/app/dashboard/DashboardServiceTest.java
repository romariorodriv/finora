package com.finora.app.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finora.app.category.CategoryClassifier;
import com.finora.app.transaction.Transaction;
import com.finora.app.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
  private final TransactionRepository repository = mock(TransactionRepository.class);
  private final DashboardService service = new DashboardService(repository, new CategoryClassifier());

  @Test
  void savingsWithdrawalAppearsInMovementsButDoesNotAffectAccountingMetrics() {
    Transaction expense = transaction("Compra", "100.00", "EXPENSE", "Alimentacion", "Tambo");
    Transaction savings = transaction("Retiro de Wardadito", "22.00", "SAVINGS_WITHDRAWAL", "Ahorro", "Wardadito");
    Transaction deposit = transaction("Aporte a Wardadito", "100.00", "SAVINGS_DEPOSIT", "Ahorro", "Wardadito");
    Transaction transfer = transaction("Transferencia BCP", "15432.10", "TRANSFER", "Transferencias", "BCP");
    Transaction cash = transaction("Retiro en cajero BCP", "1800.00", "CASH_WITHDRAWAL", "Efectivo", "BCP");
    when(repository.findByUserIdAndDateBetweenOrderByDateDesc(any(), any(), any()))
        .thenReturn(List.of(cash, transfer, deposit, savings, expense));

    DashboardResponse dashboard = service.dashboard(7L, 2026, 9);

    assertEquals(new BigDecimal("100.00"), dashboard.expenses());
    assertEquals(1, dashboard.expenseCount());
    assertEquals(new BigDecimal("100.00"), dashboard.averageTicket());
    assertEquals(BigDecimal.ZERO, dashboard.income());
    assertEquals(new BigDecimal("-100.00"), dashboard.balance());
    assertEquals(1, dashboard.categories().size());
    assertEquals(new BigDecimal("100.00"), dashboard.categories().get("Alimentacion"));
    assertEquals("ALIMENTACION", dashboard.macroCategories().get(0).macroCategory());
    assertEquals("Tambo", dashboard.merchants().get(0).merchant());
    assertEquals(1, dashboard.daily().size());
    assertEquals(new BigDecimal("100.00"), dashboard.daily().get(LocalDate.of(2026, 9, 10)));
    assertTrue(dashboard.transactions().stream()
        .anyMatch(transaction -> "SAVINGS_WITHDRAWAL".equals(transaction.type())));
    assertTrue(dashboard.transactions().stream()
        .anyMatch(transaction -> "CASH_WITHDRAWAL".equals(transaction.type())));
    assertTrue(dashboard.transactions().stream()
        .anyMatch(transaction -> "TRANSFER".equals(transaction.type())));
  }

  @Test
  void categoriesMerchantsPercentagesAndDetailsUseOnlyExpenseTransactions() {
    Transaction tambo = transaction("Tambo", "40.00", "EXPENSE", "Alimentacion", "TAMBO 123");
    Transaction oxxo = transaction("OXXO", "20.00", "EXPENSE", "Alimentacion", "OXXO Peru");
    Transaction uber = transaction("Uber", "40.00", "EXPENSE", "Transporte", "UBER *TRIP");
    Transaction income = transaction("Sueldo", "2000.00", "INCOME", "Otros", "Empresa");
    Transaction transfer = transaction("Transferencia", "300.00", "TRANSFER", "Transferencias", "BCP");
    when(repository.findByUserIdAndDateBetweenOrderByDateDesc(7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .thenReturn(List.of(income, transfer, uber, oxxo, tambo));

    DashboardResponse dashboard = service.dashboard(7L, 2026, 9);
    DashboardResponse.CategoryDetail categoryDetail = service.categoryDetail(7L, "ALIMENTACION", 2026, 9);
    DashboardResponse.MerchantDetail merchantDetail = service.merchantDetail(7L, "Tambo", 2026, 9);

    assertEquals(new BigDecimal("100.00"), dashboard.expenses());
    assertEquals(3, dashboard.expenseCount());
    assertEquals(new BigDecimal("33.33"), dashboard.averageTicket());
    assertEquals(new BigDecimal("60.00"), dashboard.categories().get("Alimentacion"));
    assertEquals(60.0, dashboard.macroCategories().get(0).percentage());
    assertEquals(List.of("Alimentacion", "Transporte"), dashboard.macroCategories().stream().map(DashboardResponse.CategorySummary::label).toList());
    assertTrue(dashboard.merchants().stream().map(DashboardResponse.MerchantSummary::label).toList()
        .containsAll(List.of("Tambo", "Uber", "OXXO")));
    assertEquals(40.0, dashboard.merchants().get(0).percentage());
    assertEquals(new BigDecimal("60.00"), categoryDetail.total());
    assertEquals(2, categoryDetail.transactions().size());
    assertEquals(new BigDecimal("40.00"), merchantDetail.total());
    assertEquals(1, merchantDetail.transactions().size());
  }

  @Test
  void topMerchantsCollapseRemainingAsOtherMerchantsAndHistoricalCategoriesAreMapped() {
    List<Transaction> tx = List.of(
        legacy("PedidosYa", "25.00", "DIA_A_DIA"),
        transaction("Uber", "20.00", "EXPENSE", "Transporte", "Uber"),
        transaction("Inkafarma", "15.00", "EXPENSE", "Salud", "Inkafarma"),
        transaction("Claro", "10.00", "EXPENSE", "Servicios", "Claro"),
        transaction("Rappi", "8.00", "EXPENSE", "Alimentacion", "Rappi"),
        transaction("OXXO", "7.00", "EXPENSE", "Alimentacion", "OXXO"),
        transaction("Nuevo", "5.00", "EXPENSE", "Otros", "Comercio Nuevo"));
    when(repository.findByUserIdAndDateBetweenOrderByDateDesc(7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .thenReturn(tx);

    DashboardResponse dashboard = service.dashboard(7L, 2026, 9);

    assertEquals(new BigDecimal("40.00"), dashboard.categories().get("Alimentacion"));
    assertEquals(6, dashboard.merchants().size());
    assertEquals("Otros comercios", dashboard.merchants().get(5).label());
    assertEquals(new BigDecimal("12.00"), dashboard.merchants().get(5).total());
  }

  @Test
  void historicalMacroCategoryIsReclassifiedFromTransactionDataInsteadOfBeingForced() {
    List<Transaction> tx = List.of(
        legacy("Spotify", "5.00", "ESTILO_DE_VIDA", "Suscripciones"),
        legacy("Uber", "20.00", "DIA_A_DIA", "Transporte"),
        legacy("Inkafarma", "15.00", "HOGAR", "Salud"),
        legacy("Tambo", "10.00", "FINANZAS", "Alimentacion"));
    when(repository.findByUserIdAndDateBetweenOrderByDateDesc(7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .thenReturn(tx);

    DashboardResponse dashboard = service.dashboard(7L, 2026, 9);

    assertEquals(new BigDecimal("20.00"), dashboard.categories().get("Transporte"));
    assertEquals(new BigDecimal("15.00"), dashboard.categories().get("Salud"));
    assertEquals(new BigDecimal("10.00"), dashboard.categories().get("Alimentacion"));
    assertEquals(new BigDecimal("5.00"), dashboard.categories().get("Suscripciones"));
    assertTrue(!dashboard.categories().containsKey("Servicios"));
  }

  @Test
  void nullHistoricalMacroCategoryIsReclassifiedAsSubscriptionOnRead() {
    List<Transaction> tx = List.of(
        legacy("Spotify", "5.00", null, "Suscripciones"),
        legacy("PARAMOUNT+", "9.00", null, "Otros"),
        legacy("EBN*PRIME VIDEO", "12.00", null, "Otros"),
        legacy("NETFLIX.COM", "18.00", null, "Otros"));
    when(repository.findByUserIdAndDateBetweenOrderByDateDesc(7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .thenReturn(tx);

    DashboardResponse dashboard = service.dashboard(7L, 2026, 9);
    DashboardResponse.CategoryDetail detail = service.categoryDetail(7L, "SUSCRIPCIONES", 2026, 9);

    assertEquals(new BigDecimal("44.00"), dashboard.categories().get("Suscripciones"));
    assertEquals("SUSCRIPCIONES", dashboard.macroCategories().get(0).macroCategory());
    assertEquals(4, detail.transactions().size());
    assertTrue(!dashboard.categories().containsKey("Otros"));
  }

  @Test
  void expenseCountAndAverageUseAllEligibleExpensesNotTheRecentTransactionsPreview() {
    List<Transaction> tx = java.util.stream.IntStream.rangeClosed(1, 12)
        .mapToObj(i -> transaction("OXXO " + i, "10.00", "EXPENSE", "Alimentacion", "OXXO " + i))
        .toList();
    when(repository.findByUserIdAndDateBetweenOrderByDateDesc(7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .thenReturn(tx);

    DashboardResponse dashboard = service.dashboard(7L, 2026, 9);

    assertEquals(new BigDecimal("120.00"), dashboard.expenses());
    assertEquals(12, dashboard.expenseCount());
    assertEquals(new BigDecimal("10.00"), dashboard.averageTicket());
    assertEquals(8, dashboard.transactions().size());
  }

  private Transaction legacy(String merchant, String amount, String macroCategory) {
    return legacy(merchant, amount, macroCategory, "Supermercado");
  }

  private Transaction legacy(String merchant, String amount, String macroCategory, String category) {
    Transaction transaction = transaction(merchant, amount, "EXPENSE", category, merchant);
    transaction.macroCategory = macroCategory;
    return transaction;
  }

  private Transaction transaction(String description, String amount, String type, String category, String merchant) {
    Transaction transaction = new Transaction();
    transaction.userId = 7L;
    transaction.description = description;
    transaction.amount = new BigDecimal(amount);
    transaction.date = LocalDate.of(2026, 9, 10);
    transaction.type = type;
    transaction.category = category;
    transaction.merchant = merchant;
    return transaction;
  }
}

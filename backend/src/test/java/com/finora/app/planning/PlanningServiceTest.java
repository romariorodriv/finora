package com.finora.app.planning;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlanningServiceTest {
  private final PlanningService service = new PlanningService();
  private final LocalDate today = LocalDate.of(2026, 10, 29);
  @Test void subtractsOnlyUpcomingObligationsAndReserve() {
    var input = new PlanningService.PreviewRequest(today, today.plusDays(10), new BigDecimal("1000.00"),
      List.of(new PlanningService.Obligation("Alquiler",today.plusDays(2),new BigDecimal("350")),
              new PlanningService.Obligation("Ayer",today.minusDays(1),new BigDecimal("100")),
              new PlanningService.Obligation("Próximo sueldo",today.plusDays(10),new BigDecimal("99"))),
      new BigDecimal("50"));
    var result = service.preview(input);
    assertEquals(new BigDecimal("350.00"),result.committedPayments());
    assertEquals(new BigDecimal("600.00"),result.available());
    assertEquals(new BigDecimal("60.00"),result.dailyLimit());
    assertFalse(result.deficit());
  }
  @Test void neverShowsNegativeDailySpendingLimit() {
    var input = new PlanningService.PreviewRequest(today, today.plusDays(5), new BigDecimal("100"),
      List.of(new PlanningService.Obligation("Tarjeta",today.plusDays(1),new BigDecimal("200"))),BigDecimal.ZERO);
    var result = service.preview(input);
    assertTrue(result.deficit());
    assertEquals(new BigDecimal("-100.00"),result.available());
    assertEquals(new BigDecimal("0.00"),result.dailyLimit());
  }
  @Test void rejectsInvalidDatesAndPrecision() {
    assertThrows(IllegalArgumentException.class, () -> service.preview(new PlanningService.PreviewRequest(today,today,BigDecimal.TEN,List.of(),BigDecimal.ZERO)));
    assertThrows(IllegalArgumentException.class, () -> service.preview(new PlanningService.PreviewRequest(today,today.plusDays(2),new BigDecimal("1.234"),List.of(),BigDecimal.ZERO)));
  }
  @Test void roundsDailyLimitDownAndRejectsScientificOverflow() {
    var result=service.preview(new PlanningService.PreviewRequest(today,today.plusDays(3),BigDecimal.TEN,List.of(),BigDecimal.ZERO));
    assertEquals(new BigDecimal("3.33"),result.dailyLimit());
    assertThrows(IllegalArgumentException.class,()->service.preview(new PlanningService.PreviewRequest(today,today.plusDays(3),new BigDecimal("1e30"),List.of(),BigDecimal.ZERO)));
  }
}

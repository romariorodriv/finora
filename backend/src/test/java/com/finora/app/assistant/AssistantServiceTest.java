package com.finora.app.assistant;

import com.finora.app.transaction.*;
import com.finora.app.admin.ManagedCategoryRepository;
import com.finora.app.shared.error.ApiException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantServiceTest {
 private final TransactionRepository repo=mock(TransactionRepository.class);
 private final ManagedCategoryRepository categories=mock(ManagedCategoryRepository.class);
 private final AssistantService service=new AssistantService(repo,categories);
 @Test void unsupportedQuestionsDoNotReadOrInventFinancialData(){
  assertTrue(service.ask(1L,new AssistantDtos.Question("Predice el dolar")).answer().contains("no está soportada"));
  verifyNoInteractions(repo,categories);
 }
 @Test void acceptsAccentedSupportedQuestionsAndReportsNoExpensesInSelectedCurrency(){
  var ym=YearMonth.now(ZoneId.of("America/Lima"));
  when(repo.findByUserIdAndDateBetweenOrderByDateDesc(1L,ym.atDay(1),ym.atEndOfMonth())).thenReturn(List.of());
  assertEquals("No hay gastos registrados este mes en USD.",service.ask(1L,new AssistantDtos.Question("¿Cuánto llevo este mes?","USD")).answer());
 }
 @Test void rejectsInvalidCurrencyBeforeReadingTransactions(){
  assertThrows(ApiException.class,()->service.ask(1L,new AssistantDtos.Question("Cuanto llevo este mes?","BAD")));
  verifyNoInteractions(repo);
 }
 @Test void excludesIncomeAndKeepsUnknownCurrenciesSeparate(){
  var ym=YearMonth.now(ZoneId.of("America/Lima"));
  var pen=new Transaction();pen.amount=BigDecimal.TEN;pen.category="Old";
  var unknown=new Transaction();unknown.currency=null;unknown.amount=new BigDecimal("20");
  var income=new Transaction();income.type="INCOME";income.amount=new BigDecimal("999");
  when(repo.findByUserIdAndDateBetweenOrderByDateDesc(1L,ym.atDay(1),ym.atEndOfMonth())).thenReturn(List.of(pen,unknown,income));
  var answer=service.ask(1L,new AssistantDtos.Question("Cuanto llevo este mes?")).answer();
  assertTrue(answer.contains("PEN 10.00"));assertTrue(answer.contains("Moneda desconocida 20.00"));assertFalse(answer.contains("999"));assertFalse(answer.contains("30.00"));
 }
}

package com.finora.app.transaction;

import com.finora.app.shared.error.ApiException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionServiceTest {
  private final TransactionRepository repo = mock(TransactionRepository.class);
  private final TransactionService service = new TransactionService(repo);

  @Test
  void updateRejectsTransactionsFromAnotherUser() {
    Transaction existing = transaction(10L, 2L);
    when(repo.findById(10L)).thenReturn(Optional.of(existing));

    ApiException error = assertThrows(ApiException.class,
        () -> service.update(1L, 10L, request()));

    assertEquals(403, error.status);
    verify(repo, never()).save(any());
  }

  @Test
  void deleteRejectsTransactionsFromAnotherUser() {
    Transaction existing = transaction(10L, 2L);
    when(repo.findById(10L)).thenReturn(Optional.of(existing));

    ApiException error = assertThrows(ApiException.class, () -> service.delete(1L, 10L));

    assertEquals(403, error.status);
    verify(repo, never()).delete(any());
  }

  private Transaction transaction(Long id, Long userId) {
    Transaction t = new Transaction();
    t.id = id;
    t.userId = userId;
    t.description = "Taxi";
    t.amount = BigDecimal.TEN;
    t.date = LocalDate.now();
    t.category = "Transporte";
    return t;
  }

  private TransactionRequest request() {
    return new TransactionRequest("Taxi", BigDecimal.TEN, LocalDate.now(), "Transporte", "EXPENSE", "Taxi", false);
  }
}

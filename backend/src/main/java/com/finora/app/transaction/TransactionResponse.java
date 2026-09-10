package com.finora.app.transaction;

import java.math.BigDecimal;
import java.time.*;

public record TransactionResponse(
    Long id,
    Long userId,
    String description,
    BigDecimal amount,
    LocalDate date,
    String category,
    String type,
    String source,
    String externalId,
    String merchant,
    boolean recurring,
    Instant createdAt
) {
  public static TransactionResponse from(Transaction t) {
    return new TransactionResponse(t.id, t.userId, t.description, t.amount, t.date, t.category, t.type, t.source,
        t.externalId, t.merchant, t.recurring, t.createdAt);
  }
}

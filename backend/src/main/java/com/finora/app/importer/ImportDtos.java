package com.finora.app.importer;

import com.finora.app.transaction.TransactionResponse;

public final class ImportDtos {
  private ImportDtos() {}

  public record BankEmailRequest(String sender, String subject, String content, String externalId) {}

  public record BankEmailResponse(TransactionResponse transaction, double confidence, String method) {}
}

package com.finora.app.importer;

import com.finora.app.transaction.TransactionResponse;

public final class ImportDtos {
  private ImportDtos() {}

  public record BankEmailRequest(String sender, String subject, String content, String externalId,java.time.LocalDate date) {
    @Override public String toString(){return "BankEmailRequest[REDACTED]";}
    public BankEmailRequest(String sender,String subject,String content,String externalId){this(sender,subject,content,externalId,null);}
  }

  public record BankEmailResponse(TransactionResponse transaction, double confidence, String method) {}
}

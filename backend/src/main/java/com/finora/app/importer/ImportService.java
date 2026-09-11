package com.finora.app.importer;

import com.finora.app.shared.error.ApiException;
import com.finora.app.transaction.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class ImportService {
  private final BankEmailParser parser;
  private final TransactionRepository repo;

  public ImportService(BankEmailParser parser, TransactionRepository repo) {
    this.parser = parser;
    this.repo = repo;
  }

  @Transactional
  public ImportDtos.BankEmailResponse importBankEmail(Long userId, ImportDtos.BankEmailRequest r) {
    String subject = r.subject() == null ? "" : r.subject();
    String content = r.content() == null ? "" : r.content();
    String emailText = subject + "\n" + content;
    if (!parser.supports(r.sender(), emailText)) {
      throw new ApiException(422, "IMPORT_UNSUPPORTED_EMAIL", "No parece una notificación bancaria compatible");
    }
    BankEmailParser.Parsed parsed = parser.parse(r.sender(), emailText);
    if (parsed.status() == BankEmailParser.OperationStatus.REJECTED) {
      throw new ApiException(422, "IMPORT_REJECTED_OPERATION", "La operación bancaria fue rechazada y no se registró");
    }
    String externalId = r.externalId() == null
        ? UUID.nameUUIDFromBytes((subject + content).getBytes(StandardCharsets.UTF_8)).toString()
        : r.externalId();
    if (repo.existsByUserIdAndExternalId(userId, externalId)) {
      throw new ApiException(409, "IMPORT_DUPLICATE", "Esta notificación ya fue importada");
    }
    Transaction t = new Transaction();
    t.userId = userId;
    t.description = parsed.description();
    t.amount = parsed.amount();
    t.date = parsed.date();
    t.merchant = parsed.merchant();
    t.category = parsed.category();
    t.source = "BANK_EMAIL";
    t.externalId = externalId;
    repo.save(t);
    return new ImportDtos.BankEmailResponse(TransactionResponse.from(t), parsed.confidence(), "RULE_WITH_AI_FALLBACK_READY");
  }
}

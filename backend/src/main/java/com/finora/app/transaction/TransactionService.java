package com.finora.app.transaction;

import com.finora.app.category.CategoryClassifier;
import com.finora.app.merchant.MerchantNormalizer;
import com.finora.app.shared.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
public class TransactionService {
  private final TransactionRepository repo;
  private final CategoryClassifier classifier;

  public TransactionService(TransactionRepository repo, CategoryClassifier classifier) {
    this.repo = repo;
    this.classifier = classifier;
  }

  public List<TransactionResponse> list(Long userId, LocalDate from, LocalDate to) {
    LocalDate end = to == null ? LocalDate.now() : to;
    LocalDate start = from == null ? end.withDayOfMonth(1) : from;
    if (start.isAfter(end)) {
      throw new ApiException(400, "INVALID_DATE_RANGE", "La fecha inicial no puede ser posterior a la fecha final");
    }
    return repo.findByUserIdAndDateBetweenOrderByDateDesc(userId, start, end).stream()
        .map(TransactionResponse::from)
        .toList();
  }

  @Transactional
  public TransactionResponse create(Long userId, TransactionRequest r) {
    Transaction t = new Transaction();
    apply(t, r);
    t.userId = userId;
    return TransactionResponse.from(repo.save(t));
  }

  @Transactional
  public TransactionResponse update(Long userId, Long id, TransactionRequest r) {
    Transaction t = repo.findById(id).orElseThrow(() -> new ApiException(404, "TRANSACTION_NOT_FOUND", "Movimiento no encontrado"));
    if (!userId.equals(t.userId)) {
      throw new ApiException(403, "TRANSACTION_FORBIDDEN", "No puedes modificar un movimiento de otro usuario");
    }
    apply(t, r);
    return TransactionResponse.from(repo.save(t));
  }

  @Transactional
  public void delete(Long userId, Long id) {
    Transaction t = repo.findById(id).orElseThrow(() -> new ApiException(404, "TRANSACTION_NOT_FOUND", "Movimiento no encontrado"));
    if (!userId.equals(t.userId)) {
      throw new ApiException(403, "TRANSACTION_FORBIDDEN", "No puedes eliminar un movimiento de otro usuario");
    }
    repo.delete(t);
  }

  private void apply(Transaction t, TransactionRequest r) {
    t.description = r.description().trim();
    t.amount = r.amount();
    t.date = r.date();
    t.category = r.category();
    t.type = r.type() == null || r.type().isBlank() ? "EXPENSE" : r.type();
    t.merchant = MerchantNormalizer.normalize(r.merchant() == null || r.merchant().isBlank() ? r.description() : r.merchant());
    t.recurring = r.recurring();
    if ("EXPENSE".equals(t.type)) {
      CategoryClassifier.Classification category = classifier.classify(t.merchant, t.description, t.category);
      t.macroCategory = category.macroCategory().name();
      t.category = category.subcategory();
    } else {
      t.macroCategory = null;
    }
  }
}

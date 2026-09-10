package com.finora.app.transaction;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/transactions", "/api/v1/transactions"})
public class TransactionController {
  private final TransactionService service;

  public TransactionController(TransactionService service) {
    this.service = service;
  }

  @GetMapping
  public List<TransactionResponse> list(Authentication a, @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return service.list(uid(a), from, to);
  }

  @PostMapping
  public ResponseEntity<TransactionResponse> create(Authentication a, @Valid @RequestBody TransactionRequest r) {
    return ResponseEntity.status(201).body(service.create(uid(a), r));
  }

  @PutMapping("/{id}")
  public TransactionResponse update(Authentication a, @PathVariable Long id, @Valid @RequestBody TransactionRequest r) {
    return service.update(uid(a), id, r);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(Authentication a, @PathVariable Long id) {
    service.delete(uid(a), id);
    return ResponseEntity.noContent().build();
  }

  private Long uid(Authentication a) {
    return (Long) a.getPrincipal();
  }
}

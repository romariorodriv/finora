package com.finora.app.importer;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/import", "/api/v1/import"})
public class ImportController {
  private final ImportService service;

  public ImportController(ImportService service) {
    this.service = service;
  }

  @PostMapping("/bank-email")
  public ResponseEntity<ImportDtos.BankEmailResponse> email(Authentication a,
      @RequestBody ImportDtos.BankEmailRequest r) {
    return ResponseEntity.status(201).body(service.importBankEmail((Long) a.getPrincipal(), r));
  }
}

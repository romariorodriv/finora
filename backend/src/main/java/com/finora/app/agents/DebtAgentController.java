package com.finora.app.agents;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.List;

/**
 * Session-authenticated backend gateway to the private Python worker.
 * Never pass OAuth credentials or internal service credentials to the browser.
 */
@RestController
@RequestMapping({"/api/v1/agents/debts", "/api/agents/debts"})
public class DebtAgentController {
  private final String workerUrl;
  private final String workerKey;
  private final RestClient client;

  public DebtAgentController(
      @Value("${SARELA_AGENT_URL:}") String workerUrl,
      @Value("${SARELA_AGENT_INTERNAL_KEY:}") String workerKey,
      RestClient.Builder builder) {
    this.workerUrl = workerUrl;
    this.workerKey = workerKey;
    this.client = builder.build();
  }

  public record DebtRequest(
      @NotNull @DecimalMin("1") @DecimalMax("1000000") java.math.BigDecimal balance,
      @NotNull @DecimalMin("0") @DecimalMax("300") java.math.BigDecimal tea,
      @NotNull @Min(1) @Max(120) Integer months
  ) {}

  @PostMapping("/analyze")
  public ResponseEntity<?> analyze(Authentication authentication, @Valid @RequestBody DebtRequest request) {
    if (authentication == null || authentication.getPrincipal() == null) {
      return ResponseEntity.status(401).body(java.util.Map.of("message", "Sesión requerida"));
    }
    if (workerUrl.isBlank() || workerKey.isBlank()) {
      return ResponseEntity.status(503).body(java.util.Map.of(
          "message", "El agente de deudas no está habilitado en este ambiente."));
    }
    try {
      String response = client.post().uri(workerUrl + "/internal/v1/debts/analyze")
          .header("X-Sarela-Agent-Key", workerKey)
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve().body(String.class);
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    } catch (RestClientException ex) {
      // No leak of upstream internals or service secrets to clients.
      return ResponseEntity.status(502).body(java.util.Map.of(
          "message", "El agente no pudo completar el análisis. Intenta de nuevo."));
    }
  }
}

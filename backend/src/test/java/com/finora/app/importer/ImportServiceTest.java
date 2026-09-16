package com.finora.app.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finora.app.category.CategoryClassifier;
import com.finora.app.shared.error.ApiException;
import com.finora.app.transaction.Transaction;
import com.finora.app.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ImportServiceTest {
  @Mock private TransactionRepository repository;
  private ImportService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new ImportService(new PeruBankParser(), repository, new CategoryClassifier());
  }

  @Test
  void completedBcpPurchaseCreatesExpense() {
    when(repository.existsByUserIdAndExternalId(7L, "completed-message")).thenReturn(false);

    service.importBankEmail(7L, new ImportDtos.BankEmailRequest(
        "notificaciones@notificacionesbcp.com.pe",
        "Realizaste un consumo",
        "Realizaste un consumo de S/ 19.00 con tu Tarjeta de Débito BCP en PedidosYa*Montao Nicola.",
        "completed-message"));

    ArgumentCaptor<Transaction> transaction = ArgumentCaptor.forClass(Transaction.class);
    verify(repository).save(transaction.capture());
    assertEquals(new BigDecimal("19.00"), transaction.getValue().amount);
    assertEquals("PedidosYa", transaction.getValue().merchant);
    assertEquals("EXPENSE", transaction.getValue().type);
    assertEquals("ALIMENTACION", transaction.getValue().macroCategory);
    assertEquals("Alimentacion", transaction.getValue().category);
  }

  @Test
  void rejectedBcpPurchaseDoesNotPersistOrParticipateInTransactionDeduplication() {
    ApiException error = assertThrows(ApiException.class, () -> service.importBankEmail(7L,
        new ImportDtos.BankEmailRequest(
            "notificaciones@notificacionesbcp.com.pe",
            "Se rechazó tu compra por fondos insuficientes - Servicio de Notificaciones BCP",
            "Monto: S/ 32.90\nNombre del comercio: DLC*Spotify\nMotivo de rechazo: Fondos Insuficientes",
            "rejected-message")));

    assertEquals("IMPORT_REJECTED_OPERATION", error.code);
    verify(repository, never()).existsByUserIdAndExternalId(any(), any());
    verify(repository, never()).save(any());
  }

  @Test
  void wardaditoWithdrawalIsPersistedAsNonAccountingMovement() {
    when(repository.existsByUserIdAndExternalId(7L, "wardadito-message")).thenReturn(false);

    service.importBankEmail(7L, new ImportDtos.BankEmailRequest(
        "notificaciones@notificacionesbcp.com.pe",
        "Realizaste un retiro de tu wardadito.",
        "Realizaste un retiro de S/ 22.00 en tu wardadito caja de ahorro.",
        "wardadito-message"));

    ArgumentCaptor<Transaction> transaction = ArgumentCaptor.forClass(Transaction.class);
    verify(repository).save(transaction.capture());
    assertEquals("SAVINGS_WITHDRAWAL", transaction.getValue().type);
    assertEquals("Retiro de Wardadito", transaction.getValue().description);
    assertEquals(new BigDecimal("22.00"), transaction.getValue().amount);
  }

  @Test
  void unsupportedPromotionDoesNotPersistOrParticipateInTransactionDeduplication() {
    ApiException error = assertThrows(ApiException.class, () -> service.importBankEmail(7L,
        new ImportDtos.BankEmailRequest(
            "notificaciones@notificacionesbcp.com.pe",
            "STOP: Esto te va a tentar en Sodimac Angamos",
            "Compra tu SOAT y participa por S/ 1000",
            "promo-message")));

    assertEquals("IMPORT_UNSUPPORTED_EMAIL", error.code);
    verify(repository, never()).existsByUserIdAndExternalId(any(), any());
    verify(repository, never()).save(any());
  }

  @Test
  void transferToOtherBankIsPersistedAsTransfer() {
    when(repository.existsByUserIdAndExternalId(7L, "transfer-message")).thenReturn(false);

    service.importBankEmail(7L, new ImportDtos.BankEmailRequest(
        "notificaciones@notificacionesbcp.com.pe",
        "Constancia de Transferencia a Otros Bancos - Servicio de Notificaciones BCP",
        "Importe: S/ 15432.10",
        "transfer-message"));

    ArgumentCaptor<Transaction> transaction = ArgumentCaptor.forClass(Transaction.class);
    verify(repository).save(transaction.capture());
    assertEquals("TRANSFER", transaction.getValue().type);
    assertEquals(new BigDecimal("15432.10"), transaction.getValue().amount);
  }

  @Test
  void cashWithdrawalIsPersistedAsCashWithdrawal() {
    when(repository.existsByUserIdAndExternalId(7L, "cash-message")).thenReturn(false);

    service.importBankEmail(7L, new ImportDtos.BankEmailRequest(
        "notificaciones@notificacionesbcp.com.pe",
        "Realizaste un retiro en un cajero automatico BCP - Servicio de Notificaciones BCP",
        "Retiro de S/ 1800.00 en cajero BCP.",
        "cash-message"));

    ArgumentCaptor<Transaction> transaction = ArgumentCaptor.forClass(Transaction.class);
    verify(repository).save(transaction.capture());
    assertEquals("CASH_WITHDRAWAL", transaction.getValue().type);
    assertEquals(new BigDecimal("1800.00"), transaction.getValue().amount);
  }
}

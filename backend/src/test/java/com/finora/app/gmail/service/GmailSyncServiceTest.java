package com.finora.app.gmail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.finora.app.gmail.client.GmailClient;
import com.finora.app.gmail.dto.GmailDtos;
import com.finora.app.gmail.entity.GmailConnection;
import com.finora.app.gmail.entity.ImportedMessage;
import com.finora.app.gmail.repository.ImportedMessageRepository;
import com.finora.app.importer.PeruBankParser;
import com.finora.app.transaction.TransactionRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GmailSyncServiceTest {
  @Mock private GmailOAuthService oauth;
  @Mock private GmailClient gmail;
  @Mock private ImportedMessageRepository importedMessages;
  @Mock private TransactionRepository transactions;

  @Test
  void rejectedPurchaseIsTrackedButDoesNotCreateTransaction() {
    GmailConnection connection = new GmailConnection();
    connection.id = 3L;
    connection.userId = 7L;
    when(oauth.active(7L)).thenReturn(connection);
    when(oauth.validAccessToken(connection)).thenReturn("access-token");
    when(gmail.search(eq("access-token"), contains("compra"), isNull())).thenReturn(
        new GmailClient.Page(List.of(new GmailClient.MessageRef("rejected-id", "thread-id")), null));
    when(gmail.getMessage("access-token", "rejected-id")).thenReturn(new GmailClient.Message(
        "rejected-id", "thread-id", "notificaciones@notificacionesbcp.com.pe",
        "Se rechazó tu compra por fondos insuficientes - Servicio de Notificaciones BCP",
        "Monto: S/ 32.90\nNombre del comercio: DLC*Spotify\nMotivo: Fondos Insuficientes",
        Instant.parse("2026-09-10T15:00:00Z")));

    GmailSyncService service = new GmailSyncService(
        oauth, gmail, importedMessages, transactions, new PeruBankParser(), 30);
    GmailDtos.SyncResponse result = service.doSync(7L);

    assertEquals(0, result.transactionsCreated());
    assertEquals(1, result.rejected());
    verify(transactions, never()).save(any());
    ArgumentCaptor<ImportedMessage> row = ArgumentCaptor.forClass(ImportedMessage.class);
    verify(importedMessages, org.mockito.Mockito.times(2)).save(row.capture());
    assertEquals(ImportedMessage.Status.REJECTED, row.getValue().processingStatus);
  }

  @Test
  void completedRetryAfterRejectedAttemptIsStillImported() {
    GmailConnection connection = new GmailConnection();
    connection.id = 3L;
    connection.userId = 7L;
    when(oauth.active(7L)).thenReturn(connection);
    when(oauth.validAccessToken(connection)).thenReturn("access-token");
    when(gmail.search(eq("access-token"), contains("compra"), isNull())).thenReturn(new GmailClient.Page(
        List.of(new GmailClient.MessageRef("rejected-id", "thread-1"),
            new GmailClient.MessageRef("completed-id", "thread-2")), null));
    when(gmail.getMessage("access-token", "rejected-id")).thenReturn(new GmailClient.Message(
        "rejected-id", "thread-1", "notificaciones@notificacionesbcp.com.pe",
        "Se rechazó tu compra", "Monto: S/ 32.90\nNombre del comercio: DLC*Spotify\nFondos insuficientes",
        Instant.parse("2026-09-10T15:00:00Z")));
    when(gmail.getMessage("access-token", "completed-id")).thenReturn(new GmailClient.Message(
        "completed-id", "thread-2", "notificaciones@notificacionesbcp.com.pe",
        "Realizaste un consumo", "Realizaste un consumo de S/ 32.90 con tu Tarjeta de Débito BCP en DLC*Spotify.",
        Instant.parse("2026-09-11T15:00:00Z")));

    GmailSyncService service = new GmailSyncService(
        oauth, gmail, importedMessages, transactions, new PeruBankParser(), 30);
    GmailDtos.SyncResponse result = service.doSync(7L);

    assertEquals(1, result.rejected());
    assertEquals(1, result.transactionsCreated());
    verify(transactions, times(1)).save(any());
  }

  @Test
  void unsupportedPromotionIsIgnoredAndDoesNotCreateTransaction() {
    GmailConnection connection = new GmailConnection();
    connection.id = 3L;
    connection.userId = 7L;
    when(oauth.active(7L)).thenReturn(connection);
    when(oauth.validAccessToken(connection)).thenReturn("access-token");
    when(gmail.search(eq("access-token"), contains("compra"), isNull())).thenReturn(
        new GmailClient.Page(List.of(new GmailClient.MessageRef("promo-id", "thread-id")), null));
    when(gmail.getMessage("access-token", "promo-id")).thenReturn(new GmailClient.Message(
        "promo-id", "thread-id", "notificaciones@notificacionesbcp.com.pe",
        "STOP: Esto te va a tentar en Sodimac Angamos",
        "Compra tu SOAT y participa por S/ 1000",
        Instant.parse("2026-09-10T15:00:00Z")));

    GmailSyncService service = new GmailSyncService(
        oauth, gmail, importedMessages, transactions, new PeruBankParser(), 30);
    GmailDtos.SyncResponse result = service.doSync(7L);

    assertEquals(0, result.transactionsCreated());
    assertEquals(0, result.rejected());
    verify(transactions, never()).save(any());
    ArgumentCaptor<ImportedMessage> row = ArgumentCaptor.forClass(ImportedMessage.class);
    verify(importedMessages, times(2)).save(row.capture());
    assertEquals(ImportedMessage.Status.IGNORED, row.getValue().processingStatus);
  }
}

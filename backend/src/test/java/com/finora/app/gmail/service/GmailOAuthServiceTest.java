package com.finora.app.gmail.service;
import com.finora.app.gmail.client.*;
import com.finora.app.gmail.entity.GmailConnection;
import com.finora.app.gmail.repository.*;
import com.finora.app.gmail.security.TokenCipher;
import com.finora.app.shared.error.ApiException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GmailOAuthServiceTest {
  GoogleGmailClient gmail = mock(GoogleGmailClient.class);
  GmailConnectionRepository connections = mock(GmailConnectionRepository.class);
  TokenCipher cipher = new TokenCipher("local-test-only");
  GmailOAuthService service = new GmailOAuthService(mock(GmailOAuthStateRepository.class),
      connections, gmail, cipher, "test-client", "https://example.test/callback", "https://example.test");
  GmailConnection connection() {
    var c = new GmailConnection();
    c.id = 1L; c.userId = 7L; c.gmailAddress = "user@example.test";
    c.encryptedAccessToken = cipher.encrypt("old-access");
    c.encryptedRefreshToken = cipher.encrypt("refresh");
    c.accessTokenExpiresAt = Instant.now().minusSeconds(100);
    return c;
  }
  @Test void invalidGrantRequiresReconnectAndPersistsStatus() {
    var c = connection();
    when(gmail.refresh("refresh")).thenThrow(new GoogleGmailClient.GmailApiException(400,"HTTP 400","invalid_grant"));
    var error = assertThrows(ApiException.class, () -> service.validAccessToken(c));
    assertEquals(401,error.status); assertEquals("GMAIL_RECONNECT_REQUIRED",error.code);
    assertEquals(GmailConnection.Status.REAUTHORIZATION_REQUIRED,c.status);
    verify(connections).save(c);
  }
  @Test void missingRefreshAlsoRequiresReconnectWithoutCallingGoogle() {
    var c = connection(); c.encryptedRefreshToken = null;
    var error = assertThrows(ApiException.class, () -> service.validAccessToken(c));
    assertEquals("GMAIL_RECONNECT_REQUIRED",error.code);
    verifyNoInteractions(gmail); verify(connections).save(c);
  }
  @Test void invalidClientDoesNotRevokeUsersConnection() {
    var c = connection();
    when(gmail.refresh("refresh")).thenThrow(new GoogleGmailClient.GmailApiException(401,"HTTP 401","invalid_client"));
    var error = assertThrows(ApiException.class, () -> service.validAccessToken(c));
    assertEquals(503,error.status); assertEquals("GMAIL_OAUTH_CONFIGURATION_ERROR",error.code);
    assertEquals(GmailConnection.Status.CONNECTED,c.status);
    verifyNoInteractions(connections);
  }
  @Test void transientFailurePreservesCredentialAndConnectedStatus() {
    var c = connection(); var encrypted = c.encryptedRefreshToken;
    when(gmail.refresh("refresh")).thenThrow(new GoogleGmailClient.GmailApiException(503,"HTTP 503"));
    var error = assertThrows(ApiException.class, () -> service.validAccessToken(c));
    assertEquals(503,error.status); assertEquals("GMAIL_TEMPORARILY_UNAVAILABLE",error.code);
    assertEquals(encrypted,c.encryptedRefreshToken);
    assertEquals(GmailConnection.Status.CONNECTED,c.status); verifyNoInteractions(connections);
  }
  @Test void unspecified400IsNotAssumedToMeanExpiredAuthorization() {
    var c = connection();
    when(gmail.refresh("refresh")).thenThrow(new GoogleGmailClient.GmailApiException(400,"HTTP 400"));
    var error = assertThrows(ApiException.class, () -> service.validAccessToken(c));
    assertEquals(502,error.status); assertEquals("GMAIL_OAUTH_REQUEST_FAILED",error.code);
    assertEquals(GmailConnection.Status.CONNECTED,c.status); verifyNoInteractions(connections);
  }
  @Test void validAccessTokenAvoidsRenewal() {
    var c = connection(); c.accessTokenExpiresAt = Instant.now().plusSeconds(3600);
    assertEquals("old-access",service.validAccessToken(c));
    verifyNoInteractions(gmail,connections);
  }
  @Test void successfulRefreshStoresEncryptedTokenAndRetainsRefresh() {
    var c = connection(); var encrypted = c.encryptedRefreshToken;
    when(gmail.refresh("refresh")).thenReturn(new GmailClient.Tokens("new-access","refresh",3600));
    assertEquals("new-access",service.validAccessToken(c));
    assertEquals("new-access",cipher.decrypt(c.encryptedAccessToken));
    assertNotEquals("new-access",c.encryptedAccessToken);
    assertEquals(encrypted,c.encryptedRefreshToken); verify(connections).save(c);
  }
  @Test void nextSyncStillAsksForReconnectWithoutRetryingInvalidToken() {
    var c = connection(); c.status = GmailConnection.Status.REAUTHORIZATION_REQUIRED;
    when(connections.findFirstByUserIdAndStatusNotOrderByUpdatedAtDesc(7L,GmailConnection.Status.DISCONNECTED))
        .thenReturn(Optional.of(c));
    var error = assertThrows(ApiException.class, () -> service.active(7L));
    assertEquals("GMAIL_RECONNECT_REQUIRED",error.code); verifyNoInteractions(gmail);
  }
  @Test void rotatedRefreshTokenIsStoredEncrypted() {
    var c=connection();
    when(gmail.refresh("refresh")).thenReturn(new GmailClient.Tokens("new-access","rotated-refresh",3600));
    assertEquals("new-access",service.validAccessToken(c));
    assertEquals("rotated-refresh",cipher.decrypt(c.encryptedRefreshToken));
    assertNotEquals("rotated-refresh",c.encryptedRefreshToken);
    verify(connections).save(c);
  }

}

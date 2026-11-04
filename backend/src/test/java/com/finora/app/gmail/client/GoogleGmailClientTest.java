package com.finora.app.gmail.client;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GoogleGmailClientTest {
  HttpClient http = mock(HttpClient.class);
  @SuppressWarnings("unchecked")
  HttpResponse<String> response = mock(HttpResponse.class);
  GoogleGmailClient client = new GoogleGmailClient(new ObjectMapper(), "test-client",
      "test-secret", "https://example.test/callback", http);

  @BeforeEach void transport() throws Exception {
    when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
        .thenReturn(response);
  }

  @Test void keepsOnlyRecognizedOAuthCodeWithoutResponseSecrets() {
    when(response.statusCode()).thenReturn(400);
    when(response.body()).thenReturn("""
        {"error":"invalid_grant","error_description":"sensitive token details","refresh_token":"secret"}
        """);
    var error = assertThrows(GoogleGmailClient.GmailApiException.class, () -> client.refresh("refresh"));
    assertEquals(400, error.status);
    assertEquals("invalid_grant", error.oauthError);
    assertFalse(error.toString().contains("sensitive"));
    assertFalse(error.toString().contains("secret"));
  }

  @Test void rejectsUnrecognizedErrorTextAsDiagnosticCode() {
    when(response.statusCode()).thenReturn(400);
    when(response.body()).thenReturn("{\"error\":\"secret-token-do-not-expose\"}");
    var error = assertThrows(GoogleGmailClient.GmailApiException.class, () -> client.refresh("refresh"));
    assertNull(error.oauthError);
    assertFalse(error.toString().contains("secret-token"));
  }

  @Test void malformedErrorBodyPreservesUpstreamStatus() {
    when(response.statusCode()).thenReturn(400);
    when(response.body()).thenReturn("<html>private upstream failure</html>");
    var error = assertThrows(GoogleGmailClient.GmailApiException.class, () -> client.refresh("refresh"));
    assertEquals(400, error.status);
    assertNull(error.oauthError);
    assertFalse(error.toString().contains("private"));
  }

  @Test void detectsConfigurationErrorWithoutLeakingItsDescription() {
    when(response.statusCode()).thenReturn(401);
    when(response.body()).thenReturn("{\"error\":\"invalid_client\",\"error_description\":\"private credentials\"}");
    var error = assertThrows(GoogleGmailClient.GmailApiException.class, () -> client.refresh("refresh"));
    assertEquals("invalid_client", error.oauthError);
    assertFalse(error.toString().contains("private credentials"));
  }

  @Test void refreshSuccessRetainsOfflineCredential() {
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"access_token\":\"access-new\",\"expires_in\":1800}");
    var tokens = client.refresh("refresh-kept");
    assertEquals("access-new", tokens.accessToken());
    assertEquals("refresh-kept", tokens.refreshToken());
    assertEquals(1800, tokens.expiresInSeconds());
  }
  @Test void refreshAcceptsRotatedOfflineCredentialWithoutExposingIt() {
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"access_token\":\"new-access\",\"refresh_token\":\"rotated-refresh\",\"expires_in\":3600}");
    var tokens=client.refresh("previous-refresh");
    assertEquals("rotated-refresh",tokens.refreshToken());
    assertFalse(tokens.toString().contains("rotated-refresh"));
  }

}

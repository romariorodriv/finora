package com.finora.app.gmail.service;
import com.finora.app.auth.*;
import com.finora.app.config.JwtService;
import com.finora.app.gmail.client.GoogleGmailClient;
import com.finora.app.gmail.entity.GmailConnection;
import com.finora.app.gmail.repository.GmailConnectionRepository;
import com.finora.app.gmail.security.TokenCipher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:gmail-oauth-errors;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class GmailOAuthIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired GmailConnectionRepository connections;
  @Autowired TokenCipher cipher;
  @Autowired com.finora.app.gmail.jobs.GmailImportRunner runner;
  @MockitoBean GoogleGmailClient gmail;
  GmailConnection connection;
  String token, otherToken;

  @BeforeEach void account() {
    String nonce=UUID.randomUUID().toString();
    var owner=users.saveAndFlush(new User("Gmail QA","owner-"+nonce+"@example.test","test-hash"));
    var other=users.saveAndFlush(new User("Other QA","other-"+nonce+"@example.test","test-hash"));
    token="Bearer "+jwt.issue(owner.id,owner.email);
    otherToken="Bearer "+jwt.issue(other.id,other.email);
    var c=new GmailConnection();
    c.userId=owner.id;c.gmailAddress=owner.email;c.encryptedAccessToken=cipher.encrypt("old-access");
    c.encryptedRefreshToken=cipher.encrypt("refresh-private");
    c.accessTokenExpiresAt=Instant.now().minusSeconds(60);
    connection=connections.saveAndFlush(c);
  }

  @Test void rejectedAuthorizationPersistsReconnectStateAndJobError() throws Exception {
    when(gmail.refresh(anyString())).thenThrow(new GoogleGmailClient.GmailApiException(400,"HTTP 400","invalid_grant"));
    mvc.perform(post("/api/v1/integrations/gmail/sync").header("Authorization",token)).andExpect(status().isAccepted());
    runner.runPage(connection.userId);
    assertEquals(GmailConnection.Status.REAUTHORIZATION_REQUIRED,connections.findById(connection.id).orElseThrow().status);
    String response=mvc.perform(get("/api/v1/integrations/gmail/sync/status").header("Authorization",token))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ERROR"))
        .andExpect(jsonPath("$.errorCode").value("GMAIL_RECONNECT_REQUIRED")).andReturn().getResponse().getContentAsString();
    assertFalse(response.contains("refresh-private"));assertFalse(response.contains("old-access"));
    mvc.perform(get("/api/v1/integrations/gmail/status").header("Authorization",token))
        .andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(false))
        .andExpect(jsonPath("$.status").value("REAUTHORIZATION_REQUIRED"));
    mvc.perform(post("/api/v1/integrations/gmail/sync").header("Authorization",token))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GMAIL_RECONNECT_REQUIRED"));
    verify(gmail,times(1)).refresh(anyString());
  }
  @Test void configurationErrorDoesNotInvalidateUserCredential() throws Exception {
    when(gmail.refresh(anyString())).thenThrow(new GoogleGmailClient.GmailApiException(401,"HTTP 401","invalid_client"));
    mvc.perform(post("/api/v1/integrations/gmail/sync").header("Authorization",token)).andExpect(status().isAccepted());
    runner.runPage(connection.userId);
    mvc.perform(get("/api/v1/integrations/gmail/sync/status").header("Authorization",token))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ERROR"))
        .andExpect(jsonPath("$.errorCode").value("GMAIL_OAUTH_CONFIGURATION_ERROR"));
    var persisted=connections.findById(connection.id).orElseThrow();
    assertEquals(GmailConnection.Status.CONNECTED,persisted.status);
    assertEquals("refresh-private",cipher.decrypt(persisted.encryptedRefreshToken));
  }
  @Test void otherAccountCannotUseConnectionOrTriggerItsRefresh() throws Exception {
    mvc.perform(post("/api/v1/integrations/gmail/sync").header("Authorization",otherToken))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GMAIL_RECONNECT_REQUIRED"));
    assertEquals(GmailConnection.Status.CONNECTED,connections.findById(connection.id).orElseThrow().status);
    verifyNoInteractions(gmail);
  }
}

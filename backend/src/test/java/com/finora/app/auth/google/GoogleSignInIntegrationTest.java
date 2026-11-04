package com.finora.app.auth.google;
import com.finora.app.auth.*;
import com.finora.app.transaction.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:google-signin-qa;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class GoogleSignInIntegrationTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired UserRepository users;@Autowired GoogleAuthFlowRepository flows;
 @Autowired AccountSessionRepository sessions;@Autowired BCryptPasswordEncoder encoder;@Autowired TransactionRepository transactions;
 @MockitoBean GoogleIdentityClient google;
 record Flow(String state,Cookie cookie){}
 String nonce=UUID.randomUUID().toString();
 @BeforeEach void client(){when(google.authorizationUrl(anyString(),anyString(),anyString())).thenAnswer(c->"https://accounts.google.com/o/oauth2/v2/auth?state="+c.getArgument(0));}
 Flow start()throws Exception{
  var response=mvc.perform(post("/api/v1/auth/google/start").header("Origin","http://localhost:4200")).andExpect(status().isOk()).andReturn().getResponse();
  String url=json.readTree(response.getContentAsString()).get("authorizationUrl").asText();
  Cookie cookie=response.getCookie(AuthCookies.GOOGLE_FLOW);assertNotNull(cookie);assertTrue(cookie.isHttpOnly());
  return new Flow(url.substring(url.indexOf("state=")+6),cookie);
 }
 MvcResult callback(Flow flow)throws Exception{return mvc.perform(get("/api/v1/auth/google/callback").param("code","one-time-code").param("state",flow.state()).cookie(flow.cookie())).andExpect(status().is3xxRedirection()).andReturn();}
 void identity(String sub,String email){when(google.identity(anyString(),anyString(),anyString())).thenReturn(new GoogleIdTokenVerifier.Identity(sub,email,"Google QA"));}
 @Test void newUserGetsUserRoleAndOpaqueServerSessionWithoutBrowserTokens()throws Exception{
  identity("new-"+nonce,"new-"+nonce+"@example.test");var flow=start();var response=callback(flow).getResponse();
  assertEquals("http://localhost:4200/?auth=connected",response.getRedirectedUrl());
  Cookie session=response.getCookie(AuthCookies.SESSION);assertNotNull(session);assertTrue(session.isHttpOnly());
  var stored=sessions.findById(AuthSecrets.hash(session.getValue())).orElseThrow();assertNotEquals(session.getValue(),stored.tokenHash);
  var user=users.findById(stored.userId).orElseThrow();assertEquals("USER",user.role);assertEquals("new-"+nonce,user.googleSubject);
  mvc.perform(get("/api/v1/account").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(user.id)).andExpect(jsonPath("$.role").value("USER"));
  assertFalse(response.getRedirectedUrl().contains(session.getValue()));
 }
 @Test void existingAdminRequiresOldPasswordAndRetainsIdRoleAndMovements()throws Exception{
  var previous=new User("Anterior","legacy-"+nonce+"@example.test",encoder.encode("old-password-proof"));previous.role="ADMIN";previous=users.saveAndFlush(previous);
  var t=new Transaction();t.userId=previous.id;t.description="Gasto existente";t.category="Otros";t.amount=new BigDecimal("12.50");t.date=LocalDate.now();transactions.saveAndFlush(t);
  identity("linked-"+nonce,previous.email);var flow=start();var response=callback(flow).getResponse();
  assertTrue(response.getRedirectedUrl().contains("link_required"));assertNull(response.getCookie(AuthCookies.SESSION));assertNull(users.findById(previous.id).orElseThrow().googleSubject);
  mvc.perform(post("/api/v1/auth/google/link").header("Origin","http://localhost:4200").cookie(flow.cookie()).contentType("application/json").content("{\"password\":\"incorrect\"}")).andExpect(status().isUnauthorized());
  var linked=mvc.perform(post("/api/v1/auth/google/link").header("Origin","http://localhost:4200").cookie(flow.cookie()).contentType("application/json").content("{\"password\":\"old-password-proof\"}")).andExpect(status().isOk()).andReturn().getResponse();
  var account=users.findById(previous.id).orElseThrow();assertEquals("ADMIN",account.role);assertEquals("linked-"+nonce,account.googleSubject);assertTrue(transactions.findByIdAndUserId(t.id,previous.id).isPresent());
  mvc.perform(get("/api/v1/admin/categories").cookie(linked.getCookie(AuthCookies.SESSION))).andExpect(status().isOk());
 }
 @Test void alreadyLinkedSubjectWinsOverChangedGoogleEmail()throws Exception{
  var existing=new User("Existing","before-"+nonce+"@example.test",encoder.encode("old-password"));existing.googleSubject="stable-"+nonce;existing.role="ADMIN";existing=users.saveAndFlush(existing);
  identity(existing.googleSubject,"changed-"+nonce+"@example.test");var response=callback(start()).getResponse();
  var session=sessions.findById(AuthSecrets.hash(response.getCookie(AuthCookies.SESSION).getValue())).orElseThrow();
  assertEquals(existing.id,session.userId);assertEquals("ADMIN",users.findById(existing.id).orElseThrow().role);
 }
 @Test void browserBindingAndSingleUseStatePreventLoginCsrfAndReplay()throws Exception{
  identity("csrf-"+nonce,"csrf-"+nonce+"@example.test");var flow=start();
  var bad=new Flow(flow.state(),new Cookie(AuthCookies.GOOGLE_FLOW,flow.state()+"."+AuthSecrets.random()));
  assertTrue(callback(bad).getResponse().getRedirectedUrl().contains("auth=failed"));
  verify(google,never()).identity(anyString(),anyString(),anyString());
  var successful=callback(flow).getResponse();assertNotNull(successful.getCookie(AuthCookies.SESSION));
  assertTrue(callback(flow).getResponse().getRedirectedUrl().contains("auth=failed"));
  verify(google,times(1)).identity(anyString(),anyString(),anyString());
 }
 @Test void cancelledLoginDoesNotCreateAccount()throws Exception{
  long count=users.count();var f=start();
  mvc.perform(get("/api/v1/auth/google/callback").param("state",f.state()).param("error","access_denied").cookie(f.cookie())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost:4200/?auth=cancelled#acceso"));
  assertEquals(count,users.count());verify(google,never()).identity(anyString(),anyString(),anyString());
 }
 @Test void cookieMutationsRequireExactOriginAndLogoutRevokesServerSession()throws Exception{
  identity("session-"+nonce,"session-"+nonce+"@example.test");Cookie cookie=callback(start()).getResponse().getCookie(AuthCookies.SESSION);
  mvc.perform(post("/api/v1/auth/google/logout").cookie(cookie).header("Origin","https://attacker.example")).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/account").cookie(cookie)).andExpect(status().isOk());
  mvc.perform(post("/api/v1/auth/google/logout").cookie(cookie).header("Origin","http://localhost:4200")).andExpect(status().isOk());
  mvc.perform(get("/api/v1/account").cookie(cookie)).andExpect(status().isUnauthorized());
 }
 @Test void startRequiresTrustedOriginAndPasswordLoginIsNotPublic()throws Exception{
  mvc.perform(post("/api/v1/auth/google/start")).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\"qa@example.test\",\"password\":\"some-password\"}")).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("GOOGLE_SIGN_IN_REQUIRED"));
 }
 @Test void expiredSessionCannotReadAccountEvenWithCookie()throws Exception{
  identity("expired-"+nonce,"expired-"+nonce+"@example.test");
  Cookie cookie=callback(start()).getResponse().getCookie(AuthCookies.SESSION);
  var row=sessions.findById(AuthSecrets.hash(cookie.getValue())).orElseThrow();row.expiresAt=java.time.Instant.now().minusSeconds(1);sessions.saveAndFlush(row);
  mvc.perform(get("/api/v1/account").cookie(cookie)).andExpect(status().isUnauthorized());
 }

}

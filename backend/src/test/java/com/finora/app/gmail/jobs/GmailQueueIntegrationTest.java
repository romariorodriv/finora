package com.finora.app.gmail.jobs;
import com.finora.app.auth.*;
import com.finora.app.config.JwtService;
import com.finora.app.gmail.client.*;
import com.finora.app.gmail.entity.*;
import com.finora.app.gmail.repository.*;
import com.finora.app.gmail.security.TokenCipher;
import com.finora.app.transaction.*;
import com.finora.app.shared.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:gmail-queue-qa;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class GmailQueueIntegrationTest {
 @Autowired GmailJobStore store;@Autowired GmailSyncJobRepository jobs;@Autowired GmailImportRunner runner;
 @Autowired GmailMessageProcessor processor;@Autowired GmailJobItemRepository items;
 @Autowired GmailConnectionRepository connections;@Autowired UserRepository users;@Autowired TransactionRepository transactions;
 @Autowired ImportedMessageRepository imported;@Autowired TokenCipher cipher;@Autowired JwtService jwt;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
 @MockitoBean GoogleGmailClient google;
 User owner,other;GmailConnection connection;String token,otherToken;
 @BeforeEach void setup(){
  String nonce=UUID.randomUUID().toString();
  owner=users.saveAndFlush(new User("Owner","queue-"+nonce+"@example.test","test-hash"));
  other=users.saveAndFlush(new User("Other","other-queue-"+nonce+"@example.test","test-hash"));
  token="Bearer "+jwt.issue(owner.id,owner.email);otherToken="Bearer "+jwt.issue(other.id,other.email);
  var c=new GmailConnection();c.userId=owner.id;c.gmailAddress=owner.email;c.encryptedAccessToken=cipher.encrypt("old-access");c.encryptedRefreshToken=cipher.encrypt("refresh-secret");c.accessTokenExpiresAt=Instant.now().minusSeconds(60);
  connection=connections.saveAndFlush(c);
 }
 void validToken(){when(google.refresh(anyString())).thenReturn(new GmailClient.Tokens("access-ok","refresh-secret",3600));}
 GmailClient.Message purchase(String id){return new GmailClient.Message(id,"thread-"+id,"notificaciones@notificacionesbcp.com.pe","Realizaste un consumo",
   "Realizaste un consumo de S/ 32.90 con tu Tarjeta de Débito BCP en DLC*Spotify.",Instant.now().minusSeconds(3600));}
 List<Transaction> ownerTransactions(){return transactions.findAll().stream().filter(t->owner.id.equals(t.userId)).toList();}
 @Test void emptyInitialImportIsCompletedWithExplicitPeriodAndZeroDetected() {
  validToken();when(google.search(anyString(),anyString(),isNull())).thenReturn(new GmailClient.Page(List.of(),null));
  var pending=store.enqueue(owner.id);assertEquals("PENDING",pending.status());
  long days=Duration.between(pending.periodFrom(),pending.periodUntil()).toDays();assertEquals(90,days);
  runner.runPage(owner.id);var result=store.status(owner.id);
  assertEquals("COMPLETED",result.status());assertEquals(0,result.messagesFound());assertEquals(0,result.transactionsCreated());
  assertNotNull(connections.findById(connection.id).orElseThrow().lastSyncAt);
 }
 @Test void moreThanFivePagesAndRepeatedSyncDoNotDuplicateOrOverrideManualCorrection() {
  validToken();
  when(google.search(anyString(),anyString(),nullable(String.class))).thenAnswer(c->{
   String cursor=c.getArgument(2);int page=cursor==null?0:Integer.parseInt(cursor);
   return new GmailClient.Page(List.of(new GmailClient.MessageRef("mail-"+page,"thread")),page<5?Integer.toString(page+1):null);
  });
  when(google.getMessage(anyString(),anyString())).thenAnswer(c->purchase(c.getArgument(1)));
  store.enqueue(owner.id);for(int i=0;i<6;i++)runner.runPage(owner.id);
  assertEquals("COMPLETED",store.status(owner.id).status());assertEquals(6,store.status(owner.id).transactionsCreated());assertEquals(6,ownerTransactions().size());
  var edited=ownerTransactions().get(0);edited.classificationSource="MANUAL_USER";edited.category="Elección manual";transactions.saveAndFlush(edited);
  store.enqueue(owner.id);for(int i=0;i<6;i++)runner.runPage(owner.id);
  assertEquals(0,store.status(owner.id).transactionsCreated());assertEquals(6,store.status(owner.id).duplicatesSkipped());assertEquals(6,ownerTransactions().size());
  var unchanged=transactions.findById(edited.id).orElseThrow();assertEquals("Elección manual",unchanged.category);assertEquals("MANUAL_USER",unchanged.classificationSource);
  assertTrue(ownerTransactions().stream().allMatch(t->"PEN".equals(t.currency)&&"EMAIL_RECEIVED".equals(t.dateSource)));
 }
 @Test void unsupportedAndAmbiguousMessagesNeverInventTransactions() {
  validToken();
  when(google.search(anyString(),anyString(),isNull())).thenReturn(new GmailClient.Page(List.of(new GmailClient.MessageRef("promo","t"),new GmailClient.MessageRef("ambiguous","t")),null));
  when(google.getMessage(anyString(),eq("promo"))).thenReturn(new GmailClient.Message("promo","t","promociones@example.test","Oferta","Compra hoy",Instant.now()));
  when(google.getMessage(anyString(),eq("ambiguous"))).thenReturn(new GmailClient.Message("ambiguous","t","notificaciones@notificacionesbcp.com.pe","Constancia de transferencia entre mis cuentas","Monto no disponible",Instant.now()));
  store.enqueue(owner.id);runner.runPage(owner.id);
  var result=store.status(owner.id);assertEquals(1,result.unsupported());assertEquals(1,result.reviewRequired());assertEquals(0,result.transactionsCreated());assertTrue(ownerTransactions().isEmpty());
 }
 @Test void missingGoogleDateIsReviewableAndNeverDefaultsToToday() {
  validToken();when(google.search(anyString(),anyString(),isNull())).thenReturn(new GmailClient.Page(List.of(new GmailClient.MessageRef("date-missing","t")),null));
  var real=purchase("date-missing");when(google.getMessage(anyString(),anyString())).thenReturn(new GmailClient.Message(real.id(),real.threadId(),real.sender(),real.subject(),real.plainText(),null));
  store.enqueue(owner.id);runner.runPage(owner.id);
  assertEquals(1,store.status(owner.id).reviewRequired());assertTrue(ownerTransactions().isEmpty());
 }
 @Test void failedMessagesProducePartialImportAndBoundedRetriesEndInReview() {
  validToken();when(google.search(anyString(),anyString(),isNull())).thenReturn(new GmailClient.Page(List.of(new GmailClient.MessageRef("removed","t")),null));
  when(google.getMessage(anyString(),anyString())).thenThrow(new GoogleGmailClient.GmailApiException(404,"HTTP 404"));
  for(int attempt=1;attempt<=3;attempt++){
   store.enqueue(owner.id);runner.runPage(owner.id);
   assertEquals(attempt<3?"PARTIAL":"COMPLETED",store.status(owner.id).status());
  }
  var row=imported.findByUserIdAndGmailConnectionIdAndExternalMessageId(owner.id,connection.id,"removed").orElseThrow();
  assertEquals(3,row.processingAttempts);assertEquals(ImportedMessage.Status.REVIEW_REQUIRED,row.processingStatus);assertTrue(ownerTransactions().isEmpty());
 }
 @Test void concurrentEnqueuesAndClaimsUseOnePersistedJobAndLease() throws Exception {
  var pool=Executors.newFixedThreadPool(2);
  try{
   var first=pool.submit(()->store.enqueue(owner.id));var second=pool.submit(()->store.enqueue(owner.id));
   assertEquals(first.get(10,TimeUnit.SECONDS).runId(),second.get(10,TimeUnit.SECONDS).runId());
   var a=pool.submit(()->store.claim(owner.id));var b=pool.submit(()->store.claim(owner.id));
   assertEquals(1,(a.get(10,TimeUnit.SECONDS).isPresent()?1:0)+(b.get(10,TimeUnit.SECONDS).isPresent()?1:0));
  }finally{pool.shutdownNow();}
 }
 @Test void expiredLeaseCanBeRecoveredAndOldWorkerCannotWrite() {
  store.enqueue(owner.id);var old=store.claim(owner.id).orElseThrow();
  var persisted=jobs.findById(owner.id).orElseThrow();persisted.leaseUntil=Instant.now().minusSeconds(1);jobs.saveAndFlush(persisted);
  var recovered=store.claim(owner.id).orElseThrow();assertNotEquals(old.leaseToken,recovered.leaseToken);
  var error=assertThrows(ApiException.class,()->processor.process(old,purchase("stale")));
  assertEquals("GMAIL_JOB_SUPERSEDED",error.code);assertTrue(ownerTransactions().isEmpty());
 }
 @Test void replayedPageHasExactlyOneItemAndOneTransaction() {
  store.enqueue(owner.id);var claim=store.claim(owner.id).orElseThrow();
  processor.process(claim,purchase("replayed"));processor.process(claim,purchase("replayed"));
  assertEquals(1,ownerTransactions().size());assertEquals(1,store.status(owner.id).messagesFound());assertEquals(1,store.status(owner.id).transactionsCreated());
 }
 @Test void quotaFailuresBackOffAndStopAfterFiveRetries() {
  validToken();when(google.search(anyString(),anyString(),nullable(String.class))).thenThrow(new GoogleGmailClient.GmailApiException(429,"HTTP 429"));
  store.enqueue(owner.id);
  for(int attempt=1;attempt<=6;attempt++){
   runner.runPage(owner.id);var row=jobs.findById(owner.id).orElseThrow();
   assertEquals(attempt,row.retryCount);
   if(attempt<=5){assertEquals("PENDING",row.status);assertTrue(row.nextAttemptAt.isAfter(Instant.now()));assertTrue(store.claim(owner.id).isEmpty());row.nextAttemptAt=Instant.now().minusSeconds(1);jobs.saveAndFlush(row);}
   else assertEquals("ERROR",row.status);
  }
  assertEquals(GmailConnection.Status.CONNECTED,connections.findById(connection.id).orElseThrow().status);
 }
 @Test void cappedPartialImportResumesItsCursorAndCounters() {
  var original=store.enqueue(owner.id);var claim=store.claim(owner.id).orElseThrow();
  processor.process(claim,purchase("first-page"));store.pageDone(owner.id,claim.runId,claim.leaseToken,"next-page",1);
  assertEquals("PARTIAL",store.status(owner.id).status());
  var resumed=store.enqueue(owner.id);assertEquals(original.runId(),resumed.runId());assertEquals("PENDING",resumed.status());assertEquals(1,resumed.transactionsCreated());
  assertEquals("next-page",jobs.findById(owner.id).orElseThrow().nextPageToken);
 }
 @Test void incrementalImportUsesOverlapAndDisconnectPreventsOldWorkerWrites() throws Exception {
  store.enqueue(owner.id);var baseline=store.claim(owner.id).orElseThrow();store.pageDone(owner.id,baseline.runId,baseline.leaseToken,null,1000);
  connection=connections.findById(connection.id).orElseThrow();
  connection.lastSyncAt=Instant.now().minusSeconds(7200);connections.saveAndFlush(connection);
  var pending=store.enqueue(owner.id);assertTrue(Math.abs(Duration.between(connection.lastSyncAt.minusSeconds(48*3600),pending.periodFrom()).toNanos())<1000);
  var claim=store.claim(owner.id).orElseThrow();
  mvc.perform(delete("/api/v1/integrations/gmail").header("Authorization",token)).andExpect(status().isOk());
  assertEquals("CANCELLED",store.status(owner.id).status());
  assertThrows(ApiException.class,()->processor.process(claim,purchase("after-disconnect")));
  var disconnected=connections.findById(connection.id).orElseThrow();assertNull(disconnected.encryptedRefreshToken);assertTrue(ownerTransactions().isEmpty());
 }
 @Test void jobsAndReviewMetadataAreIsolatedBetweenAccounts() throws Exception {
  store.enqueue(owner.id);var claim=store.claim(owner.id).orElseThrow();
  processor.process(claim,new GmailClient.Message("unknown","thread","someone@example.test","Private subject","Unsupported",Instant.now()));
  mvc.perform(get("/api/v1/integrations/gmail/sync/status").header("Authorization",otherToken).param("userId",owner.id.toString()))
   .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_STARTED")).andExpect(jsonPath("$.runId").isEmpty());
  mvc.perform(get("/api/v1/integrations/gmail/review").header("Authorization",otherToken).param("userId",owner.id.toString()))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
  String ownerResponse=mvc.perform(get("/api/v1/integrations/gmail/review").header("Authorization",token))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andReturn().getResponse().getContentAsString();
  assertFalse(ownerResponse.contains("refresh-secret"));
 }
 @Test void readOnlyConsentIsBrowserBoundAndStartsFirstJobOnlyAfterSuccess() throws Exception {
  when(google.configured()).thenReturn(true);
  var response=mvc.perform(get("/api/v1/integrations/gmail/authorization-url").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse();
  String url=json.readTree(response.getContentAsString()).get("authorizationUrl").asText();
  assertTrue(url.contains("gmail.readonly"));assertFalse(url.contains("gmail.modify"));assertTrue(url.contains("code_challenge_method=S256"));
  String state=Arrays.stream(URI.create(url).getRawQuery().split("&")).filter(v->v.startsWith("state=")).findFirst().orElseThrow().substring(6);
  Cookie browser=response.getCookie(com.finora.app.auth.google.AuthCookies.GMAIL_FLOW);
  when(google.exchangeCode(eq("consent-code"),anyString())).thenReturn(new GmailClient.Tokens("fresh-access","fresh-refresh",3600));
  when(google.profile("fresh-access")).thenReturn(new GmailClient.Profile(owner.email));
  mvc.perform(get("/api/v1/integrations/gmail/callback").param("state",state).param("code","consent-code"))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost:4200/?gmail=failed"));
  assertTrue(jobs.findById(owner.id).isEmpty());
  mvc.perform(get("/api/v1/integrations/gmail/callback").param("state",state).param("code","consent-code").cookie(browser))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost:4200/?gmail=connected"));
  assertEquals("PENDING",store.status(owner.id).status());
  var saved=connections.findById(connection.id).orElseThrow();assertNotEquals("fresh-refresh",saved.encryptedRefreshToken);assertEquals("fresh-refresh",cipher.decrypt(saved.encryptedRefreshToken));
 }
 @Test void rejectedGmailConsentDoesNotLoopLoginOrQueueImport() throws Exception {
  connection.status=GmailConnection.Status.DISCONNECTED;connections.saveAndFlush(connection);
  when(google.configured()).thenReturn(true);
  var response=mvc.perform(get("/api/v1/integrations/gmail/authorization-url").header("Authorization",token)).andReturn().getResponse();
  String url=json.readTree(response.getContentAsString()).get("authorizationUrl").asText();
  String state=Arrays.stream(URI.create(url).getRawQuery().split("&")).filter(v->v.startsWith("state=")).findFirst().orElseThrow().substring(6);
  mvc.perform(get("/api/v1/integrations/gmail/callback").param("state",state).param("error","access_denied").cookie(response.getCookie(com.finora.app.auth.google.AuthCookies.GMAIL_FLOW)))
   .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("http://localhost:4200/?gmail=cancelled"));
  assertTrue(jobs.findById(owner.id).isEmpty());verify(google,never()).exchangeCode(anyString(),anyString());
  mvc.perform(get("/api/v1/account").header("Authorization",token)).andExpect(status().isOk());
 }
 @Test void deletionRequestStopsImportAndCannotTargetAnotherAccount() throws Exception {
  store.enqueue(owner.id);
  mvc.perform(post("/api/v1/account/deletion-request").header("Authorization",token).contentType("application/json").content("{\"confirmed\":false}")).andExpect(status().isBadRequest());
  assertEquals("PENDING",store.status(owner.id).status());
  mvc.perform(post("/api/v1/account/deletion-request").header("Authorization",token).contentType("application/json").content("{\"confirmed\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
  assertEquals("CANCELLED",store.status(owner.id).status());
  assertEquals(GmailConnection.Status.DISCONNECTED,connections.findById(connection.id).orElseThrow().status);
  var error=assertThrows(ApiException.class,()->store.enqueue(owner.id));assertEquals("DELETION_PENDING",error.code);
  mvc.perform(get("/api/v1/account").header("Authorization",otherToken)).andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(other.id));
 }

 @Test void receivedRecordFromInterruptedLegacyImportIsProcessedRatherThanSkipped() {
  var row=new ImportedMessage();row.userId=owner.id;row.gmailConnectionId=connection.id;row.externalMessageId="received";row.processingStatus=ImportedMessage.Status.RECEIVED;imported.saveAndFlush(row);
  store.enqueue(owner.id);var claim=store.claim(owner.id).orElseThrow();processor.process(claim,purchase("received"));
  assertEquals(1,ownerTransactions().size());assertEquals(1,store.status(owner.id).transactionsCreated());
 }

 @Test void sameGmailMessageIdInDifferentMailboxesIsNotMistakenForSameSource() {
  store.enqueue(owner.id);var first=store.claim(owner.id).orElseThrow();processor.process(first,purchase("mailbox-local-id"));store.pageDone(owner.id,first.runId,first.leaseToken,null,1000);
  connection.status=GmailConnection.Status.DISCONNECTED;connections.saveAndFlush(connection);
  var second=new GmailConnection();second.userId=owner.id;second.gmailAddress="second-"+owner.email;second.encryptedAccessToken=cipher.encrypt("second-access");second.encryptedRefreshToken=cipher.encrypt("second-refresh");second.accessTokenExpiresAt=Instant.now().plusSeconds(3600);connections.saveAndFlush(second);
  store.enqueue(owner.id);var claim=store.claim(owner.id).orElseThrow();processor.process(claim,purchase("mailbox-local-id"));
  assertEquals(2,ownerTransactions().size());assertEquals(1,store.status(owner.id).transactionsCreated());
 }

 @Test void firstNewJobBackfillsNinetyDaysEvenForLegacyConnectionWithOldSyncMarker(){
  connection.lastSyncAt=Instant.now().minusSeconds(3600);connections.saveAndFlush(connection);
  var first=store.enqueue(owner.id);
  assertEquals(90,Duration.between(first.periodFrom(),first.periodUntil()).toDays());
  assertTrue(jobs.findById(owner.id).orElseThrow().initialImport);
 }

 @Test void disconnectRetiresEveryLegacyMailboxOfUserWithoutTouchingAnotherUser() throws Exception {
  var older=new GmailConnection();older.userId=owner.id;older.gmailAddress="older-"+owner.email;older.encryptedAccessToken=cipher.encrypt("older-access");older.encryptedRefreshToken=cipher.encrypt("older-refresh");connections.saveAndFlush(older);
  var foreign=new GmailConnection();foreign.userId=other.id;foreign.gmailAddress=other.email;foreign.encryptedAccessToken=cipher.encrypt("foreign-access");foreign.encryptedRefreshToken=cipher.encrypt("foreign-refresh");connections.saveAndFlush(foreign);
  mvc.perform(delete("/api/v1/integrations/gmail").header("Authorization",token)).andExpect(status().isOk());
  for(var c:connections.findByUserId(owner.id)){assertEquals(GmailConnection.Status.DISCONNECTED,c.status);assertNull(c.encryptedRefreshToken);assertNull(c.encryptedAccessToken);}
  assertEquals(GmailConnection.Status.CONNECTED,connections.findById(foreign.id).orElseThrow().status);
 }

}

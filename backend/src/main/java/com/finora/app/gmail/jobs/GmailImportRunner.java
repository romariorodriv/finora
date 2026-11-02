package com.finora.app.gmail.jobs;
import com.finora.app.gmail.client.*;
import com.finora.app.gmail.service.GmailOAuthService;
import com.finora.app.gmail.repository.GmailConnectionRepository;
import com.finora.app.shared.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
@Service
public class GmailImportRunner {
 private final GmailJobStore jobs;private final GmailClient gmail;private final GmailOAuthService oauth;private final GmailMessageProcessor messages;private final GmailConnectionRepository connections;
 private final int maxPages;
 public GmailImportRunner(GmailJobStore jobs,GmailClient gmail,GmailOAuthService oauth,GmailMessageProcessor messages,GmailConnectionRepository connections,@Value("${app.gmail.max-pages:1000}")int maxPages){
  this.jobs=jobs;this.gmail=gmail;this.oauth=oauth;this.messages=messages;this.connections=connections;this.maxPages=Math.max(1,maxPages);
 }
 public void runPage(Long userId){
  var available=jobs.claim(userId);if(available.isEmpty())return;var claim=available.get();
  try{
   var connection=oauth.active(userId);
   if(!connection.id.equals(claim.connectionId))throw new ApiException(409,"GMAIL_CONNECTION_CHANGED","La conexión cambió.");
   String token=oauth.validAccessToken(connection);
   String query="in:anywhere after:"+claim.periodFrom.getEpochSecond()+" before:"+claim.periodUntil.getEpochSecond()+" (consumo OR compra OR operación OR pago OR retiro OR transferencia OR devolución)";
   var page=gmail.search(token,query,claim.nextPageToken);
   for(var ref:page.messages()){
    jobs.heartbeat(userId,claim.runId,claim.leaseToken);
    try{messages.process(claim,gmail.getMessage(token,ref.id()));}
    catch(GoogleGmailClient.GmailApiException e){if(e.status==404)messages.failed(claim,ref.id());else throw e;}
    catch(ApiException e){throw e;}
    catch(Exception e){messages.failed(claim,ref.id());}
   }
   jobs.pageDone(userId,claim.runId,claim.leaseToken,page.nextPageToken(),maxPages);
  }catch(ApiException e){
   if(!"GMAIL_JOB_SUPERSEDED".equals(e.code))fail(claim,e.code,"GMAIL_TEMPORARILY_UNAVAILABLE".equals(e.code));
  }catch(GoogleGmailClient.GmailApiException e){
   if(e.status==401||"insufficientPermissions".equals(e.googleReason))oauth.markReauthorization(userId,claim.connectionId);
   boolean retry=e.status==429||e.status>=500||"rateLimitExceeded".equals(e.googleReason)||"userRateLimitExceeded".equals(e.googleReason);
   fail(claim,(e.status==401||"insufficientPermissions".equals(e.googleReason))?"GMAIL_RECONNECT_REQUIRED":retry?"GOOGLE_RATE_OR_TRANSIENT_ERROR":"GMAIL_ACCESS_ERROR",retry);
  }catch(Exception e){fail(claim,"GMAIL_IMPORT_ERROR",true);}
 }
 private void fail(GmailSyncJob j,String code,boolean retry){
  try{jobs.failed(j.userId,j.runId,j.leaseToken,code,retry);}catch(ApiException e){if(!"GMAIL_JOB_SUPERSEDED".equals(e.code))throw e;}
 }
}

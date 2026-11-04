package com.finora.app.gmail.jobs;
import com.finora.app.auth.*;
import com.finora.app.gmail.entity.GmailConnection;
import com.finora.app.gmail.repository.GmailConnectionRepository;
import com.finora.app.shared.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service
public class GmailJobStore {
 public record Status(String runId,String status,Instant periodFrom,Instant periodUntil,
   Instant startedAt,Instant completedAt,Instant updatedAt,Instant nextAttemptAt,
   int messagesFound,int messagesProcessed,int transactionsCreated,int duplicatesSkipped,
   int reviewRequired,int unsupported,int rejected,int failed,int retryCount,
   String errorCode,String message,boolean stale,boolean hasMorePages){}
 private final com.finora.app.privacy.DeletionRequestRepository deletions;
 private final GmailSyncJobRepository jobs;private final GmailJobItemRepository items;
 private final GmailConnectionRepository connections;private final UserRepository users;private final int days,overlapHours,staleMinutes;
 public GmailJobStore(GmailSyncJobRepository jobs,GmailJobItemRepository items,GmailConnectionRepository connections,UserRepository users,com.finora.app.privacy.DeletionRequestRepository deletions,
  @Value("${app.gmail.initial-sync-days:90}")int days,@Value("${app.gmail.overlap-hours:48}")int overlap,
  @Value("${app.gmail.stale-minutes:120}")int staleMinutes){
  this.deletions=deletions;this.jobs=jobs;this.items=items;this.connections=connections;this.users=users;this.days=Math.max(1,Math.min(365,days));this.overlapHours=Math.max(1,overlap);this.staleMinutes=Math.max(1,staleMinutes);
 }
 @Transactional public Status enqueue(Long userId){
  // Serializes even the first enqueue, before a per-user job row exists.
  users.locked(userId).orElseThrow(()->new ApiException(401,"ACCOUNT_NOT_FOUND","Sesión no válida."));
  if(deletions.findById(userId).filter(d->"PENDING".equals(d.status)).isPresent())
   throw new ApiException(409,"DELETION_PENDING","Hay una solicitud de eliminación pendiente. Contacta al operador para reactivar la importación.");
  var connection=connections.findFirstByUserIdAndStatusNotOrderByUpdatedAtDesc(userId,GmailConnection.Status.DISCONNECTED)
   .filter(c->c.status==GmailConnection.Status.CONNECTED)
   .orElseThrow(()->new ApiException(409,"GMAIL_RECONNECT_REQUIRED","Conecta Gmail para importar tus movimientos."));
  var previous=jobs.locked(userId);
  if(previous.isPresent()&&previous.get().connectionId.equals(connection.id)&&Set.of("PENDING","IMPORTING").contains(previous.get().status))return view(previous.get());
  if(previous.isPresent()&&previous.get().connectionId.equals(connection.id)&&"PARTIAL".equals(previous.get().status)&&"PAGE_LIMIT".equals(previous.get().errorCode)&&previous.get().nextPageToken!=null){
   var resume=previous.get();resume.status="PENDING";resume.retryCount=0;resume.pagesProcessed=0;resume.errorCode=null;resume.completedAt=null;resume.nextAttemptAt=null;resume.updatedAt=Instant.now();return view(resume);
  }
  boolean initial=previous.isEmpty()||!previous.get().connectionId.equals(connection.id)||connection.lastSyncAt==null;
  var j=previous.orElseGet(GmailSyncJob::new);
  j.userId=userId;j.connectionId=connection.id;j.runId=UUID.randomUUID().toString();j.status="PENDING";j.queuedAt=Instant.now();j.updatedAt=j.queuedAt;
  j.initialImport=initial;
  j.periodUntil=j.queuedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
  j.periodFrom=j.initialImport?j.periodUntil.minus(Duration.ofDays(days)):connection.lastSyncAt.minus(Duration.ofHours(overlapHours));j.startedAt=null;j.completedAt=null;j.nextPageToken=null;j.leaseToken=null;j.leaseUntil=null;j.nextAttemptAt=null;j.retryCount=0;j.pagesProcessed=0;j.errorCode=null;
  jobs.saveAndFlush(j);return view(j);
 }
 @Transactional public Optional<GmailSyncJob> claim(Long userId){
  var optional=jobs.locked(userId);if(optional.isEmpty())return Optional.empty();var j=optional.get();Instant now=Instant.now();
  if(!Set.of("PENDING","IMPORTING").contains(j.status)||(j.nextAttemptAt!=null&&j.nextAttemptAt.isAfter(now))||(j.leaseUntil!=null&&j.leaseUntil.isAfter(now)))return Optional.empty();
  j.status="IMPORTING";j.leaseToken=UUID.randomUUID().toString();j.leaseUntil=now.plusSeconds(120);j.updatedAt=now;if(j.startedAt==null)j.startedAt=now;jobs.saveAndFlush(j);return Optional.of(j);
 }
 public GmailSyncJob owned(Long userId,String runId,String lease){
  var j=jobs.locked(userId).orElseThrow();
  if(!j.runId.equals(runId)||!Objects.equals(j.leaseToken,lease)||!"IMPORTING".equals(j.status))
    throw new ApiException(409,"GMAIL_JOB_SUPERSEDED","La importación fue cancelada o sustituida.");
  return j;
 }
 @Transactional public void heartbeat(Long userId,String runId,String lease){var j=owned(userId,runId,lease);j.leaseUntil=Instant.now().plusSeconds(120);j.updatedAt=Instant.now();}
 @Transactional public void pageDone(Long userId,String runId,String lease,String next,int maxPages){
  var j=owned(userId,runId,lease);j.nextPageToken=next;j.pagesProcessed++;j.retryCount=0;j.errorCode=null;j.updatedAt=Instant.now();j.leaseToken=null;j.leaseUntil=null;j.nextAttemptAt=null;
  if(next!=null&&j.pagesProcessed<maxPages){j.status="PENDING";return;}
  var totals=counts(j);boolean partial=next!=null||totals.getOrDefault("FAILED",0)>0;
  j.status=partial?"PARTIAL":"COMPLETED";j.completedAt=Instant.now();if(next!=null)j.errorCode="PAGE_LIMIT";
  if(!partial)connections.findById(j.connectionId).filter(c->c.userId.equals(userId)&&c.status==GmailConnection.Status.CONNECTED).ifPresent(c->{c.lastSyncAt=j.periodUntil;c.updatedAt=Instant.now();connections.save(c);});
 }
 @Transactional public void failed(Long userId,String runId,String lease,String code,boolean retryable){
  var j=owned(userId,runId,lease);j.retryCount++;j.errorCode=code;j.updatedAt=Instant.now();j.leaseToken=null;j.leaseUntil=null;
  if(retryable&&j.retryCount<=5){j.status="PENDING";j.nextAttemptAt=Instant.now().plusSeconds(Math.min(1800,30L*(1L<<j.retryCount)));}
  else{j.status="ERROR";j.completedAt=Instant.now();j.nextAttemptAt=null;}
 }
 @Transactional public void cancel(Long userId){jobs.locked(userId).ifPresent(j->{j.status="CANCELLED";j.leaseToken=null;j.leaseUntil=null;j.updatedAt=Instant.now();j.completedAt=j.updatedAt;});}
 public Status status(Long userId){return jobs.findById(userId).map(this::view).orElseGet(()->new Status(null,"NOT_STARTED",null,null,null,null,null,null,0,0,0,0,0,0,0,0,0,null,"Conecta Gmail para iniciar la importación.",true,false));}
 private Map<String,Integer> counts(GmailSyncJob j){var result=new HashMap<String,Integer>();for(Object[] row:items.totals(j.runId,j.userId))result.put((String)row[0],((Number)row[1]).intValue());return result;}
 private Status view(GmailSyncJob j){
  var c=counts(j);int found=c.values().stream().mapToInt(Integer::intValue).sum();
  var connection=connections.findById(j.connectionId);
  boolean stale=connection.map(v->v.lastSyncAt==null||v.lastSyncAt.isBefore(Instant.now().minus(Duration.ofMinutes(staleMinutes)))).orElse(true);
  String message=switch(j.status){case "PENDING"->j.retryCount>0?"Reintentaremos la importación automáticamente.":"Importación pendiente.";
   case "IMPORTING"->"Estamos leyendo notificaciones compatibles.";case "COMPLETED"->"Importación completada.";
   case "PARTIAL"->"Importación parcial. Revisa los correos pendientes y vuelve a actualizar.";case "CANCELLED"->"Importación detenida. Los movimientos ya importados se conservan.";
   default->"GMAIL_RECONNECT_REQUIRED".equals(j.errorCode)?"Vuelve a conectar Gmail para continuar.":
    "GMAIL_OAUTH_CONFIGURATION_ERROR".equals(j.errorCode)?"La configuración de Google requiere revisión del operador de Sarela.":
    "GMAIL_ACCESS_ERROR".equals(j.errorCode)?"Google no permitió consultar Gmail. Revisa los permisos o contacta al operador.":
    "No se pudo completar la importación. Los reintentos terminaron; puedes volver a actualizar.";};
  return new Status(j.runId,j.status,j.periodFrom,j.periodUntil,j.startedAt,j.completedAt,j.updatedAt,j.nextAttemptAt,
   found,found-c.getOrDefault("DUPLICATE",0),c.getOrDefault("CREATED",0),c.getOrDefault("DUPLICATE",0),
   c.getOrDefault("REVIEW",0),c.getOrDefault("UNSUPPORTED",0),c.getOrDefault("REJECTED",0),c.getOrDefault("FAILED",0),j.retryCount,j.errorCode,message,stale,j.nextPageToken!=null);
 }
}

package com.finora.app.gmail.jobs;
import com.finora.app.gmail.entity.GmailConnection;
import com.finora.app.gmail.repository.GmailConnectionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;
@Component @EnableScheduling
@ConditionalOnProperty(name="app.gmail.jobs.enabled",havingValue="true",matchIfMissing=true)
public class GmailJobScheduler {
 private final GmailSyncJobRepository jobs;private final GmailImportRunner runner;private final GmailJobStore store;private final GmailConnectionRepository connections;private final int intervalMinutes;
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(GmailJobScheduler.class);
 public GmailJobScheduler(GmailSyncJobRepository jobs,GmailImportRunner runner,GmailJobStore store,GmailConnectionRepository connections,@Value("${app.gmail.sync-interval-minutes:60}")int interval){
  this.jobs=jobs;this.runner=runner;this.store=store;this.connections=connections;this.intervalMinutes=Math.max(5,interval);
 }
 @Scheduled(fixedDelayString="${app.gmail.worker-delay-ms:1000}") public void work(){
  for(Long user:jobs.due(Instant.now(),PageRequest.of(0,4)))try{runner.runPage(user);}catch(Exception e){log.warn("Gmail job paused; a persisted lease permits recovery. User ID {}",user);}
 }
 @Scheduled(fixedDelayString="${app.gmail.scheduler-delay-ms:60000}") public void queueIncremental(){
  var seen=new HashSet<Long>();
  for(var c:connections.findByStatusOrderByUpdatedAtDesc(GmailConnection.Status.CONNECTED)){
   if(!seen.add(c.userId))continue;
   var job=jobs.findById(c.userId);
   boolean active=job.map(j->Set.of("PENDING","IMPORTING").contains(j.status)).orElse(false);
   Instant last=job.map(j->j.completedAt==null?j.updatedAt:j.completedAt).orElse(c.lastSyncAt);
   if(!active&&(last==null||last.isBefore(Instant.now().minus(Duration.ofMinutes(intervalMinutes)))))
    try{store.enqueue(c.userId);}catch(Exception e){log.warn("Gmail automatic enqueue deferred. User ID {}",c.userId);}
  }
 }
}

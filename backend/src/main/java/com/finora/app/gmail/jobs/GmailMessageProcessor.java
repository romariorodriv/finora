package com.finora.app.gmail.jobs;
import com.finora.app.gmail.client.GmailClient;
import com.finora.app.gmail.entity.*;
import com.finora.app.gmail.repository.*;
import com.finora.app.importer.BankEmailParser;
import com.finora.app.transaction.*;
import com.finora.app.admin.ClassificationEngine;
import com.finora.app.merchant.MerchantNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
@Service
public class GmailMessageProcessor {
 private final GmailJobStore jobs;private final GmailJobItemRepository items;private final ImportedMessageRepository imported;
 private final TransactionRepository transactions;private final BankEmailParser parser;private final ClassificationEngine classification;
 public GmailMessageProcessor(GmailJobStore jobs,GmailJobItemRepository items,ImportedMessageRepository imported,TransactionRepository transactions,BankEmailParser parser,ClassificationEngine classification){
  this.jobs=jobs;this.items=items;this.imported=imported;this.transactions=transactions;this.parser=parser;this.classification=classification;
 }
 @Transactional public void process(GmailSyncJob claim,GmailClient.Message message){
  var job=jobs.owned(claim.userId,claim.runId,claim.leaseToken);
  job.updatedAt=Instant.now();job.leaseUntil=job.updatedAt.plusSeconds(120);
  if(items.existsByRunIdAndMessageId(claim.runId,message.id()))return;
  var existing=imported.findByUserIdAndGmailConnectionIdAndExternalMessageId(claim.userId,claim.connectionId,message.id());
  if(existing.isPresent()&&java.util.Set.of(ImportedMessage.Status.IMPORTED,ImportedMessage.Status.IGNORED,ImportedMessage.Status.REJECTED,ImportedMessage.Status.REVIEW_REQUIRED).contains(existing.get().processingStatus)){
   String prior=switch(existing.get().processingStatus){case REVIEW_REQUIRED->"REVIEW";case IGNORED->"UNSUPPORTED";case REJECTED->"REJECTED";default->"DUPLICATE";};
   record(claim,message.id(),prior,existing.get().errorCode);return;
  }
  var row=existing.orElseGet(ImportedMessage::new);row.userId=claim.userId;row.gmailConnectionId=claim.connectionId;row.externalMessageId=message.id();row.threadId=message.threadId();
  row.sender=truncate(message.sender(),500);row.subject=truncate(message.subject(),500);row.receivedAt=message.receivedAt();row.updatedAt=Instant.now();row.errorCode=null;
  String text=(message.subject()==null?"":message.subject())+" "+(message.plainText()==null?"":message.plainText());
  String outcome;
  if(text.length()>200000){row.processingStatus=ImportedMessage.Status.REVIEW_REQUIRED;row.errorCode="MESSAGE_TOO_LARGE";outcome="REVIEW";}
  else if(!isRecognizedNotificationSender(message.sender()) && parser.supports(message.sender(),text)){
   // Unknown sender with financial-looking content: do not create a movement.
   // From matching alone is not verified authenticity; see QA follow-up for auth signals.
   row.processingStatus=ImportedMessage.Status.REVIEW_REQUIRED;row.errorCode="SENDER_NOT_TRUSTED";outcome="REVIEW";
  }
  else if(!parser.supports(message.sender(),text)){row.processingStatus=ImportedMessage.Status.IGNORED;outcome="UNSUPPORTED";}
  else {
   var parsed=parser.tryParse(message.sender(),text);
   if(parsed.isEmpty()||message.receivedAt()==null){row.processingStatus=ImportedMessage.Status.REVIEW_REQUIRED;row.errorCode="AMBIGUOUS_MESSAGE";outcome="REVIEW";}
   else{
    var p=parsed.get();row.confidence=p.confidence();row.parserName="PeruBankParser";row.parserVersion="1.3";
    if(p.status()==BankEmailParser.OperationStatus.REJECTED){row.processingStatus=ImportedMessage.Status.REJECTED;outcome="REJECTED";}
    else if(p.amount()==null||p.amount().signum()<=0||p.currency()==null||p.confidence()<.90
        ||("EXPENSE".equals(p.type().name())&&MerchantNormalizer.UNKNOWN.equals(MerchantNormalizer.normalize(p.merchant())))){
      row.processingStatus=ImportedMessage.Status.REVIEW_REQUIRED;row.errorCode="AMBIGUOUS_MESSAGE";outcome="REVIEW";
    }else{
     String externalId="gmail:"+claim.connectionId+":"+message.id();
     if(transactions.existsByUserIdAndExternalId(claim.userId,externalId)
       ||(existing.isPresent()&&transactions.existsByUserIdAndExternalId(claim.userId,"gmail:"+message.id()))){
      row.processingStatus=ImportedMessage.Status.IMPORTED;outcome="DUPLICATE";
     }else{
      var t=new Transaction();t.userId=claim.userId;t.description=p.description();t.originalDescription=t.description;t.amount=p.amount();
      // This is the verified delivery timestamp, not an invented bank operation date.
      t.date=LocalDate.ofInstant(message.receivedAt(),ZoneId.of("America/Lima"));t.dateSource="EMAIL_RECEIVED";
      t.merchant=p.merchant();t.category=p.category();t.type=p.type().name();t.source="GMAIL";t.externalId=externalId;t.currency=p.currency();
      classification.automatic(t);transactions.saveAndFlush(t);
      row.processingStatus=ImportedMessage.Status.IMPORTED;row.createdTransactionId=t.id;outcome="CREATED";
     }
    }
   }
  }
  imported.saveAndFlush(row);record(claim,message.id(),outcome,row.errorCode);
 }
 @Transactional public void failed(GmailSyncJob claim,String messageId){
  jobs.owned(claim.userId,claim.runId,claim.leaseToken);
  if(items.existsByRunIdAndMessageId(claim.runId,messageId))return;
  var row=imported.findByUserIdAndGmailConnectionIdAndExternalMessageId(claim.userId,claim.connectionId,messageId).orElseGet(ImportedMessage::new);
  row.userId=claim.userId;row.gmailConnectionId=claim.connectionId;row.externalMessageId=messageId;row.processingAttempts++;row.processingStatus=row.processingAttempts>=3?ImportedMessage.Status.REVIEW_REQUIRED:ImportedMessage.Status.FAILED;
  row.errorCode=row.processingAttempts>=3?"PROCESSING_REVIEW_REQUIRED":"PROCESSING_ERROR";row.updatedAt=Instant.now();imported.saveAndFlush(row);
  record(claim,messageId,row.processingAttempts>=3?"REVIEW":"FAILED",row.errorCode);
 }
 private boolean isRecognizedNotificationSender(String sender){
  if(sender==null)return false;
  // A From header is not authenticated proof; this is a conservative preliminary filter.
  String value=sender.trim();
  int opening=value.lastIndexOf('<');
  int closing=value.lastIndexOf('>');
  if(opening>=0 && closing>opening)value=value.substring(opening+1,closing).trim();
  return value.equalsIgnoreCase("notificaciones@notificacionesbcp.com.pe");
 }
 private void record(GmailSyncJob j,String messageId,String outcome,String error){var item=new GmailJobItem();item.runId=j.runId;item.userId=j.userId;item.messageId=messageId;item.outcome=outcome;item.errorCode=error;items.saveAndFlush(item);}
 private String truncate(String value,int max){return value==null?"":value.substring(0,Math.min(max,value.length()));}
}

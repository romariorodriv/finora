package com.finora.app.gmail.service;

import com.finora.app.gmail.client.GmailClient;
import com.finora.app.gmail.dto.GmailDtos;
import com.finora.app.gmail.entity.*;
import com.finora.app.gmail.repository.ImportedMessageRepository;
import com.finora.app.importer.BankEmailParser;
import com.finora.app.transaction.*;
import com.finora.app.shared.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GmailSyncService {
    private final GmailOAuthService oauth;private final GmailClient gmail;private final ImportedMessageRepository imported;
    private final TransactionRepository transactions;private final BankEmailParser parser;private final int days;
    private final Set<Long> running=ConcurrentHashMap.newKeySet();
    public GmailSyncService(GmailOAuthService oauth,GmailClient gmail,ImportedMessageRepository imported,TransactionRepository transactions,BankEmailParser parser,@Value("${app.gmail.initial-sync-days:30}")int days){this.oauth=oauth;this.gmail=gmail;this.imported=imported;this.transactions=transactions;this.parser=parser;this.days=days;}

    public GmailDtos.SyncResponse sync(Long userId){if(!running.add(userId))throw new ApiException(409,"GMAIL_SYNC_RUNNING","Ya existe una sincronización en curso");try{return doSync(userId);}finally{running.remove(userId);}}
    @Transactional
    protected GmailDtos.SyncResponse doSync(Long userId){GmailConnection c=oauth.active(userId);String token=oauth.validAccessToken(c);String query="in:anywhere newer_than:"+days+"d (consumo OR compra OR operación OR pago OR retiro OR transferencia OR devolución)";int found=0,processed=0,created=0,dupes=0,review=0,rejected=0,failed=0;String page=null;int pages=0;
        do{GmailClient.Page result=gmail.search(token,query,page);found+=result.messages().size();for(GmailClient.MessageRef ref:result.messages()){
            if(imported.existsByUserIdAndExternalMessageId(userId,ref.id())){dupes++;continue;}
            ImportedMessage row=new ImportedMessage();row.userId=userId;row.gmailConnectionId=c.id;row.externalMessageId=ref.id();row.threadId=ref.threadId();row.processingStatus=ImportedMessage.Status.PROCESSING;imported.save(row);
            try{GmailClient.Message m=gmail.getMessage(token,ref.id());row.sender=trim(m.sender(),500);row.subject=trim(m.subject(),500);row.receivedAt=m.receivedAt();row.contentHash=sha256(m.subject()+"|"+m.plainText());processed++;
                if(!parser.supports(m.sender(),m.subject()+" "+m.plainText())){row.processingStatus=ImportedMessage.Status.REJECTED;rejected++;}
                else{BankEmailParser.Parsed p=parser.parse(m.subject()+" "+m.plainText());if(p.confidence()<.75){row.processingStatus=ImportedMessage.Status.REVIEW_REQUIRED;row.confidence=p.confidence();review++;}
                    else{Transaction t=new Transaction();t.userId=userId;t.description=p.description();t.amount=p.amount();t.date=LocalDate.ofInstant(m.receivedAt(),ZoneId.of("America/Lima"));t.merchant=p.merchant();t.category=p.category();t.source="GMAIL";t.externalId="gmail:"+ref.id();transactions.save(t);row.processingStatus=ImportedMessage.Status.IMPORTED;row.createdTransactionId=t.id;row.confidence=p.confidence();row.parserName="PeruBankParser";row.parserVersion="1.0";created++;}}
            }catch(Exception e){row.processingStatus=ImportedMessage.Status.FAILED;row.errorCode="PROCESSING_ERROR";failed++;}row.updatedAt=Instant.now();imported.save(row);
        }page=result.nextPageToken();pages++;}while(page!=null&&pages<5);
        c.lastSyncAt=Instant.now();c.updatedAt=Instant.now();oauth.save(c);return new GmailDtos.SyncResponse(found,processed,created,dupes,review,rejected,failed);
    }
    private String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String trim(String s,int max){return s==null?"":s.substring(0,Math.min(max,s.length()));}
}

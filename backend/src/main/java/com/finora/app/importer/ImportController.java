package com.finora.app.importer;
import com.finora.app.transaction.*; import org.springframework.http.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets; import java.util.*;
@RestController @RequestMapping("/api/import")
public class ImportController {
 private final BankEmailParser parser; private final TransactionRepository repo; public ImportController(BankEmailParser p,TransactionRepository r){parser=p;repo=r;}
 public record Request(String sender,String subject,String content,String externalId){}
 @PostMapping("/bank-email") public ResponseEntity<?> email(Authentication a,@RequestBody Request r){Long uid=(Long)a.getPrincipal();String eid=r.externalId()==null?UUID.nameUUIDFromBytes((r.subject()+r.content()).getBytes(StandardCharsets.UTF_8)).toString():r.externalId();if(repo.existsByUserIdAndExternalId(uid,eid))return ResponseEntity.status(409).body(Map.of("message","Esta notificación ya fue importada"));if(!parser.supports(r.sender(),r.content()))return ResponseEntity.unprocessableEntity().body(Map.of("message","No parece una notificación bancaria compatible"));var p=parser.parse(r.content());Transaction t=new Transaction();t.userId=uid;t.description=p.description();t.amount=p.amount();t.date=p.date();t.merchant=p.merchant();t.category=p.category();t.source="BANK_EMAIL";t.externalId=eid;repo.save(t);return ResponseEntity.status(201).body(Map.of("transaction",t,"confidence",p.confidence(),"method","RULE_WITH_AI_FALLBACK_READY"));}
}

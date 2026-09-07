package com.finora.app.transaction;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.http.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.*;

@RestController @RequestMapping("/api/transactions")
public class TransactionController {
 private final TransactionRepository repo; public TransactionController(TransactionRepository r){repo=r;}
 public record Request(@NotBlank String description,@DecimalMin("0.01") BigDecimal amount,@NotNull LocalDate date,@NotBlank String category,String type,String merchant,boolean recurring){}
 @GetMapping public List<Transaction> list(Authentication a,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to){LocalDate end=to==null?LocalDate.now():to,start=from==null?end.withDayOfMonth(1):from;return repo.findByUserIdAndDateBetweenOrderByDateDesc(uid(a),start,end);}
 @PostMapping public ResponseEntity<Transaction> create(Authentication a,@Valid @RequestBody Request r){Transaction t=new Transaction();apply(t,r);t.userId=uid(a);return ResponseEntity.status(201).body(repo.save(t));}
 @PutMapping("/{id}") public Transaction update(Authentication a,@PathVariable Long id,@Valid @RequestBody Request r){Transaction t=repo.findByIdAndUserId(id,uid(a)).orElseThrow();apply(t,r);return repo.save(t);}
 @DeleteMapping("/{id}") public void delete(Authentication a,@PathVariable Long id){repo.findByIdAndUserId(id,uid(a)).ifPresent(repo::delete);}
 private void apply(Transaction t,Request r){t.description=r.description();t.amount=r.amount();t.date=r.date();t.category=r.category();t.type=r.type()==null?"EXPENSE":r.type();t.merchant=r.merchant();t.recurring=r.recurring();}
 private Long uid(Authentication a){return(Long)a.getPrincipal();}
}

package com.finora.app.transaction;
import com.finora.app.category.CategoryClassifier;
import com.finora.app.merchant.MerchantNormalizer;
import com.finora.app.shared.error.ApiException;
import com.finora.app.admin.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
@Service
public class TransactionService {
 private final TransactionRepository repo;private final CategoryClassifier classifier;
 private ClassificationEngine engine;
 @Autowired public void setClassificationEngine(ClassificationEngine e){engine=e;}
 public TransactionService(TransactionRepository repo,CategoryClassifier classifier){this.repo=repo;this.classifier=classifier;}
 public List<TransactionResponse> list(Long uid,LocalDate from,LocalDate to){LocalDate end=to==null?LocalDate.now():to;LocalDate start=from==null?end.withDayOfMonth(1):from;if(start.isAfter(end))throw new ApiException(400,"INVALID_DATE_RANGE","La fecha inicial no puede ser posterior a la fecha final");return repo.findByUserIdAndDateBetweenOrderByDateDesc(uid,start,end).stream().map(TransactionResponse::from).toList();}
 @Transactional public TransactionResponse create(Long uid,TransactionRequest r){var t=new Transaction();t.userId=uid;apply(t,r,true);return TransactionResponse.from(repo.save(t));}
 @Transactional public TransactionResponse update(Long uid,Long id,TransactionRequest r){var t=repo.findById(id).orElseThrow(()->new ApiException(404,"TRANSACTION_NOT_FOUND","Movimiento no encontrado"));if(!uid.equals(t.userId))throw new ApiException(403,"TRANSACTION_FORBIDDEN","No puedes modificar un movimiento de otro usuario");apply(t,r,false);return TransactionResponse.from(repo.save(t));}
 @Transactional public void delete(Long uid,Long id){var t=repo.findById(id).orElseThrow(()->new ApiException(404,"TRANSACTION_NOT_FOUND","Movimiento no encontrado"));if(!uid.equals(t.userId))throw new ApiException(403,"TRANSACTION_FORBIDDEN","No puedes eliminar un movimiento de otro usuario");repo.delete(t);}
 private void apply(Transaction t,TransactionRequest r,boolean creating){
  var before=engine==null?null:engine.snapshot(t);boolean changed=!Objects.equals(t.category,r.category());String oldMerchant=t.merchant;String oldType=t.type;
  t.description=r.description().trim();if(t.originalDescription==null)t.originalDescription=t.description;t.amount=r.amount();t.date=r.date();t.type=r.type()==null||r.type().isBlank()?"EXPENSE":r.type();t.merchant=MerchantNormalizer.normalize(r.merchant()==null||r.merchant().isBlank()?r.description():r.merchant());t.recurring=r.recurring();
  if(r.currency()!=null){try{java.util.Currency.getInstance(r.currency());}catch(Exception e){throw new ApiException(400,"INVALID_CURRENCY","Moneda ISO no válida.");}t.currency=r.currency();}
  t.normalizedMerchant=ClassificationKeys.merchant(t.merchant);
  if(!creating&&!changed){
    if(engine!=null&&engine.manual(t))return;
    if(Objects.equals(oldMerchant,t.merchant)&&Objects.equals(oldType,t.type))return;
    if("EXPENSE".equals(t.type)){var inferred=classifier.classify(t.merchant,t.description,t.category);t.category=inferred.subcategory();t.macroCategory=inferred.macroCategory().name();if(engine!=null)engine.automatic(t);}else{t.macroCategory=null;t.categoryId=null;}
    return;
  }
  t.category=r.category();
  if("EXPENSE".equals(t.type)){
   if(engine!=null&&(!creating||!"Otros".equalsIgnoreCase(r.category()))){engine.userCategory(t,r.category(),t.userId,before);return;}
   var c=classifier.classify(t.merchant,t.description,t.category);t.macroCategory=c.macroCategory().name();t.category=c.subcategory();if(engine!=null)engine.automatic(t);
  }else{t.macroCategory=null;t.categoryId=null;}
 }
}

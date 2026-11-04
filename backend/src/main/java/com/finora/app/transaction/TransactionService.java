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
 private ManagedCategoryRepository catalog;
 @Autowired public void setCategoryCatalog(ManagedCategoryRepository catalog){this.catalog=catalog;}

 @Autowired public void setClassificationEngine(ClassificationEngine e){engine=e;}
 public TransactionService(TransactionRepository repo,CategoryClassifier classifier){this.repo=repo;this.classifier=classifier;}
 public List<TransactionResponse> list(Long uid,LocalDate from,LocalDate to){LocalDate end=to==null?LocalDate.now():to;LocalDate start=from==null?end.withDayOfMonth(1):from;if(start.isAfter(end))throw new ApiException(400,"INVALID_DATE_RANGE","La fecha inicial no puede ser posterior a la fecha final");return repo.findByUserIdAndDateBetweenOrderByDateDesc(uid,start,end).stream().map(TransactionResponse::from).toList();}
 private jakarta.persistence.criteria.Expression<String> searchText(jakarta.persistence.criteria.CriteriaBuilder cb,jakarta.persistence.criteria.Expression<String> value){
  return cb.function("translate",String.class,cb.lower(value),cb.literal("áéíóúüñ"),cb.literal("aeiouun"));
 }
 public record HistoryPage(List<TransactionResponse> items,long total,int page,int totalPages) {}
 @Transactional(readOnly=true)
 public HistoryPage history(Long uid,LocalDate from,LocalDate to,String search,String category,String type,String currency,int page,int size){
  if(page<0 || size<1 || size>100)throw new ApiException(400,"INVALID_PAGE","Pagina o tamano invalido.");
  if(from!=null && to!=null && from.isAfter(to))throw new ApiException(400,"INVALID_DATE_RANGE","Rango de fechas invalido.");
  if(search!=null && search.length()>200)throw new ApiException(400,"INVALID_SEARCH","Busqueda demasiado larga.");
  org.springframework.data.jpa.domain.Specification<Transaction> filter=(root,query,cb)->{
   var predicates=new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
   predicates.add(cb.equal(root.get("userId"),uid));
   if(from!=null)predicates.add(cb.greaterThanOrEqualTo(root.get("date"),from));
   if(to!=null)predicates.add(cb.lessThanOrEqualTo(root.get("date"),to));
   if(category!=null&&!category.isBlank()){
    var current=query.subquery(Long.class);var c=current.from(ManagedCategory.class);
    current.select(c.get("id")).where(cb.equal(c.get("name"),category));
    predicates.add(cb.or(root.get("categoryId").in(current),("Sin categoría".equals(category)?cb.isNull(root.get("categoryId")):cb.disjunction())));
   }
   if(type!=null&&!type.isBlank())predicates.add(cb.equal(root.get("type"),type));
   if(currency!=null&&!currency.isBlank())predicates.add(cb.equal(root.get("currency"),currency.trim().toUpperCase(Locale.ROOT)));
   if(search!=null&&!search.isBlank()){
    String pattern="%"+java.text.Normalizer.normalize(search.trim(),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
    predicates.add(cb.or(cb.like(searchText(cb,root.get("description")),pattern,'!'),cb.like(searchText(cb,root.get("merchant")),pattern,'!')));
   }
   return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
  };
  var result=repo.findAll(filter,org.springframework.data.domain.PageRequest.of(page,size,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"date","id")));
  var names=new java.util.HashMap<Long,ManagedCategory>();
  catalog.findAllById(result.getContent().stream().map(t->t.categoryId).filter(Objects::nonNull).distinct().toList()).forEach(c->names.put(c.id,c));
  var items=result.getContent().stream().map(t->{
   var r=TransactionResponse.from(t);if(!"EXPENSE".equals(t.type))return r;
   var c=names.get(t.categoryId);
   return new TransactionResponse(r.id(),r.userId(),r.description(),r.amount(),r.date(),c==null?"Sin categoría":c.name,c==null?"UNCATEGORIZED":"CAT_"+c.id,r.type(),r.source(),r.externalId(),r.merchant(),r.recurring(),r.createdAt(),r.currency(),r.categoryId(),r.classificationSource(),r.reviewStatus(),r.dateSource());
  }).toList();
  return new HistoryPage(items,result.getTotalElements(),page,result.getTotalPages());
 }
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

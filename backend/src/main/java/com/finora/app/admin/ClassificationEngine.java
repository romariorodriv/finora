package com.finora.app.admin;
import com.finora.app.transaction.Transaction;
import com.finora.app.shared.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.finora.app.admin.ClassificationKeys.*;
@Service
public class ClassificationEngine {
 private final ManagedCategoryRepository categories;
 private final ClassificationRuleRepository rules;
 private final AdminAuditRepository audit;
 private final ObjectMapper json;
 public ClassificationEngine(ManagedCategoryRepository c,ClassificationRuleRepository r,AdminAuditRepository a,ObjectMapper j){categories=c;rules=r;audit=a;json=j;}
 public static final Comparator<ClassificationRule> ORDER=Comparator
   .comparingInt((ClassificationRule r)->"EXACT".equals(r.matchType)?0:1)
   .thenComparingInt(r->r.userId==null?1:0)
   .thenComparing(Comparator.comparingInt((ClassificationRule r)->r.priority).reversed())
   .thenComparing(Comparator.comparingInt((ClassificationRule r)->r.merchantKey.length()).reversed())
   .thenComparing(r->r.id);
 public boolean matches(ClassificationRule r,Transaction t){String key=merchant(t.merchant==null||t.merchant.isBlank()?t.description:t.merchant);return (r.userId==null||r.userId.equals(t.userId)) && ("EXACT".equals(r.matchType)?key.equals(r.merchantKey):key.startsWith(r.merchantKey));}
 public Optional<ClassificationRule> winner(Transaction t){return rules.eligible(t.userId).stream().filter(r->matches(r,t)).filter(r->categories.findById(r.categoryId).map(c->c.active).orElse(false)).sorted(ORDER).findFirst();}
 public boolean manual(Transaction t){return t.classificationSource!=null&&t.classificationSource.startsWith("MANUAL");}
 public void automatic(Transaction t){
  t.normalizedMerchant=merchant(t.merchant==null||t.merchant.isBlank()?t.description:t.merchant);
  if(!"EXPENSE".equals(t.type)||manual(t))return;
  var winner=winner(t);
  if(winner.isPresent()){var rule=winner.get();assign(t,active(rule.categoryId),rule.userId==null?"GLOBAL_RULE":"USER_RULE",rule.id);return;}
  var category=categories.findByNameKey(name(t.category)).filter(c->c.active).orElse(null);
  if(category!=null)assign(t,category,"AUTOMATIC",null);
  else{t.categoryId=null;t.classificationSource="AUTOMATIC";t.classificationRuleId=null;t.reviewStatus="PENDING";}
 }
 public ManagedCategory active(Long id){var c=categories.findById(id==null?-1L:id).orElseThrow(ClassificationKeys::missing);if(!c.active)throw bad("La categoría está desactivada.");return c;}
 public void userCategory(Transaction t,String label,Long actor,AdminDtos.Snapshot before){
  var c=categories.findByNameKey(name(label)).orElseThrow(()->bad("Selecciona una categoría del catálogo."));if(!c.active)throw bad("La categoría está desactivada.");
  assign(t,c,"MANUAL_USER",null);
  if(t.id!=null)record(actor,"USER_CORRECTION","TRANSACTION",t.id.toString(),before,snapshot(t),null,"USER");
 }
 public void assign(Transaction t,ManagedCategory c,String origin,Long ruleId){t.categoryId=c.id;t.category=c.name;t.macroCategory=c.macroGroup;t.classificationSource=origin;t.classificationRuleId=ruleId;t.reviewStatus="Otros".equalsIgnoreCase(c.name)&&!origin.startsWith("MANUAL")?"PENDING":"REVIEWED";}
 public AdminDtos.Snapshot snapshot(Transaction t){return new AdminDtos.Snapshot(t.categoryId,t.category,t.macroCategory,t.classificationSource,t.classificationRuleId,t.reviewStatus,t.version);}
 public void restore(Transaction t,AdminDtos.Snapshot s){t.categoryId=s.categoryId();t.category=s.category();t.macroCategory=s.macroCategory();t.classificationSource=s.classificationSource();t.classificationRuleId=s.classificationRuleId();t.reviewStatus=s.reviewStatus();}
 public String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 public AdminDtos.Snapshot decode(String value){try{return json.readValue(value,AdminDtos.Snapshot.class);}catch(Exception e){throw new IllegalStateException(e);}}
 public void record(Long actor,String action,String type,String id,Object before,Object after,String batch,String actorKind){var a=new AdminAudit();a.actorId=actor;a.actorKind=actorKind;a.action=action;a.entityType=type;a.entityId=id;a.beforeJson=before instanceof String?(String)before:encode(before);a.afterJson=after instanceof String?(String)after:encode(after);a.batchId=batch;audit.save(a);}
}

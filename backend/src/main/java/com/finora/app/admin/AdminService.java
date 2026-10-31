package com.finora.app.admin;
import com.finora.app.transaction.*;
import com.finora.app.auth.UserRepository;
import com.finora.app.category.MacroCategory;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static com.finora.app.admin.AdminDtos.*;
import static com.finora.app.admin.ClassificationKeys.*;
@Service @Transactional
public class AdminService {
 private final ManagedCategoryRepository categories;private final ClassificationRuleRepository rules;
 private final TransactionRepository transactions;private final ClassificationBatchRepository batches;
 private final ClassificationChangeRepository changes;private final AdminAuditRepository audit;
 private final ClassificationEngine engine;private final UserRepository users;
 @PersistenceContext private EntityManager em;
 private static final Set<String> ICONS=Set.of("grid","wallet","rows","mail","message","trend","spark","shield","calendar","repeat","info");
 public AdminService(ManagedCategoryRepository c,ClassificationRuleRepository r,TransactionRepository t,ClassificationBatchRepository b,ClassificationChangeRepository h,AdminAuditRepository a,ClassificationEngine e,UserRepository u){categories=c;rules=r;transactions=t;batches=b;changes=h;audit=a;engine=e;users=u;}
 public PageRequest page(int page,int size){if(page<0||size<1||size>100)throw bad("Paginación inválida (máximo 100).");return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"));}
 private <T> PageResult<T> out(Page<T> p){return new PageResult<>(p.getContent(),p.getTotalElements(),p.getTotalPages(),p.getNumber());}
 public List<ManagedCategory> options(){return categories.findAllByOrderByNameAsc();}
 public PageResult<CategoryView> categories(String q,Boolean active,int p,int size){
  if(q!=null&&q.length()>100)throw bad("Búsqueda demasiado larga.");
  Specification<ManagedCategory> spec=(root,c,b)->b.and(q==null||q.isBlank()?b.conjunction():b.like(root.get("nameKey"),"%"+escape(name(q))+"%",'\\'),active==null?b.conjunction():b.equal(root.get("active"),active));
  return out(categories.findAll(spec,page(p,size)).map(c->new CategoryView(c.id,c.name,c.description,c.icon,c.color,c.macroGroup,c.active,c.version,transactions.countByCategoryIdAndType(c.id,"EXPENSE"))));
 }
 public ManagedCategory saveCategory(Long actor,Long id,CategoryInput input){
  if(input==null||input.name()==null||input.name().isBlank()||input.name().strip().length()>100)throw bad("El nombre debe tener entre 1 y 100 caracteres.");
  String key=name(input.name());if(key.isBlank())throw bad("Nombre inválido.");
  var duplicate=categories.findByNameKey(key);if(duplicate.isPresent()&&!duplicate.get().id.equals(id))throw conflict("Ya existe una categoría con ese nombre.");
  if((input.icon()==null||!ICONS.contains(input.icon()))||input.color()==null||!input.color().matches("#[0-9a-fA-F]{6}"))throw bad("Icono o color no válido.");
  if(input.description()!=null&&input.description().length()>500)throw bad("Descripción demasiado larga.");
  try{MacroCategory.valueOf(input.macroGroup()==null?"OTROS":input.macroGroup());}catch(Exception e){throw bad("Grupo de categoría inválido.");}
  var c=id==null?new ManagedCategory():categories.findById(id).orElseThrow(ClassificationKeys::missing);
  if(id!=null&&!Objects.equals(input.version(),c.version))throw conflict("La categoría cambió. Recarga antes de editar.");
  String before=id==null?null:engine.encode(c);String oldName=c.name;String oldGroup=c.macroGroup;
  c.name=input.name().strip();c.nameKey=key;c.description=input.description()==null?"":input.description();c.icon=input.icon();c.color=input.color();c.macroGroup=input.macroGroup()==null?"OTROS":input.macroGroup();c.active=input.active();
  categories.saveAndFlush(c);
  if(id!=null&&(!Objects.equals(oldName,c.name)||!Objects.equals(oldGroup,c.macroGroup)))em.createQuery("update Transaction t set t.category=:label,t.macroCategory=:macro,t.version=t.version+1 where t.categoryId=:id").setParameter("label",c.name).setParameter("macro",c.macroGroup).setParameter("id",c.id).executeUpdate();
  engine.record(actor,id==null?"CATEGORY_CREATE":"CATEGORY_UPDATE","CATEGORY",c.id.toString(),before,c,null,"ADMIN");return c;
 }
 private String escape(String q){return q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
 private Filters valid(Filters f){
  if(f==null)return Filters.empty();
  if(f.q()!=null&&f.q().length()>100)throw bad("Búsqueda demasiado larga.");
  if(f.from()!=null&&f.to()!=null&&f.from().isAfter(f.to()))throw bad("Rango de fechas inválido.");
  if(f.reviewStatus()!=null&&!f.reviewStatus().isBlank()&&!Set.of("PENDING","REVIEWED").contains(f.reviewStatus()))throw bad("Estado de revisión inválido.");return f;
 }
 private Specification<Transaction> spec(Filters input){Filters f=valid(input);return (root,q,b)->{
  var list=new ArrayList<Predicate>();list.add(b.equal(root.get("type"),"EXPENSE"));
  if(f.q()!=null&&!f.q().isBlank()){String term="%"+escape(f.q().strip().toLowerCase(Locale.ROOT))+"%";list.add(b.or(b.like(b.lower(root.get("merchant")),term,'\\'),b.like(b.lower(root.get("description")),term,'\\')));}
  if(f.from()!=null)list.add(b.greaterThanOrEqualTo(root.get("date"),f.from()));if(f.to()!=null)list.add(b.lessThanOrEqualTo(root.get("date"),f.to()));
  if(f.categoryId()!=null)list.add(b.equal(root.get("categoryId"),f.categoryId()));if(f.userId()!=null)list.add(b.equal(root.get("userId"),f.userId()));
  if(f.reviewStatus()!=null&&!f.reviewStatus().isBlank())list.add(b.equal(root.get("reviewStatus"),f.reviewStatus()));
  if(f.uncategorized())list.add(b.or(b.isNull(root.get("categoryId")),b.equal(root.get("reviewStatus"),"PENDING")));
  return b.and(list.toArray(Predicate[]::new));};}
 public TransactionsPage transactions(Filters f,int page,int size){return new TransactionsPage(out(transactions.findAll(spec(f),page(page,size)).map(this::view)),totals(f));}
 private List<CurrencyTotal> totals(Filters f){
  var b=em.getCriteriaBuilder();var query=b.createTupleQuery();var root=query.from(Transaction.class);Expression<String> currency=b.coalesce(root.get("currency"),"UNK");
  query.multiselect(currency,b.sum(root.<BigDecimal>get("amount")),b.count(root)).where(spec(f).toPredicate(root,query,b)).groupBy(currency).orderBy(b.asc(currency));
  return em.createQuery(query).getResultList().stream().map(t->new CurrencyTotal(t.get(0,String.class),t.get(1,BigDecimal.class),((Number)t.get(2)).longValue())).toList();
 }
 private TxView view(Transaction t){return new TxView(t.id,t.userId,t.date,redact(t.merchant),redact(merchant(t.merchant==null||t.merchant.isBlank()?t.description:t.merchant)),redact(t.description),t.amount,t.currency==null?"UNK":t.currency,t.categoryId,t.category,t.classificationSource,t.reviewStatus,t.source);}
 public PageResult<RuleView> rules(String q,int p,int size){if(q!=null&&q.length()>100)throw bad("Búsqueda demasiado larga.");Specification<ClassificationRule> s=(r,c,b)->q==null||q.isBlank()?b.conjunction():b.like(r.get("merchantKey"),"%"+escape(name(q))+"%",'\\');return out(rules.findAll(s,page(p,size)).map(this::ruleView));}
 private RuleView ruleView(ClassificationRule r){return new RuleView(r.id,redact(r.merchantKey),r.matchType,r.userId==null?"GLOBAL":"USER",r.userId,r.categoryId,r.priority,r.active,r.version);}
 public RuleView saveRule(Long actor,Long id,RuleInput input){
  if(input==null||input.merchant()==null||input.merchant().isBlank()||input.merchant().length()>200)throw bad("Confirma un comercio o prefijo de hasta 200 caracteres.");
  String key=merchant(input.merchant());if(key.length()>200||key.equals(name(com.finora.app.merchant.MerchantNormalizer.UNKNOWN))||key.contains("[dato oculto]")||key.contains("[correo oculto]"))throw bad("No se puede crear una regla para ese comercio.");
  if((input.matchType()==null||!Set.of("EXACT","PREFIX").contains(input.matchType()))||("PREFIX".equals(input.matchType())&&key.length()<5))throw bad("Usa coincidencia exacta o un prefijo explícito de al menos 5 caracteres.");
  if((input.scope()==null||!Set.of("GLOBAL","USER").contains(input.scope()))||input.priority()<0||input.priority()>1000)throw bad("Alcance o prioridad no válido.");
  Long owner="USER".equals(input.scope())?input.userId():null;if("USER".equals(input.scope())&&(owner==null||!users.existsById(owner)))throw bad("Selecciona una cuenta existente para la regla personal.");
  if(input.active())engine.active(input.categoryId());else categories.findById(input.categoryId()==null?-1L:input.categoryId()).orElseThrow(ClassificationKeys::missing);String scope=owner==null?"GLOBAL":"USER:"+owner;
  if(rules.existsByMatchTypeAndMerchantKeyAndScopeKeyAndIdNot(input.matchType(),key,scope,id==null?-1L:id))throw conflict("Ya existe una regla para este comercio, tipo y alcance. Edita esa regla.");
  var rule=id==null?new ClassificationRule():rules.findById(id).orElseThrow(ClassificationKeys::missing);
  if(id!=null&&!Objects.equals(input.version(),rule.version))throw conflict("La regla cambió. Recarga antes de editar.");
  String before=id==null?null:engine.encode(rule);
  rule.merchantKey=key;rule.matchType=input.matchType();rule.userId=owner;rule.scopeKey=scope;rule.categoryId=input.categoryId();rule.priority=input.priority();rule.active=input.active();rules.saveAndFlush(rule);
  engine.record(actor,id==null?"RULE_CREATE":"RULE_UPDATE","RULE",rule.id.toString(),before,rule,null,"ADMIN");return ruleView(rule);
 }
 private ClassificationBatch fresh(Long actor,String kind,Long target,Filters f){var c=engine.active(target);var b=new ClassificationBatch();b.id=UUID.randomUUID().toString();b.actorId=actor;b.kind=kind;b.targetCategoryId=target;b.targetCategoryVersion=c.version;b.filterJson=engine.encode(valid(f));return b;}
 private ClassificationChange change(ClassificationBatch b,Transaction t,ManagedCategory target,String origin,Long ruleId){var c=new ClassificationChange();c.batchId=b.id;c.transactionId=t.id;c.beforeJson=engine.encode(engine.snapshot(t));c.afterJson=engine.encode(new Snapshot(target.id,target.name,target.macroGroup,origin,ruleId,"REVIEWED",t.version));return c;}
 public BatchView correct(Long actor,Correction request){
  if(request==null||!request.confirmed()||request.transactionIds()==null||request.transactionIds().isEmpty()||request.transactionIds().size()>200||request.transactionIds().stream().anyMatch(Objects::isNull))throw bad("Confirma una selección de 1 a 200 consumos.");
  var target=engine.active(request.categoryId());var b=fresh(actor,"MANUAL_CORRECTION",target.id,Filters.empty());batches.save(b);
  for(Long id:new TreeSet<>(request.transactionIds())){var t=transactions.locked(id).orElseThrow(ClassificationKeys::missing);if(!"EXPENSE".equals(t.type))throw bad("Solo se corrigen consumos.");var c=change(b,t,target,"MANUAL_ADMIN",null);engine.assign(t,target,"MANUAL_ADMIN",null);transactions.saveAndFlush(t);c.afterJson=engine.encode(engine.snapshot(t));changes.save(c);engine.record(actor,"CORRECTION","TRANSACTION",id.toString(),engine.decode(c.beforeJson),engine.snapshot(t),b.id,"ADMIN");b.changedCount++;}
  b.status="APPLIED";b.appliedAt=Instant.now();batches.save(b);return batch(actor,b.id);
 }
 public BatchView previewRule(Long actor,Long id,Filters f){var rule=rules.findById(id).orElseThrow(ClassificationKeys::missing);if(!rule.active)throw bad("Activa la regla antes de previsualizarla.");f=valid(f);if(rule.userId!=null){if(f.userId()!=null&&!rule.userId.equals(f.userId()))throw bad("La cuenta del filtro no corresponde al alcance de la regla.");f=new Filters(f.q(),f.from(),f.to(),f.categoryId(),f.reviewStatus(),rule.userId,f.uncategorized());}var b=fresh(actor,"RULE_APPLY",rule.categoryId,f);b.ruleId=id;b.ruleVersion=rule.version;return preview(b,valid(f),rule);}
 public BatchView previewReassign(Long actor,Long source,Reassign input){
  if(input==null||Objects.equals(source,input.targetCategoryId()))throw bad("Selecciona otra categoría de destino.");categories.findById(source).orElseThrow(ClassificationKeys::missing);var f=valid(input.filters());f=new Filters(f.q(),f.from(),f.to(),source,f.reviewStatus(),f.userId(),f.uncategorized());return preview(fresh(actor,"CATEGORY_REASSIGN",input.targetCategoryId(),f),f,null);
 }
 private BatchView preview(ClassificationBatch b,Filters f,ClassificationRule rule){
  batches.save(b);var target=engine.active(b.targetCategoryId);int page=0,scanned=0;Page<Transaction> rows;
  do{rows=transactions.findAll(spec(f),PageRequest.of(page++,300,Sort.by("id")));
   for(var t:rows){if(++scanned>100000)throw bad("Afina los filtros: la vista previa supera 100.000 consumos examinados.");
    if(rule!=null&&!engine.matches(rule,t))continue;if(engine.manual(t)){b.skippedManual++;continue;}
    if(rule!=null&&!engine.winner(t).map(w->w.id.equals(rule.id)).orElse(false))continue;
    if(Objects.equals(t.categoryId,target.id)&&Objects.equals(t.category,target.name)&&!"PENDING".equals(t.reviewStatus))continue;
    if(++b.changedCount>2000)throw bad("Afina los filtros: el máximo por lote es 2.000 cambios.");
    changes.save(change(b,t,target,rule==null?"MANUAL_ADMIN":rule.userId==null?"GLOBAL_RULE":"USER_RULE",rule==null?null:rule.id));
   }
   em.flush();em.clear();
  }while(rows.hasNext());batches.saveAndFlush(b);engine.record(b.actorId,"PREVIEW_CREATE","BATCH",b.id,null,Map.of("count",b.changedCount,"scope",b.filterJson),b.id,"ADMIN");return batch(b.actorId,b.id);
 }
 public BatchView batch(Long actor,String id){var b=batches.findById(id).orElseThrow(ClassificationKeys::missing);var entries=changes.findByBatchIdOrderByTransactionIdAsc(id);var examples=new ArrayList<TxView>();var sums=new TreeMap<String,BigDecimal>();var counts=new TreeMap<String,Long>();
  for(var t:transactions.findAllById(entries.stream().map(c->c.transactionId).toList()).stream().sorted(Comparator.comparing(x->x.id)).toList()){if(examples.size()<10)examples.add(view(t));String currency=t.currency==null?"UNK":t.currency;sums.merge(currency,t.amount,BigDecimal::add);counts.merge(currency,1L,Long::sum);}
  return new BatchView(b.id,b.kind,b.status,b.changedCount,b.skippedManual,b.targetCategoryId,b.ruleId,b.expiresAt,redact(b.filterJson),examples,sums.entrySet().stream().map(e->new CurrencyTotal(e.getKey(),e.getValue(),counts.get(e.getKey()))).toList());
 }
 public BatchView apply(Long actor,String id,Confirmation confirmation){
  if(confirmation==null||!confirmation.confirmed())throw bad("Confirma explícitamente el alcance antes de aplicar.");var b=batches.locked(id).orElseThrow(ClassificationKeys::missing);
  if(!actor.equals(b.actorId)||!"PREVIEW".equals(b.status)||b.expiresAt.isBefore(Instant.now()))throw conflict("La vista previa no está disponible. Genera una nueva.");
  var target=engine.active(b.targetCategoryId);if(!Objects.equals(target.version,b.targetCategoryVersion))throw conflict("La categoría cambió desde la vista previa.");
  if(b.ruleId!=null){var rule=rules.findById(b.ruleId).orElseThrow(ClassificationKeys::missing);if(!rule.active||!Objects.equals(rule.version,b.ruleVersion))throw conflict("La regla cambió desde la vista previa.");}
  for(var c:changes.findByBatchIdOrderByTransactionIdAsc(id)){var t=transactions.locked(c.transactionId).orElseThrow(()->conflict("Un consumo fue eliminado. Genera otra vista previa."));if(!engine.snapshot(t).equals(engine.decode(c.beforeJson))||engine.manual(t))throw conflict("Un consumo cambió desde la vista previa. No se aplicó el lote.");if(b.ruleId!=null&&!engine.winner(t).map(w->w.id.equals(b.ruleId)).orElse(false))throw conflict("Cambió la prioridad de las reglas. Genera otra vista previa.");var next=engine.decode(c.afterJson);engine.restore(t,next);transactions.saveAndFlush(t);c.afterJson=engine.encode(engine.snapshot(t));changes.save(c);engine.record(actor,"BATCH_APPLY","TRANSACTION",t.id.toString(),engine.decode(c.beforeJson),engine.snapshot(t),id,"ADMIN");}
  b.status="APPLIED";b.appliedAt=Instant.now();batches.save(b);return batch(actor,id);
 }
 public BatchView revert(Long actor,String id,Confirmation confirmation){
  if(confirmation==null||!confirmation.confirmed())throw bad("Confirma la reversión del lote.");var b=batches.locked(id).orElseThrow(ClassificationKeys::missing);if(!"APPLIED".equals(b.status))throw conflict("Solo se puede revertir una aplicación vigente.");
  for(var c:changes.findByBatchIdOrderByTransactionIdAsc(id)){var t=transactions.locked(c.transactionId).orElseThrow(()->conflict("Un consumo fue eliminado. No se puede revertir automáticamente."));if(!engine.snapshot(t).equals(engine.decode(c.afterJson)))throw conflict("Hay cambios posteriores. No se revirtió ningún consumo.");var before=engine.snapshot(t);var original=engine.decode(c.beforeJson);if(original.categoryId()!=null&&!categories.existsById(original.categoryId()))throw conflict("La categoría original ya no existe.");engine.restore(t,original);transactions.saveAndFlush(t);engine.record(actor,"BATCH_REVERT","TRANSACTION",t.id.toString(),before,engine.snapshot(t),id,"ADMIN");}
  b.status="REVERTED";b.revertedAt=Instant.now();batches.save(b);return batch(actor,id);
 }
 public PageResult<AdminAudit> history(int p,int size){return out(audit.findAll(page(p,size)));}
 public PageResult<ClassificationBatch> batches(int p,int size){return out(batches.findAll(page(p,size).withSort(Sort.by(Sort.Direction.DESC,"createdAt","id"))));}
}

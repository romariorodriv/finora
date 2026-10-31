package com.finora.app.admin;
import com.finora.app.auth.*;
import com.finora.app.config.JwtService;
import com.finora.app.transaction.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class AdminIntegrationTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired UserRepository users;@Autowired JwtService jwt;
 @Autowired TransactionRepository transactions;@Autowired ManagedCategoryRepository categories;@Autowired ClassificationRuleRepository rules;@Autowired AdminAuditRepository audits;@Autowired ClassificationEngine engine;
 User admin,owner,other;String adminToken,token,nonce;
 @BeforeEach void setup(){nonce=UUID.randomUUID().toString().substring(0,8);admin=user("admin",true);owner=user("owner",false);other=user("other",false);adminToken=bearer(admin);token=bearer(owner);}
 User user(String prefix,boolean admin){var u=new User(prefix,prefix+nonce+"@example.test","unused-test-hash");u.role=admin?"ADMIN":"USER";return users.saveAndFlush(u);}
 String bearer(User u){return "Bearer "+jwt.issue(u.id,u.email);}
 String body(Object o)throws Exception{return json.writeValueAsString(o);}
 JsonNode getAs(String path,String auth)throws Exception{return json.readTree(mvc.perform(get("/api/v1"+path).header("Authorization",auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 JsonNode postAs(String path,Object payload,String auth)throws Exception{return json.readTree(mvc.perform(post("/api/v1"+path).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(body(payload))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 JsonNode category(String label)throws Exception{return postAs("/admin/categories",Map.of("name",label+nonce,"description","Descripción QA","icon","grid","color","#187650","macroGroup","OTROS","active",true),adminToken);}
 Transaction tx(User u,String merchant,String currency,String source){var t=new Transaction();t.userId=u.id;t.description="Descripción original de prueba";t.originalDescription=t.description;t.merchant=merchant;t.category="Otros";t.categoryId=categories.findByNameKey("otros").orElseThrow().id;t.macroCategory="OTROS";t.amount=new BigDecimal("10.00");t.currency=currency;t.date=LocalDate.now();t.classificationSource=source;t.reviewStatus="PENDING";return transactions.saveAndFlush(t);}
 JsonNode rule(String merchant,Long category,String scope,Long uid,int priority)throws Exception{var b=new HashMap<String,Object>();b.put("merchant",merchant);b.put("categoryId",category);b.put("scope",scope);b.put("matchType","EXACT");b.put("priority",priority);b.put("active",true);if(uid!=null)b.put("userId",uid);return postAs("/admin/rules",b,adminToken);}
 @Test void allAdminReadsAndWritesRequireAdminAndRegistrationCannotEscalate()throws Exception{
  for(String p:new String[]{"/categories","/category-options","/transactions","/rules","/history","/batches","/batches/missing"})mvc.perform(get("/api/v1/admin"+p).header("Authorization",token)).andExpect(status().isForbidden());
  for(String p:new String[]{"/categories","/rules","/corrections","/rules/1/preview","/categories/1/reassign-preview","/batches/1/apply","/batches/1/revert"})mvc.perform(post("/api/admin"+p).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
  for(String p:new String[]{"/categories/1","/rules/1"})mvc.perform(put("/api/v1/admin"+p).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
  mvc.perform(get("/api/admin/categories")).andExpect(status().isForbidden());
  var registered=json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name","Registro QA","email","register"+nonce+"@example.test","password","Local-test-123!","role","ADMIN")))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  assertEquals("USER",registered.get("role").asText());assertEquals("USER",getAs("/account",token).get("role").asText());
 }
 @Test void checksCurrentDatabaseRoleEvenWithAnExistingJwt()throws Exception{getAs("/admin/categories",adminToken);admin.role="USER";users.saveAndFlush(admin);mvc.perform(get("/api/admin/categories").header("Authorization",adminToken)).andExpect(status().isForbidden());}
 @Test void categoryCrudValidatesDuplicatesCountsAssociationsAndPreservesDescriptions()throws Exception{
  var cat=category("Nueva ");long id=cat.get("id").asLong();var t=tx(owner,"Comercio "+nonce,"PEN","AUTOMATIC");
  postAs("/admin/corrections",Map.of("transactionIds",List.of(t.id),"categoryId",id,"confirmed",true),adminToken);
  var page=getAs("/admin/categories?q="+nonce,adminToken);assertEquals(1,page.get("items").get(0).get("transactionCount").asLong());
  var renamed=Map.of("name","Renombrada "+nonce,"description","Editada","icon","wallet","color","#123F30","macroGroup","OTROS","active",false,"version",cat.get("version").asLong());
  mvc.perform(put("/api/admin/categories/"+id).header("Authorization",adminToken).contentType(MediaType.APPLICATION_JSON).content(body(renamed))).andExpect(status().isOk());
  var saved=transactions.findById(t.id).orElseThrow();assertEquals("Renombrada "+nonce,saved.category);assertEquals("Descripción original de prueba",saved.description);assertEquals(saved.description,saved.originalDescription);
  assertFalse(getAs("/categories",token).toString().contains("Renombrada "+nonce));
  mvc.perform(post("/api/admin/categories").header("Authorization",adminToken).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name","RENOMBRADA "+nonce,"icon","grid","color","#187650","macroGroup","OTROS","active",true)))).andExpect(status().isConflict());
  assertEquals(1,getAs("/admin/transactions?categoryId="+id,adminToken).get("page").get("totalElements").asInt());
 }
 @Test void separatesCurrencyTotalsPaginatesAndRetainsNormalUserIsolation()throws Exception{
  tx(owner,"Moneda "+nonce,"PEN","AUTOMATIC");tx(owner,"Moneda "+nonce,"USD","AUTOMATIC");tx(other,"Moneda "+nonce,"UNK","AUTOMATIC");
  var view=getAs("/admin/transactions?size=1&userId="+owner.id,adminToken);assertEquals(2,view.get("page").get("totalElements").asInt());assertEquals(1,view.get("page").get("items").size());assertEquals(2,view.get("totals").size());
  assertEquals(2,getAs("/transactions",token).size());var summary=getAs("/dashboard?currency=USD",token);assertEquals(10,summary.get("expenses").decimalValue().intValue());assertEquals("USD",summary.get("currency").asText());
  mvc.perform(get("/api/admin/transactions?size=101").header("Authorization",adminToken)).andExpect(status().isBadRequest());
 }
 @Test void manualIndividualAndBulkCorrectionsAreAuditedAndNeverCreateRules()throws Exception{
  var c=category("Manual ");var a=tx(owner,"Manual "+nonce,"PEN","AUTOMATIC");var b=tx(other,"Manual "+nonce,"USD","AUTOMATIC");long count=rules.count();
  var batch=postAs("/admin/corrections",Map.of("transactionIds",List.of(a.id,b.id),"categoryId",c.get("id").asLong(),"confirmed",true),adminToken);
  assertEquals(2,batch.get("count").asInt());assertEquals(count,rules.count());assertEquals("MANUAL_ADMIN",transactions.findById(a.id).orElseThrow().classificationSource);
  assertTrue(audits.findAll().stream().anyMatch(e->"CORRECTION".equals(e.action)&&admin.id.equals(e.actorId)&&e.beforeJson!=null&&e.afterJson!=null));
  mvc.perform(post("/api/admin/corrections").header("Authorization",adminToken).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("transactionIds",List.of(a.id),"categoryId",c.get("id").asLong(),"confirmed",false)))).andExpect(status().isBadRequest());
 }
 @Test void rulesAffectNewTransactionsButDoNotChangeHistoryUntilExplicitApplication()throws Exception{
  var c=category("Regla ");String merchant="Comercio exacto "+nonce;var old=tx(owner,merchant,"PEN","AUTOMATIC");var manual=tx(owner,merchant,"PEN","MANUAL_USER");
  var rule=rule(merchant,c.get("id").asLong(),"GLOBAL",null,100);assertEquals("Otros",transactions.findById(old.id).orElseThrow().category);
  var preview=postAs("/admin/rules/"+rule.get("id").asLong()+"/preview",Map.of("userId",owner.id),adminToken);assertEquals(1,preview.get("count").asInt());assertEquals(1,preview.get("skippedManual").asInt());assertEquals("Otros",transactions.findById(old.id).orElseThrow().category);
  postAs("/admin/batches/"+preview.get("id").asText()+"/apply",Map.of("confirmed",true),adminToken);assertEquals(c.get("id").asLong(),transactions.findById(old.id).orElseThrow().categoryId);assertEquals("MANUAL_USER",transactions.findById(manual.id).orElseThrow().classificationSource);
  var created=mvc.perform(post("/api/transactions").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("description","Consumo nuevo","merchant",merchant,"amount",20,"date",LocalDate.now().toString(),"category","Otros","type","EXPENSE","recurring",false)))).andExpect(status().isCreated()).andReturn();
  assertEquals("GLOBAL_RULE",json.readTree(created.getResponse().getContentAsString()).get("classificationSource").asText());
  postAs("/admin/batches/"+preview.get("id").asText()+"/revert",Map.of("confirmed",true),adminToken);assertEquals("Otros",transactions.findById(old.id).orElseThrow().category);
 }
 @Test void stalePreviewAndChangedRuleAreRejectedWithoutOverwritingManualEdits()throws Exception{
  var c=category("Cambio ");String merchant="Cambio "+nonce;var old=tx(owner,merchant,"PEN","AUTOMATIC");var rule=rule(merchant,c.get("id").asLong(),"GLOBAL",null,100);
  var preview=postAs("/admin/rules/"+rule.get("id").asLong()+"/preview",Map.of("userId",owner.id),adminToken);
  postAs("/admin/corrections",Map.of("transactionIds",List.of(old.id),"categoryId",c.get("id").asLong(),"confirmed",true),adminToken);
  mvc.perform(post("/api/admin/batches/"+preview.get("id").asText()+"/apply").header("Authorization",adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}")).andExpect(status().isConflict());
  assertEquals("MANUAL_ADMIN",transactions.findById(old.id).orElseThrow().classificationSource);
 }
 @Test void cannotRevertOverSubsequentCorrections()throws Exception{
  var c=category("Reversión ");var a=tx(owner,"Revertir "+nonce,"PEN","AUTOMATIC");var batch=postAs("/admin/corrections",Map.of("transactionIds",List.of(a.id),"categoryId",c.get("id").asLong(),"confirmed",true),adminToken);
  postAs("/admin/corrections",Map.of("transactionIds",List.of(a.id),"categoryId",categories.findByNameKey("otros").orElseThrow().id,"confirmed",true),adminToken);
  mvc.perform(post("/api/admin/batches/"+batch.get("id").asText()+"/revert").header("Authorization",adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}")).andExpect(status().isConflict());
 }
 @Test void exactRulesAndAccountScopesHaveDeterministicPrecedence()throws Exception{
  var global=category("Global ");var personal=category("Personal ");String merchant="Prioridad "+nonce;
  rule(merchant,global.get("id").asLong(),"GLOBAL",null,900);var userRule=rule(merchant,personal.get("id").asLong(),"USER",owner.id,1);
  var t=tx(owner,merchant,"PEN","AUTOMATIC");assertEquals(userRule.get("id").asLong(),engine.winner(t).orElseThrow().id);
  var another=tx(other,merchant,"PEN","AUTOMATIC");assertEquals(global.get("id").asLong(),engine.winner(another).orElseThrow().categoryId);
  t.classificationSource="MANUAL_USER";engine.automatic(t);assertEquals("Otros",t.category);
  var prefix=postAs("/admin/rules",Map.of("merchant","Prioridad","matchType","PREFIX","scope","USER","userId",other.id,"categoryId",personal.get("id").asLong(),"priority",1000,"active",true),adminToken);
  assertEquals(global.get("id").asLong(),engine.winner(another).orElseThrow().categoryId);
 }
 @Test void adminViewsRedactSensitiveStringsAndNeverReturnCredentials()throws Exception{
  var t=tx(owner,"Local "+nonce,"PEN","AUTOMATIC");t.description="Contacto private@example.test cuenta 123456789012";transactions.saveAndFlush(t);
  String response=getAs("/admin/transactions?userId="+owner.id,adminToken).toString();assertFalse(response.contains("private@example.test"));assertFalse(response.contains("123456789012"));assertFalse(response.contains("passwordHash"));assertFalse(response.contains("Bearer"));
 }
 @Test void staleSecondRowRollsBackTheEntireHistoricalApplication()throws Exception{
  var cat=category("Atómica ");String merchant="Atómica "+nonce;var first=tx(owner,merchant,"PEN","AUTOMATIC");var second=tx(owner,merchant,"PEN","AUTOMATIC");var r=rule(merchant,cat.get("id").asLong(),"GLOBAL",null,100);
  var preview=postAs("/admin/rules/"+r.get("id").asLong()+"/preview",Map.of("userId",owner.id),adminToken);
  postAs("/admin/corrections",Map.of("transactionIds",List.of(second.id),"categoryId",cat.get("id").asLong(),"confirmed",true),adminToken);
  mvc.perform(post("/api/admin/batches/"+preview.get("id").asText()+"/apply").header("Authorization",adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}")).andExpect(status().isConflict());
  assertEquals("Otros",transactions.findById(first.id).orElseThrow().category);assertEquals("MANUAL_ADMIN",transactions.findById(second.id).orElseThrow().classificationSource);
 }
 @Test void aRuleEditedAfterPreviewCannotBeApplied()throws Exception{
  var cat=category("Versión ");String merchant="Versión "+nonce;var t=tx(owner,merchant,"PEN","AUTOMATIC");var rule=rule(merchant,cat.get("id").asLong(),"GLOBAL",null,100);
  var preview=postAs("/admin/rules/"+rule.get("id").asLong()+"/preview",Map.of("userId",owner.id),adminToken);
  mvc.perform(put("/api/admin/rules/"+rule.get("id").asLong()).header("Authorization",adminToken).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("merchant",merchant,"matchType","EXACT","scope","GLOBAL","categoryId",cat.get("id").asLong(),"priority",101,"active",true,"version",rule.get("version").asLong())))).andExpect(status().isOk());
  mvc.perform(post("/api/admin/batches/"+preview.get("id").asText()+"/apply").header("Authorization",adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}")).andExpect(status().isConflict());assertEquals("Otros",transactions.findById(t.id).orElseThrow().category);
 }
 @Test void reassignmentPreviewRespectsManualCorrectionsAndUpdatesTheRegularDashboard()throws Exception{
  var cat=category("Reasignada ");var a=tx(owner,"Reasignar "+nonce,"PEN","AUTOMATIC");var manual=tx(owner,"Reasignar "+nonce,"PEN","MANUAL_USER");long source=categories.findByNameKey("otros").orElseThrow().id;
  var preview=postAs("/admin/categories/"+source+"/reassign-preview",Map.of("targetCategoryId",cat.get("id").asLong(),"filters",Map.of("userId",owner.id)),adminToken);assertEquals(1,preview.get("count").asInt());assertEquals(1,preview.get("skippedManual").asInt());
  postAs("/admin/batches/"+preview.get("id").asText()+"/apply",Map.of("confirmed",true),adminToken);assertEquals(source,transactions.findById(manual.id).orElseThrow().categoryId);
  assertTrue(getAs("/dashboard",token).get("macroCategories").toString().contains(cat.get("name").asText()));
 }
}

package com.finora.app;

import com.finora.app.auth.*;
import com.finora.app.config.JwtService;
import com.finora.app.transaction.Transaction;
import com.finora.app.transaction.TransactionRepository;
import com.finora.app.admin.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class ReadinessIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired UserRepository users;
 @Autowired JwtService jwt;
 @Autowired TransactionRepository transactions;
 @Autowired ManagedCategoryRepository categories;
 private LocalDate today(){return LocalDate.now(ZoneId.of("America/Lima"));}
 private User user(String name){return users.save(new User(name,name+"@readiness.test","hash"));}
 private String auth(User u){return "Bearer "+jwt.issue(u.id,u.email);}
 private Transaction expense(User u,LocalDate date,String currency,String description){
  var t=new Transaction();t.userId=u.id;t.date=date;t.currency=currency;t.description=description;t.merchant=description;t.category="Old label";t.amount=new BigDecimal("300.00");return transactions.save(t);
 }
 @Test void historyUsesAllDatesStablePagesFiltersAndCurrentCategoryNames() throws Exception {
  var u=user("history");var other=user("other-history");
  var c=categories.findAll().getFirst();c.name="Renamed readiness category";categories.save(c);
  var d=today().minusDays(89);
  var first=expense(u,d,"PEN","Coffee 100%_special");first.categoryId=c.id;transactions.save(first);
  var second=expense(u,d,"USD","Coffee 100%_special");second.categoryId=c.id;transactions.save(second);
  expense(other,d,"PEN","Coffee 100%_special");expense(u,today(),"PEN","Other");
  mvc.perform(get("/api/v1/transactions/history").header("Authorization",auth(u)).param("from",d.minusDays(1).toString()).param("to",d.toString()).param("search","100%_").param("category",c.name).param("size","1"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items[0].id").value(second.id)).andExpect(jsonPath("$.items[0].category").value(c.name));
  mvc.perform(get("/api/v1/transactions/history").header("Authorization",auth(u)).param("to",d.toString()).param("size","1").param("page","1"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(first.id));
  mvc.perform(get("/api/v1/transactions/history").header("Authorization",auth(u)).param("currency","USD").param("type","EXPENSE"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
  mvc.perform(get("/api/v1/transactions/history").header("Authorization",auth(u)).param("search","absent"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
  for(var size:new String[]{"0","101"})mvc.perform(get("/api/v1/transactions/history").header("Authorization",auth(u)).param("size",size)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/transactions/history").header("Authorization",auth(u)).param("from",today().toString()).param("to",d.toString())).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/transactions/history")).andExpect(status().isUnauthorized());
 }
 @Test void assistantSeparatesCurrenciesUsesCatalogAndOnlyReportsKnownRecurrence() throws Exception {
  var u=user("assistant");var other=user("other-assistant");
  var c=categories.findAll().getFirst();c.name="Current category";categories.save(c);
  var pen=expense(u,today(),"PEN","Shop");pen.categoryId=c.id;transactions.save(pen);
  expense(u,today(),"USD","Other shop");expense(other,today(),"PEN","Private");
  mvc.perform(post("/api/assistant").header("Authorization",auth(u)).contentType("application/json").content("{\"message\":\"Cuanto llevo este mes?\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.answer",allOf(containsString("PEN 300.00"),containsString("USD 300.00"),not(containsString("600.00")))));
  mvc.perform(post("/api/assistant").header("Authorization",auth(u)).contentType("application/json").content("{\"message\":\"En que gaste mas?\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.answer",containsString(c.name)));
  mvc.perform(post("/api/assistant").header("Authorization",auth(u)).contentType("application/json").content("{\"message\":\"Cuanto llevo este mes?\",\"currency\":\"USD\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.answer",not(containsString("PEN"))));
  mvc.perform(post("/api/assistant").header("Authorization",auth(u)).contentType("application/json").content("{\"message\":\"Que pagos se repiten?\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.answer",containsString("No hay gastos marcados")));
  pen.recurring=true;transactions.save(pen);
  mvc.perform(post("/api/assistant").header("Authorization",auth(u)).contentType("application/json").content("{\"message\":\"Que pagos se repiten?\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.answer",containsString("No confirma frecuencia")));
  mvc.perform(post("/api/assistant").header("Authorization",auth(u)).contentType("application/json").content("{\"message\":\"Predice mis ingresos\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.answer",containsString("no está soportada")));
 }
}

package com.finora.app.planning;

import com.finora.app.auth.User;
import com.finora.app.auth.UserRepository;
import com.finora.app.config.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanningAuthorizationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired PlanningProfileRepository profiles;
  @Autowired PlanningPaymentRepository payments;
  @Autowired JwtService jwt;
  @Autowired jakarta.persistence.EntityManager entityManager;
  private LocalDate today() { return LocalDate.now(ZoneId.of("America/Lima")); }
  private String token(String email) {
    var user = users.save(new User(email,email,"test-hash"));
    return "Bearer " + jwt.issue(user.id,email);
  }
  private String profile() throws Exception {
    return json.writeValueAsString(Map.of("confirmedBalance",1000,"reserve",50,"nextIncomeDate",today().plusDays(10).toString()));
  }
  @Test void persistsStateAndIsolatesUsersIncludingSpoofedOwnership() throws Exception {
    var alice = token("alice-planning@test.local");
    var bob = token("bob-planning@test.local");
    mvc.perform(put("/api/v1/planning/state").header("Authorization",alice).contentType("application/json").content(profile()))
      .andExpect(status().isOk()).andExpect(jsonPath("$.projection.available").value(950));
    var result = mvc.perform(post("/api/v1/planning/payments").header("Authorization",alice).contentType("application/json")
      .content(json.writeValueAsString(Map.of("name","Alquiler","dueDate",today().plusDays(1).toString(),"amount",300,"userId",999))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.projection.available").value(650)).andReturn();
    var id = json.readTree(result.getResponse().getContentAsString()).get("payments").get(0).get("id").asLong();
    mvc.perform(get("/api/planning/state").header("Authorization",bob))
      .andExpect(status().isOk()).andExpect(jsonPath("$.profile").isEmpty()).andExpect(jsonPath("$.payments").isEmpty());
    mvc.perform(put("/api/planning/state").header("Authorization",bob).contentType("application/json")
      .content(profile().replace("1000", "2000"))).andExpect(status().isOk());
    mvc.perform(delete("/api/v1/planning/payments/{id}",id).header("Authorization",bob)).andExpect(status().isNotFound());
    entityManager.flush();
    entityManager.clear();
    mvc.perform(get("/api/v1/planning/state").header("Authorization",alice))
      .andExpect(status().isOk()).andExpect(jsonPath("$.profile.confirmedBalance").value(1000))
      .andExpect(jsonPath("$.payments[0].id").value(id)).andExpect(jsonPath("$.alerts[0]").exists());
    mvc.perform(delete("/api/planning/payments/{id}",id).header("Authorization",alice))
      .andExpect(status().isOk()).andExpect(jsonPath("$.payments").isEmpty()).andExpect(jsonPath("$.projection.available").value(950));
    assertFalse(payments.existsById(id));
    assertEquals(2,profiles.count());
  }
  @Test void requiresAuthenticationOnEveryPlanningOperationAndAlias() throws Exception {
    for (var base : new String[]{"/api/planning","/api/v1/planning"}) {
      mvc.perform(get(base+"/state")).andExpect(status().isForbidden());
      mvc.perform(put(base+"/state").contentType("application/json").content(profile())).andExpect(status().isForbidden());
      mvc.perform(post(base+"/payments").contentType("application/json").content("{}")).andExpect(status().isForbidden());
      mvc.perform(delete(base+"/payments/1")).andExpect(status().isForbidden());
      mvc.perform(post(base+"/preview").contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
  }
  @Test void rejectsMalformedAndOutOfRangeInputsWithoutServerErrors() throws Exception {
    var owner=token("invalid-planning@test.local");
    for(var body:new String[]{"{}", profile().replace("1000","-1"),profile().replace("1000","1e30"),profile().replace(today().plusDays(10).toString(),"invalid")}) {
      mvc.perform(put("/api/planning/state").header("Authorization",owner).contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
    mvc.perform(post("/api/planning/payments").header("Authorization",owner).contentType("application/json")
      .content(json.writeValueAsString(Map.of("name","Pago","dueDate",today().toString(),"amount",0)))).andExpect(status().isBadRequest());
  }
  @Test void warnsForOverduePaymentsAndExpiredIncome() throws Exception {
    var owner=token("alerts-planning@test.local");
    mvc.perform(put("/api/planning/state").header("Authorization",owner).contentType("application/json").content(profile())).andExpect(status().isOk());
    mvc.perform(post("/api/planning/payments").header("Authorization",owner).contentType("application/json")
      .content(json.writeValueAsString(Map.of("name","Vencido","dueDate",today().minusDays(1).toString(),"amount",20))))
      .andExpect(status().isOk()).andExpect(jsonPath("$.alerts[0]").value(org.hamcrest.Matchers.containsString("vencido")));
    var uid=jwt.userId(owner.substring(7));
    var p=profiles.findByUserId(uid).orElseThrow();p.nextIncomeDate=today();profiles.saveAndFlush(p);
    mvc.perform(get("/api/planning/state").header("Authorization",owner)).andExpect(status().isOk())
      .andExpect(jsonPath("$.projection").isEmpty()).andExpect(jsonPath("$.alerts[1]").value(org.hamcrest.Matchers.containsString("Actualiza")));
  }
}

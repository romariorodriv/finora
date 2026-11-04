package com.finora.app.config;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
@Component @Order(-100)
public class SchemaPreflight implements ApplicationRunner {
 private final JdbcTemplate database;
 public SchemaPreflight(JdbcTemplate database){this.database=database;}
 public void run(ApplicationArguments args){
  Integer missing=database.queryForObject("select count(*) from transactions where version is null or classification_source is null or currency is null or date_source is null",Integer.class);
  Integer roles=database.queryForObject("select count(*) from app_users where role is null or role not in ('USER','ADMIN')",Integer.class);
  if(missing==null||roles==null||missing>0||roles>0)throw new IllegalStateException("Complete and verify V2_4 migration before starting Sarela; legacy metadata or roles are incomplete.");
 }
}

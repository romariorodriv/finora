package com.finora.app.importer;
import java.math.BigDecimal; import java.time.LocalDate;
public interface BankEmailParser {
 record Parsed(String description,BigDecimal amount,LocalDate date,String merchant,String category,double confidence){}
 boolean supports(String sender,String content); Parsed parse(String content);
}

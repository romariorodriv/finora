package com.finora.app.importer;
import java.math.BigDecimal; import java.time.LocalDate;
public interface BankEmailParser {
 record Parsed(String description,BigDecimal amount,String currency,LocalDate date,String merchant,String category,double confidence){}
 boolean supports(String sender,String content);
 Parsed parse(String content);
 default Parsed parse(String sender,String content){return parse(content);}
}

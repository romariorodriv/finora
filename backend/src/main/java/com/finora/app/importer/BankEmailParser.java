package com.finora.app.importer;
import java.math.BigDecimal; import java.time.LocalDate;
public interface BankEmailParser {
 enum OperationStatus { COMPLETED, REJECTED, REVERSED, REFUNDED }
 record Parsed(String description,BigDecimal amount,String currency,LocalDate date,String merchant,String category,double confidence,OperationStatus status){}
 boolean supports(String sender,String content);
 Parsed parse(String content);
 default Parsed parse(String sender,String content){return parse(content);}
}

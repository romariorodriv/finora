package com.finora.app.importer;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.Optional;
public interface BankEmailParser {
 enum MovementType { EXPENSE, INCOME, TRANSFER, SAVINGS_DEPOSIT, SAVINGS_WITHDRAWAL, CASH_WITHDRAWAL, REFUND }
 enum OperationStatus { COMPLETED, REJECTED, REVERSED, REFUNDED }
 record Parsed(String description,BigDecimal amount,String currency,LocalDate date,String merchant,String category,double confidence,MovementType type,OperationStatus status){}
 boolean supports(String sender,String content);
 Parsed parse(String content);
 default Parsed parse(String sender,String content){return parse(content);}
 default Optional<Parsed> tryParse(String sender,String content){
  if(!supports(sender,content))return Optional.empty();
  try{return Optional.of(parse(sender,content));}
  catch(IllegalArgumentException e){return Optional.empty();}
 }
}

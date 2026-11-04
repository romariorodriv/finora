package com.finora.app.importer;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class PeruBankParserOnboardingTest {
 final PeruBankParser parser=new PeruBankParser();
 final String sender="notificaciones@notificacionesbcp.com.pe";
 @Test void thousandsAreNotTruncatedToSmallExpenses(){
  var result=parser.parse(sender,"Realizaste un consumo de S/ 1,234.56 con tu Tarjeta de Débito BCP en OXXO.");
  assertEquals(new BigDecimal("1234.56"),result.amount());assertEquals("PEN",result.currency());
 }
 @Test void explicitPurchaseAmountWinsOverUnrelatedBalance(){
  var result=parser.parse(sender,"Saldo: S/ 5,000.00. Realizaste un consumo de S/ 32.90 con tu Tarjeta de Débito BCP en OXXO.");
  assertEquals(new BigDecimal("32.90"),result.amount());
 }
 @Test void ambiguousAmountsAndMixedCurrenciesRequireReview(){
  assertTrue(parser.tryParse(sender,"Constancia de transferencia entre mis cuentas S/ 100.00 y S/ 300.00").isEmpty());
  assertTrue(parser.tryParse(sender,"Realizaste un consumo de S/ 100.00 con tu Tarjeta de Débito BCP en OXXO. USD 30.00").isEmpty());
 }
 @Test void explicitPayrollIsIncomeAndNotPurchase(){
  var result=parser.parse(sender,"Abono de sueldo. Monto: S/ 2500.00");
  assertEquals(BankEmailParser.MovementType.INCOME,result.type());assertEquals(new BigDecimal("2500.00"),result.amount());
 }
}

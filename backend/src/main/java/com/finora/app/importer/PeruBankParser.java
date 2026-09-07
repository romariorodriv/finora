package com.finora.app.importer;
import org.springframework.stereotype.Component; import java.math.BigDecimal; import java.time.LocalDate; import java.util.regex.*;
@Component
public class PeruBankParser implements BankEmailParser {
 private static final Pattern AMOUNT=Pattern.compile("(?:S/|PEN)\\s*([0-9]+(?:[.,][0-9]{1,2})?)",Pattern.CASE_INSENSITIVE);
 private static final Pattern MERCHANT=Pattern.compile("(?:en|establecimiento|comercio)\\s+([A-Za-z0-9 .&*_-]{2,40})",Pattern.CASE_INSENSITIVE);
 public boolean supports(String s,String c){String x=(s+" "+c).toLowerCase();return x.contains("bcp")||x.contains("interbank")||x.contains("bbva")||x.contains("scotiabank")||x.contains("consumo");}
 public Parsed parse(String c){Matcher a=AMOUNT.matcher(c);if(!a.find())throw new IllegalArgumentException("No encontramos un monto");BigDecimal amount=new BigDecimal(a.group(1).replace(',','.'));Matcher m=MERCHANT.matcher(c);String merchant=m.find()?m.group(1).trim():"Comercio detectado";return new Parsed("Consumo en "+merchant,amount,LocalDate.now(),merchant,categorize(merchant),.91);}
 private String categorize(String m){String x=m.toLowerCase();if(x.matches(".*(rappi|starbucks|restaurant|tambo).*"))return"Comida";if(x.matches(".*(uber|cabify|grif).*"))return"Transporte";if(x.matches(".*(netflix|spotify|openai|canva).*"))return"Suscripciones";if(x.matches(".*(inkafarma|mifarma|clinica).*"))return"Salud";return"Otros";}
}

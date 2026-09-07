package com.finora.app.assistant;
import com.finora.app.transaction.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal; import java.time.*; import java.util.*; import java.util.stream.*;
@RestController @RequestMapping("/api/assistant")
public class AssistantController {
 private final TransactionRepository repo; public AssistantController(TransactionRepository r){repo=r;}
 public record Question(String message){} public record Answer(String answer,List<String> suggestions,String mode){}
 @PostMapping public Answer ask(Authentication a,@RequestBody Question q){YearMonth ym=YearMonth.now();var tx=repo.findByUserIdAndDateBetweenOrderByDateDesc((Long)a.getPrincipal(),ym.atDay(1),ym.atEndOfMonth());Map<String,BigDecimal> c=tx.stream().filter(t->"EXPENSE".equals(t.type)).collect(Collectors.groupingBy(t->t.category,Collectors.reducing(BigDecimal.ZERO,t->t.amount,BigDecimal::add)));var top=c.entrySet().stream().max(Map.Entry.comparingByValue());BigDecimal total=c.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);String ans=top.map(e->"Este mes gastaste S/ "+total+". Tu categoría más alta es "+e.getKey()+" con S/ "+e.getValue()+". Revisa los últimos movimientos de esa categoría antes de fijar un límite.").orElse("Todavía no tengo movimientos suficientes. Agrega uno o importa una notificación bancaria.");return new Answer(ans,List.of("¿En qué gasté más?","¿Cuánto llevo este mes?","¿Qué pagos se repiten?"),"LOCAL_ANALYTICS");}
}

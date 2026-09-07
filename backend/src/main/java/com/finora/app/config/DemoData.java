package com.finora.app.config;
import com.finora.app.auth.*; import com.finora.app.transaction.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.boot.CommandLineRunner; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.stereotype.Component;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.List;
@Component
public class DemoData implements CommandLineRunner {
 private final UserRepository users;private final TransactionRepository tx;private final BCryptPasswordEncoder enc;private final boolean enabled;
 public DemoData(UserRepository u,TransactionRepository t,BCryptPasswordEncoder e,@Value("${app.demo-data}")boolean enabled){users=u;tx=t;enc=e;this.enabled=enabled;}
 public void run(String...x){if(!enabled||users.findByEmailIgnoreCase("demo@finora.pe").isPresent())return;User u=users.save(new User("Romario","demo@finora.pe",enc.encode("Demo1234")));add(u,"Sueldo",4700,"Ingresos","INCOME",1,false);add(u,"Starbucks",24.5,"Comida","EXPENSE",2,false);add(u,"Uber",18.9,"Transporte","EXPENSE",3,false);add(u,"OpenAI",100,"Suscripciones","EXPENSE",5,true);add(u,"Movistar Internet",89.9,"Servicios","EXPENSE",6,true);add(u,"Rappi",47.5,"Comida","EXPENSE",8,false);add(u,"Plaza Vea",183.4,"Compras","EXPENSE",10,false);add(u,"Netflix",44.9,"Suscripciones","EXPENSE",12,true);}
 private void add(User u,String d,double a,String c,String type,int day,boolean rec){Transaction t=new Transaction();t.userId=u.id;t.description=d;t.merchant=d;t.amount=BigDecimal.valueOf(a);t.category=c;t.type=type;t.date=LocalDate.now().withDayOfMonth(Math.min(day,LocalDate.now().lengthOfMonth()));t.source="DEMO";t.recurring=rec;tx.save(t);}
}

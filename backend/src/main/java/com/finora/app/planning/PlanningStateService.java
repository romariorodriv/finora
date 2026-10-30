package com.finora.app.planning;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.finora.app.shared.error.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PlanningStateService {
  public record ProfileInput(BigDecimal confirmedBalance,BigDecimal reserve,LocalDate nextIncomeDate){}
  public record PaymentInput(String name,LocalDate dueDate,BigDecimal amount){}
  public record PaymentOutput(Long id,String name,LocalDate dueDate,BigDecimal amount){}
  public record State(ProfileInput profile,List<PaymentOutput> payments,PlanningService.PreviewResponse projection,List<String> alerts){}
  private final PlanningProfileRepository profiles;
  private final PlanningPaymentRepository payments;
  private final PlanningService calculator;
  public PlanningStateService(PlanningProfileRepository profiles,PlanningPaymentRepository payments,PlanningService calculator){this.profiles=profiles;this.payments=payments;this.calculator=calculator;}
  private BigDecimal amount(BigDecimal n, boolean zeroAllowed){
    if(n==null || n.signum()<0 || (!zeroAllowed && n.signum()==0) || n.abs().compareTo(new BigDecimal("999999999999.99"))>0 || n.scale()>2)
      throw new ApiException(400,"PLANNING_VALIDATION","Monto inválido (máximo dos decimales).");
    return n;
  }
  @Transactional(readOnly=true)
  public State state(Long uid){
    var profile=profiles.findByUserId(uid).orElse(null);
    var saved=payments.findByUserIdOrderByDueDateAscIdAsc(uid);
    var list=saved.stream().map(x->new PaymentOutput(x.id,x.name,x.dueDate,x.amount)).toList();
    if(profile==null)return new State(null,list,null,List.of("Configura tu saldo y próximo ingreso para comenzar."));
    var data=new ProfileInput(profile.confirmedBalance,profile.reserve,profile.nextIncomeDate);
    var today=LocalDate.now(java.time.ZoneId.of("America/Lima"));
    var warnings=new java.util.ArrayList<String>();
    for(var x:saved)if(x.dueDate.isBefore(today))warnings.add("Pago vencido sin confirmar: "+x.name+". Actualiza o elimina el pago si ya fue realizado.");
    PlanningService.PreviewResponse result=null;
    if(profile.nextIncomeDate.isAfter(today) && !profile.nextIncomeDate.isAfter(today.plusDays(366))){
      result=calculator.preview(new PlanningService.PreviewRequest(today,profile.nextIncomeDate,profile.confirmedBalance,
        saved.stream().map(x->new PlanningService.Obligation(x.name,x.dueDate,x.amount)).toList(),profile.reserve));
      if(result.deficit())warnings.add("Tus pagos pendientes y reserva superan tu saldo confirmado.");
      for(var x:saved)if(!x.dueDate.isBefore(today) && !x.dueDate.isAfter(today.plusDays(3)))warnings.add("Pago próximo: "+x.name+" vence el "+x.dueDate+".");
    }else warnings.add("Actualiza tu fecha de próximo ingreso para recalcular.");
    return new State(data,list,result,warnings);
  }
  @Transactional
  public State saveProfile(Long uid,ProfileInput input){
    if(input==null || input.nextIncomeDate()==null || !input.nextIncomeDate().isAfter(LocalDate.now(java.time.ZoneId.of("America/Lima"))) || input.nextIncomeDate().isAfter(LocalDate.now(java.time.ZoneId.of("America/Lima")).plusDays(366)))
      throw new ApiException(400,"PLANNING_VALIDATION","El próximo ingreso debe estar entre 1 y 366 días en el futuro.");
    var balance=amount(input.confirmedBalance(),true);var reserve=amount(input.reserve(),true);
    var p=profiles.findByUserId(uid).orElseGet(()->new PlanningProfile(uid,balance,reserve,input.nextIncomeDate()));
    p.confirmedBalance=balance;p.reserve=reserve;p.nextIncomeDate=input.nextIncomeDate();profiles.save(p);
    return state(uid);
  }
  @Transactional
  public State addPayment(Long uid,PaymentInput input){
    if(input==null || input.name()==null || input.name().trim().isBlank() || input.name().trim().length()>100 || input.dueDate()==null || input.dueDate().isBefore(LocalDate.now(java.time.ZoneId.of("America/Lima")).minusDays(366)) || input.dueDate().isAfter(LocalDate.now(java.time.ZoneId.of("America/Lima")).plusYears(5)))
      throw new ApiException(400,"PLANNING_VALIDATION","Confirma el concepto y la fecha del pago.");
    var n=amount(input.amount(),false);
    if(payments.countByUserId(uid)>=200)throw new ApiException(400,"PLANNING_VALIDATION","Límite de 200 pagos alcanzado.");
    payments.save(new PlanningPayment(uid,input.name().trim(),input.dueDate(),n));
    return state(uid);
  }
  @Transactional
  public State deletePayment(Long uid,Long id){
    var payment=payments.findById(id).orElseThrow(()->new ApiException(404,"PAYMENT_NOT_FOUND","Pago no encontrado."));
    if(!payment.userId.equals(uid))throw new ApiException(404,"PAYMENT_NOT_FOUND","Pago no encontrado.");
    payments.delete(payment);return state(uid);
  }
}

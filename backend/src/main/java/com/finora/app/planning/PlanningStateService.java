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
  public record PaymentOutput(Long id,String name,LocalDate dueDate,BigDecimal amount,java.time.Instant paidAt){}
  public record CompletePaymentInput(BigDecimal resultingBalance){}
  public record State(ProfileInput profile,List<PaymentOutput> payments,PlanningService.PreviewResponse projection,List<String> alerts,java.time.Instant balanceConfirmedAt){}
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
    var pending=saved.stream().filter(x->x.paidAt==null).toList();
    var list=saved.stream().map(x->new PaymentOutput(x.id,x.name,x.dueDate,x.amount,x.paidAt)).toList();
    if(profile==null)return new State(null,list,null,List.of("Configura tu saldo y próximo ingreso para comenzar."),null);
    var data=new ProfileInput(profile.confirmedBalance,profile.reserve,profile.nextIncomeDate);
    var today=LocalDate.now(java.time.ZoneId.of("America/Lima"));
    var warnings=new java.util.ArrayList<String>();
    for(var x:pending)if(x.dueDate.isBefore(today))warnings.add("Pago vencido sin confirmar: "+x.name+". Marca el pago como realizado y confirma el saldo resultante si ya lo pagaste.");
    PlanningService.PreviewResponse result=null;
    if(profile.balanceConfirmedAt==null){warnings.add("Confirma tu saldo actual para calcular la disponibilidad.");}
    else if(profile.nextIncomeDate.isAfter(today) && !profile.nextIncomeDate.isAfter(today.plusDays(366))){
      result=calculator.preview(new PlanningService.PreviewRequest(today,profile.nextIncomeDate,profile.confirmedBalance,
        pending.stream().map(x->new PlanningService.Obligation(x.name,x.dueDate,x.amount)).toList(),profile.reserve));
      if(result.deficit())warnings.add("Tus pagos pendientes y reserva superan tu saldo confirmado.");
      for(var x:pending)if(!x.dueDate.isBefore(today) && !x.dueDate.isAfter(today.plusDays(3)))warnings.add("Pago próximo: "+x.name+" vence el "+x.dueDate+".");
    }else warnings.add("Actualiza tu fecha de próximo ingreso para recalcular.");
    if(profile.balanceConfirmedAt==null)warnings.add("La fecha de confirmación del saldo es desconocida. Confirma tu saldo actual.");
    if(profile.balanceConfirmedAt!=null && profile.balanceConfirmedAt.isBefore(java.time.Instant.now().minus(java.time.Duration.ofHours(24))))warnings.add("Saldo desactualizado: han pasado mas de 24 horas. Confirma tu saldo actual.");
    return new State(data,list,result,warnings,profile.balanceConfirmedAt);
  }
  @Transactional
  public State saveProfile(Long uid,ProfileInput input){
    if(input==null || input.nextIncomeDate()==null || !input.nextIncomeDate().isAfter(LocalDate.now(java.time.ZoneId.of("America/Lima"))) || input.nextIncomeDate().isAfter(LocalDate.now(java.time.ZoneId.of("America/Lima")).plusDays(366)))
      throw new ApiException(400,"PLANNING_VALIDATION","El próximo ingreso debe estar entre 1 y 366 días en el futuro.");
    var balance=amount(input.confirmedBalance(),true);var reserve=amount(input.reserve(),true);
    var p=profiles.lockedByUserId(uid).orElseGet(()->new PlanningProfile(uid,balance,reserve,input.nextIncomeDate()));
    p.confirmedBalance=balance;p.reserve=reserve;p.nextIncomeDate=input.nextIncomeDate();p.balanceConfirmedAt=java.time.Instant.now();profiles.save(p);
    return state(uid);
  }
  @Transactional
  public State addPayment(Long uid,PaymentInput input){
    if(input==null || input.name()==null || input.name().trim().isBlank() || input.name().trim().length()>100 || input.dueDate()==null || input.dueDate().isBefore(LocalDate.now(java.time.ZoneId.of("America/Lima")).minusDays(366)) || input.dueDate().isAfter(LocalDate.now(java.time.ZoneId.of("America/Lima")).plusYears(5)))
      throw new ApiException(400,"PLANNING_VALIDATION","Confirma el concepto y la fecha del pago.");
    var n=amount(input.amount(),false);
    if(payments.countByUserIdAndPaidAtIsNull(uid)>=200)throw new ApiException(400,"PLANNING_VALIDATION","Límite de 200 pagos alcanzado.");
    payments.save(new PlanningPayment(uid,input.name().trim(),input.dueDate(),n));
    return state(uid);
  }
  @Transactional
  public State completePayment(Long uid,Long id,CompletePaymentInput input){
    // Serialize confirmations for the account and commit payment + actual balance together.
    var profile=profiles.lockedByUserId(uid).orElseThrow(()->new ApiException(400,"PLANNING_VALIDATION","Primero confirma tu saldo y configura tu plan."));
    var payment=payments.findById(id).orElseThrow(()->new ApiException(404,"PAYMENT_NOT_FOUND","Pago no encontrado."));
    if(!payment.userId.equals(uid))throw new ApiException(404,"PAYMENT_NOT_FOUND","Pago no encontrado.");
    if(payment.paidAt!=null)throw new ApiException(409,"PAYMENT_ALREADY_PAID","El pago ya fue marcado como realizado. Recarga tu plan.");
    var balance=amount(input==null?null:input.resultingBalance(),true);
    var now=java.time.Instant.now();
    payment.paidAt=now;
    profile.confirmedBalance=balance;profile.balanceConfirmedAt=now;
    payments.save(payment);profiles.save(profile);
    return state(uid);
  }
  @Transactional
  public State deletePayment(Long uid,Long id){
    var payment=payments.findById(id).orElseThrow(()->new ApiException(404,"PAYMENT_NOT_FOUND","Pago no encontrado."));
    if(!payment.userId.equals(uid))throw new ApiException(404,"PAYMENT_NOT_FOUND","Pago no encontrado.");
    payments.delete(payment);return state(uid);
  }
}

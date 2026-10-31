package com.finora.app.planning;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="planning_payments", indexes=@Index(name="idx_planning_payments_user",columnList="user_id"))
public class PlanningPayment {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(name="user_id",nullable=false) public Long userId;
  @Column(nullable=false,length=100) public String name;
  @Column(name="due_date",nullable=false) public LocalDate dueDate;
  @Column(nullable=false,precision=16,scale=2) public BigDecimal amount;
  @ManyToOne(fetch=FetchType.LAZY, optional=false)
  @JoinColumn(name="user_id", insertable=false, updatable=false, foreignKey=@ForeignKey(name="fk_planningpayment_user"))
  private com.finora.app.auth.User user;
  protected PlanningPayment() {}
  public PlanningPayment(Long userId,String name,LocalDate dueDate,BigDecimal amount){this.userId=userId;this.name=name;this.dueDate=dueDate;this.amount=amount;}
}

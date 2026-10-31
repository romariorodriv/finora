package com.finora.app.planning;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="planning_profiles", uniqueConstraints=@UniqueConstraint(columnNames="user_id"))
public class PlanningProfile {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(name="user_id", nullable=false, unique=true) public Long userId;
  @Column(name="confirmed_balance",nullable=false,precision=16,scale=2) public BigDecimal confirmedBalance;
  @Column(name="reserve_amount",nullable=false,precision=16,scale=2) public BigDecimal reserve;
  @Column(name="next_income_date",nullable=false) public LocalDate nextIncomeDate;
  @ManyToOne(fetch=FetchType.LAZY, optional=false)
  @JoinColumn(name="user_id", insertable=false, updatable=false, foreignKey=@ForeignKey(name="fk_planningprofile_user"))
  private com.finora.app.auth.User user;
  protected PlanningProfile() {}
  public PlanningProfile(Long userId, BigDecimal confirmedBalance, BigDecimal reserve, LocalDate nextIncomeDate) {
    this.userId=userId;this.confirmedBalance=confirmedBalance;this.reserve=reserve;this.nextIncomeDate=nextIncomeDate;
  }
}

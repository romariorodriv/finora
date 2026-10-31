package com.finora.app.admin;
import jakarta.persistence.*;
@Entity @Table(name="managed_categories",uniqueConstraints=@UniqueConstraint(columnNames="name_key"))
public class ManagedCategory {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false,length=100) public String name;
 @Column(name="name_key",nullable=false,length=120) public String nameKey;
 @Column(length=500) public String description="";
 @Column(nullable=false,length=30) public String icon="grid";
 @Column(nullable=false,length=7) public String color="#187650";
 @Column(name="macro_group",nullable=false,length=30) public String macroGroup="OTROS";
 @Column(nullable=false) public boolean active=true;
 @Version public Long version;
}

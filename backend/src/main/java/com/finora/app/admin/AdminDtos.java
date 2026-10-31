package com.finora.app.admin;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
public final class AdminDtos {
 private AdminDtos(){}
 public record CategoryInput(String name,String description,String icon,String color,String macroGroup,boolean active,Long version){}
 public record CategoryView(Long id,String name,String description,String icon,String color,String macroGroup,boolean active,Long version,long transactionCount){}
 public record RuleInput(String merchant,String matchType,String scope,Long userId,Long categoryId,int priority,boolean active,Long version){}
 public record RuleView(Long id,String merchant,String matchType,String scope,Long userId,Long categoryId,int priority,boolean active,Long version){}
 public record Filters(String q,LocalDate from,LocalDate to,Long categoryId,String reviewStatus,Long userId,boolean uncategorized){public static Filters empty(){return new Filters(null,null,null,null,null,null,false);}}
 public record TxView(Long id,Long accountId,LocalDate date,String merchant,String normalizedMerchant,String description,BigDecimal amount,String currency,Long categoryId,String category,String classificationSource,String reviewStatus,String source){}
 public record CurrencyTotal(String currency,BigDecimal total,long count){}
 public record PageResult<T>(List<T> items,long totalElements,int totalPages,int page){}
 public record TransactionsPage(PageResult<TxView> page,List<CurrencyTotal> totals){}
 public record Correction(List<Long> transactionIds,Long categoryId,boolean confirmed){}
 public record Reassign(Long targetCategoryId,Filters filters){}
 public record Confirmation(boolean confirmed){}
 public record BatchView(String id,String kind,String status,long count,int skippedManual,Long targetCategoryId,Long ruleId,Instant expiresAt,String scope,List<TxView> examples,List<CurrencyTotal> totals){}
 public record Snapshot(Long categoryId,String category,String macroCategory,String classificationSource,Long classificationRuleId,String reviewStatus,Long version){}
}

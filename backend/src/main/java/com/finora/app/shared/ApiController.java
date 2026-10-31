package com.finora.app.shared;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class ApiController {
  private final com.finora.app.admin.ManagedCategoryRepository catalog;
  public ApiController(com.finora.app.admin.ManagedCategoryRepository catalog){this.catalog=catalog;}
  @GetMapping({"/api/category-catalog","/api/v1/category-catalog"})
  public java.util.List<com.finora.app.admin.ManagedCategory> catalog(){return catalog.findByActiveTrueOrderByNameAsc();}

  @GetMapping({"/api/health", "/api/v1/health"})
  public Map<String, Object> health() {
    return Map.of("status", "UP", "app", "Sarela");
  }

  @GetMapping({"/api/categories", "/api/v1/categories"})
  public List<String> categories() {
    return catalog.findByActiveTrueOrderByNameAsc().stream().map(c->c.name).toList();
  }
}

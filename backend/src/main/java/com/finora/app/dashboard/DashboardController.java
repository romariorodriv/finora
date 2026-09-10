package com.finora.app.dashboard;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/dashboard", "/api/v1/dashboard"})
public class DashboardController {
  private final DashboardService service;

  public DashboardController(DashboardService service) {
    this.service = service;
  }

  @GetMapping
  public DashboardResponse dashboard(Authentication a, @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month) {
    return service.dashboard((Long) a.getPrincipal(), year, month);
  }
}

package com.finora.app.planning;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping({"/api/planning", "/api/v1/planning"})
public class PlanningController {
  private final PlanningService planning;
  private final PlanningStateService state;
  public PlanningController(PlanningService planning, PlanningStateService state) { this.planning = planning; this.state = state; }

  @GetMapping("/state") public PlanningStateService.State getState(Authentication auth){return state.state((Long)auth.getPrincipal());}
  @PutMapping("/state") public PlanningStateService.State saveState(Authentication auth,@RequestBody PlanningStateService.ProfileInput input){return state.saveProfile((Long)auth.getPrincipal(),input);}
  @PostMapping("/payments") public PlanningStateService.State addPayment(Authentication auth,@RequestBody PlanningStateService.PaymentInput input){return state.addPayment((Long)auth.getPrincipal(),input);}
  @DeleteMapping("/payments/{id}") public PlanningStateService.State deletePayment(Authentication auth,@PathVariable Long id){return state.deletePayment((Long)auth.getPrincipal(),id);}


  @PostMapping("/preview")
  public PlanningService.PreviewResponse preview(@RequestBody PlanningService.PreviewRequest request) {
    return planning.preview(request);
  }

}

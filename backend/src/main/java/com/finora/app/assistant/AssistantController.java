package com.finora.app.assistant;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/assistant", "/api/v1/assistant"})
public class AssistantController {
  private final AssistantService service;

  public AssistantController(AssistantService service) {
    this.service = service;
  }

  @PostMapping
  public AssistantDtos.Answer ask(Authentication a, @RequestBody AssistantDtos.Question q) {
    return service.ask((Long) a.getPrincipal(), q);
  }
}

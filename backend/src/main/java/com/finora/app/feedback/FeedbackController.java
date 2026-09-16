package com.finora.app.feedback;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/feedback", "/api/v1/feedback"})
public class FeedbackController {
  private final FeedbackService service;

  public FeedbackController(FeedbackService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<FeedbackDtos.FeedbackResponse> create(Authentication authentication,
      @Valid @RequestBody FeedbackDtos.FeedbackRequest request) {
    return ResponseEntity.status(201).body(service.create((Long) authentication.getPrincipal(), request));
  }
}

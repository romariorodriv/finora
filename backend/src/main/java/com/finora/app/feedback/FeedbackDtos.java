package com.finora.app.feedback;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class FeedbackDtos {
  private FeedbackDtos() {}

  public record FeedbackRequest(
      @Min(1) @Max(5) int rating,
      @NotNull UserFeedback.UnderstoodSpending understoodSpending,
      @Size(max = 1000) String liked,
      @Size(max = 1000) String improvement,
      @Size(max = 1000) String wantedFeature
  ) {}

  public record FeedbackResponse(
      Long id,
      int rating,
      UserFeedback.UnderstoodSpending understoodSpending,
      String liked,
      String improvement,
      String wantedFeature,
      Instant createdAt
  ) {
    static FeedbackResponse from(UserFeedback feedback) {
      return new FeedbackResponse(feedback.id, feedback.rating, feedback.understoodSpending, feedback.liked,
          feedback.improvement, feedback.wantedFeature, feedback.createdAt);
    }
  }
}

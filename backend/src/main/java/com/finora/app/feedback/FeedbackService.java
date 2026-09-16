package com.finora.app.feedback;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {
  private final UserFeedbackRepository repository;

  public FeedbackService(UserFeedbackRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public FeedbackDtos.FeedbackResponse create(Long userId, FeedbackDtos.FeedbackRequest request) {
    UserFeedback feedback = new UserFeedback();
    feedback.userId = userId;
    feedback.rating = request.rating();
    feedback.understoodSpending = request.understoodSpending();
    feedback.liked = blankToNull(request.liked());
    feedback.improvement = blankToNull(request.improvement());
    feedback.wantedFeature = blankToNull(request.wantedFeature());
    return FeedbackDtos.FeedbackResponse.from(repository.save(feedback));
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}

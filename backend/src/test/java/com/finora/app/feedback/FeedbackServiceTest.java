package com.finora.app.feedback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FeedbackServiceTest {
  private final UserFeedbackRepository repository = mock(UserFeedbackRepository.class);
  private final FeedbackService service = new FeedbackService(repository);
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void authenticatedUserCanSubmitFeedbackAndCreatedAtIsGenerated() {
    when(repository.save(any())).thenAnswer(invocation -> {
      UserFeedback feedback = invocation.getArgument(0);
      feedback.id = 9L;
      return feedback;
    });

    FeedbackDtos.FeedbackResponse response = service.create(7L,
        new FeedbackDtos.FeedbackRequest(4, UserFeedback.UnderstoodSpending.YES, "El resumen", "Nada", "Alertas"));

    ArgumentCaptor<UserFeedback> saved = ArgumentCaptor.forClass(UserFeedback.class);
    verify(repository).save(saved.capture());
    assertEquals(7L, saved.getValue().userId);
    assertEquals(9L, response.id());
    assertNotNull(response.createdAt());
  }

  @Test
  void ratingLowerThanOneFailsValidation() {
    FeedbackDtos.FeedbackRequest request = new FeedbackDtos.FeedbackRequest(0, UserFeedback.UnderstoodSpending.YES,
        null, null, null);
    assertFalse(validator.validate(request).isEmpty());
  }

  @Test
  void ratingGreaterThanFiveFailsValidation() {
    FeedbackDtos.FeedbackRequest request = new FeedbackDtos.FeedbackRequest(6, UserFeedback.UnderstoodSpending.YES,
        null, null, null);
    assertFalse(validator.validate(request).isEmpty());
  }

  @Test
  void understoodSpendingIsRequired() {
    FeedbackDtos.FeedbackRequest request = new FeedbackDtos.FeedbackRequest(3, null, null, null, null);
    assertFalse(validator.validate(request).isEmpty());
  }

  @Test
  void requestDoesNotAcceptArbitraryUserId() throws NoSuchFieldException {
    assertThrows(NoSuchFieldException.class, () -> FeedbackDtos.FeedbackRequest.class.getDeclaredField("userId"));
  }
}

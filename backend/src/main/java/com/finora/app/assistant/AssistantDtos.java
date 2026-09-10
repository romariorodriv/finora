package com.finora.app.assistant;

import java.util.List;

public final class AssistantDtos {
  private AssistantDtos() {}

  public record Question(String message) {}

  public record Answer(String answer, List<String> suggestions, String mode) {}
}

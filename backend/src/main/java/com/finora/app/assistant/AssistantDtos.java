package com.finora.app.assistant;

import java.util.List;

public final class AssistantDtos {
  private AssistantDtos() {}

  public record Question(String message, String currency) {
    public Question(String message) { this(message,null); }
  }

  public record Answer(String answer, List<String> suggestions, String mode) {}
}

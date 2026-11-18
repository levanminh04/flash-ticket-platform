package com.flashticket.assistant_service.agent;

import dev.langchain4j.service.SystemMessage;

public interface BuyerAssistantAgent {
  @SystemMessage(fromResource = "/prompts/buyer-assistant.txt")
  public abstract String chat(String message);
}

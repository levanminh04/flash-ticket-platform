package com.flashticket.assistant_service.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AdminAssistantAgent {
  @SystemMessage(fromResource = "/prompts/admin-assistant.txt")
  String chat(@UserMessage String message, @V("today") String today);
}

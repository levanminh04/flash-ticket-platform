package com.flashticket.assistant_service.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.assistant_service.client.BuyerApiClient;
import com.flashticket.assistant_service.config.AssistantConfiguration;
import com.flashticket.assistant_service.dto.OrderReadPage;
import com.flashticket.assistant_service.service.RedisConversationStore;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import jakarta.validation.Validation;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BuyerAssistantAgentContractTests {
  @Test
  void runsToolsWithPerTurnMemoryAndBoundedLoop() {
    var model = mock(ChatModel.class); var api = mock(BuyerApiClient.class); var store = mock(RedisConversationStore.class);
    var now = Instant.parse("2026-11-18T00:00:00Z");
    var jwt = Jwt.withTokenValue("fixture-private-token").header("alg", "RS256").subject("buyer").expiresAt(now.plusSeconds(300)).build();
    when(store.isLeaseCurrent(anyString(), any(), any())).thenReturn(true);
    var page = new OrderReadPage(); page.setItems(List.of()); when(api.getMyOrders(jwt, 0, "")).thenReturn(page);
    var call = ToolExecutionRequest.builder().id("call-1").name("getMyOrders").arguments("{\"page\":0,\"state\":\"\"}").build();
    var toolResponse = ChatResponse.builder().aiMessage(AiMessage.from(call)).build();
    var finalResponse = ChatResponse.builder().aiMessage(AiMessage.from("Không có đơn.")).build();
    when(model.chat(any(ChatRequest.class))).thenReturn(toolResponse, finalResponse);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var tools = new BuyerAssistantTools(api, store, factory.getValidator(), Clock.fixed(now, ZoneOffset.UTC), jwt, "owner", UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(45));
      var memory = MessageWindowChatMemory.withMaxMessages(30);
      var agent = AiServices.builder(BuyerAssistantAgent.class).chatModel(model).chatMemory(memory).tools(tools).maxToolCallingRoundTrips(3).toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation()).build();
      assertEquals("Không có đơn.", agent.chat("Xem đơn của tôi")); verify(api).getMyOrders(jwt, 0, "");
      var captured = org.mockito.ArgumentCaptor.forClass(ChatRequest.class); verify(model, times(2)).chat(captured.capture());
      assertEquals(6, captured.getAllValues().getFirst().parameters().toolSpecifications().size());
      assertFalse(captured.getAllValues().toString().contains("fixture-private-token"));
      var fresh = MessageWindowChatMemory.withMaxMessages(30); assertTrue(fresh.messages().isEmpty());
      clearInvocations(model, api); when(model.chat(any(ChatRequest.class))).thenReturn(toolResponse);
      assertThrows(RuntimeException.class, () -> agent.chat("Tra cứu tiếp")); verify(model, atMost(4)).chat(any(ChatRequest.class));
    }
  }

  @Test
  void routesProxyToolCallsThroughChatCompletions() throws IOException {
    var mapper = JsonMapper.builder().build();
    var requests = new CopyOnWriteArrayList<JsonNode>();
    var credentials = new CopyOnWriteArrayList<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      try {
        if (!"POST".equals(exchange.getRequestMethod()) || !"/v1/chat/completions".equals(exchange.getRequestURI().getPath())) {
          exchange.sendResponseHeaders(404, -1);
          return;
        }
        credentials.add(exchange.getRequestHeaders().getFirst("Authorization"));
        requests.add(mapper.readTree(exchange.getRequestBody()));
        String choice = requests.size() == 1 ? """
            {"index":0,"finish_reason":"tool_calls","message":{"role":"assistant","content":null,"tool_calls":[
              {"id":"call-proxy-1","type":"function","function":{"name":"getMyOrders","arguments":"{\\"page\\":0,\\"state\\":\\"\\"}"}}
            ]}}
            """ : """
            {"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"No orders."}}
            """;
        byte[] response = ("""
            {"id":"chatcmpl-fixture","object":"chat.completion","created":1794960000,"model":"gemini-3.8-flash-high","choices":[%s]}
            """.formatted(choice)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
      } finally {
        exchange.close();
      }
    });
    server.start();
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var config = new AssistantConfiguration();
      ReflectionTestUtils.setField(config, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
      ReflectionTestUtils.setField(config, "apiKey", "fixture-proxy-key");
      ReflectionTestUtils.setField(config, "model", "gemini-3.8-flash-high");
      var api = mock(BuyerApiClient.class);
      var store = mock(RedisConversationStore.class);
      var now = Instant.parse("2026-11-18T00:00:00Z");
      var jwt = Jwt.withTokenValue("fixture-buyer-jwt").header("alg", "RS256").subject("buyer").expiresAt(now.plusSeconds(300)).build();
      var page = new OrderReadPage();
      page.setItems(List.of());
      when(api.getMyOrders(jwt, 0, "")).thenReturn(page);
      when(store.isLeaseCurrent(anyString(), any(), any())).thenReturn(true);
      var tools = new BuyerAssistantTools(api, store, factory.getValidator(), Clock.fixed(now, ZoneOffset.UTC),
          jwt, "owner", UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(45));
      var agent = AiServices.builder(BuyerAssistantAgent.class).chatModel(config.buyerChatModel())
          .chatMemory(MessageWindowChatMemory.withMaxMessages(30)).tools(tools).maxToolCallingRoundTrips(3)
          .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation()).build();

      assertEquals("No orders.", agent.chat("Show my orders"));
      verify(api).getMyOrders(jwt, 0, "");
      assertEquals(2, requests.size());
      assertEquals(List.of("Bearer fixture-proxy-key", "Bearer fixture-proxy-key"), credentials);
      for (JsonNode request : requests) {
        assertEquals("gemini-3.8-flash-high", request.path("model").asString());
        assertEquals(2048, request.path("max_tokens").asInt());
        assertFalse(request.toString().contains(jwt.getTokenValue()));
      }
      JsonNode toolSchemas = requests.getFirst().path("tools");
      assertEquals(6, toolSchemas.size());
      Set<String> toolNames = new HashSet<>();
      for (JsonNode tool : toolSchemas) toolNames.add(tool.path("function").path("name").asString());
      assertEquals(Set.of("searchEvents", "getEvent", "getAvailability", "getMyOrders", "getMyOrder", "prepareQuantityPurchase"), toolNames);
      JsonNode messages = requests.getLast().path("messages");
      JsonNode assistantCall = messages.path(messages.size() - 2);
      JsonNode toolResult = messages.path(messages.size() - 1);
      assertEquals("assistant", assistantCall.path("role").asString());
      assertEquals("call-proxy-1", assistantCall.at("/tool_calls/0/id").asString());
      assertEquals("tool", toolResult.path("role").asString());
      assertEquals("call-proxy-1", toolResult.path("tool_call_id").asString());
      assertEquals(mapper.readTree("[]"), mapper.readTree(toolResult.path("content").asString()).path("items"));
    } finally {
      server.stop(0);
    }
  }
}

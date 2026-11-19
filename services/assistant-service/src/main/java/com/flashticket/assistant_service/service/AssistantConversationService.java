package com.flashticket.assistant_service.service;

import com.flashticket.assistant_service.agent.BuyerAssistantAgent;
import com.flashticket.assistant_service.agent.BuyerAssistantTools;
import com.flashticket.assistant_service.client.BuyerApiClient;
import com.flashticket.assistant_service.dto.*;
import com.flashticket.assistant_service.exception.AssistantProblem;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class AssistantConversationService {
  private final RedisConversationStore conversationStore;
  private final BuyerApiClient buyerApiClient;
  private final ChatModel chatModel;
  private final ExecutorService assistantExecutor;
  private final Clock clock;
  private final Validator validator;
  private final JsonMapper transcriptMapper = JsonMapper.builder().build();

  public ConversationView openConversation(Jwt jwt) {
    String owner = ownerKey(jwt);
    try {
      return toView(conversationStore.read(owner), null);
    } catch (AssistantProblem error) {
      if (error.getStatus() != HttpStatus.GONE) throw error;
      return toView(conversationStore.open(owner), "NEW_CONVERSATION");
    }
  }

  public ConversationView getConversation(Jwt jwt) {
    return toView(conversationStore.read(ownerKey(jwt)), null);
  }

  public ChatResponse sendMessage(Jwt jwt, ChatRequest request) {
    String owner = ownerKey(jwt);
    if (request == null || !validator.validate(request).isEmpty() || request.getMessage().trim().isEmpty()) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Thông tin gửi lên không hợp lệ.");
    }
    String hash = requestHash(request);
    ChatRequest safeRequest = new ChatRequest();
    safeRequest.setConversationId(request.getConversationId()); safeRequest.setVersion(request.getVersion());
    safeRequest.setRequestId(request.getRequestId()); safeRequest.setMessage(redactSensitiveText(request.getMessage()));
    UUID lease = UUID.randomUUID();
    Instant deadline = clock.instant().plusSeconds(45);
    ConversationState stored = conversationStore.beginTurn(owner, safeRequest, hash, lease);
    if (safeRequest.getRequestId().equals(stored.getLastRequestId())) {
      if (!hash.equals(stored.getLastRequestHash()) || stored.getLastResponse() == null) {
        throw new AssistantProblem(HttpStatus.CONFLICT, "REQUEST_ID_CONFLICT", "Yêu cầu không khớp lượt đã lưu.");
      }
      return stored.getLastResponse();
    }
    Future<TurnResult> future = null;
    try {
      future = assistantExecutor.submit(() -> runTurn(jwt, owner, lease, safeRequest, stored, deadline));
      long remaining = Duration.between(clock.instant(), deadline).toNanos();
      if (remaining <= 0) throw new TimeoutException();
      TurnResult result = future.get(remaining, TimeUnit.NANOSECONDS);
      ownerKey(jwt); // Token có thể hết hạn trong lúc chờ provider.
      if (!clock.instant().isBefore(deadline)) throw new TimeoutException();
      ChatResponse response = new ChatResponse();
      response.setConversationId(stored.getConversationId()); response.setVersion(UUID.randomUUID());
      response.setRequestId(safeRequest.getRequestId()); response.setMessage(result.getAnswer());
      response.setDraft(result.getDraft()); response.setEvents(result.getEvents());
      response.setOrders(result.getOrders());
      ConversationState next = new ConversationState();
      next.setConversationId(stored.getConversationId()); next.setVersion(response.getVersion());
      next.setLastRequestId(safeRequest.getRequestId()); next.setLastRequestHash(hash); next.setLastResponse(response);
      next.setContextJson(result.getContextJson()); next.setMessages(appendTranscript(stored, safeRequest, result, clock.instant()));
      if (!clock.instant().isBefore(deadline)) throw new TimeoutException();
      return conversationStore.commitTurn(owner, stored.getConversationId(), stored.getVersion(), lease, next).getLastResponse();
    } catch (RejectedExecutionException error) {
      throw new AssistantProblem(HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_CAPACITY_EXCEEDED", "Trợ lý đang bận. Vui lòng thử lại sau.");
    } catch (TimeoutException error) {
      throw new AssistantProblem(HttpStatus.GATEWAY_TIMEOUT, "TURN_DEADLINE_EXCEEDED", "Lượt trò chuyện đã hết thời gian. Vui lòng đọc lại trạng thái.");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new AssistantProblem(HttpStatus.SERVICE_UNAVAILABLE, "TURN_INTERRUPTED", "Lượt trò chuyện bị gián đoạn. Vui lòng đọc lại trạng thái.");
    } catch (ExecutionException error) {
      Throwable cause = error.getCause();
      while (cause != null && !(cause instanceof AssistantProblem) && cause.getCause() != cause) cause = cause.getCause();
      if (cause instanceof AssistantProblem problem) throw problem;
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "MODEL_TURN_FAILED", "Trợ lý chưa thể trả lời. Hội thoại chưa được cập nhật.");
    } finally {
      // Cancel chỉ yêu cầu interruption; worker không có quyền commit nên kết quả muộn vẫn bị fence.
      if (future != null && !future.isDone()) future.cancel(true);
      conversationStore.release(owner, lease);
    }
  }

  public ConversationView resetConversation(Jwt jwt, ResetConversationRequest request) {
    if (request == null || !validator.validate(request).isEmpty()) {
      throw new AssistantProblem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Thông tin gửi lên không hợp lệ.");
    }
    return toView(conversationStore.reset(ownerKey(jwt), request), "CONVERSATION_RESET");
  }

  private String ownerKey(Jwt jwt) {
    if (jwt == null || jwt.getIssuer() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()
        || jwt.getExpiresAt() == null || !clock.instant().isBefore(jwt.getExpiresAt())) {
      throw new AssistantProblem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Vui lòng đăng nhập lại.");
    }
    try {
      String issuer = jwt.getIssuer().toString();
      String canonicalOwner = issuer.length() + ":" + issuer + jwt.getSubject().length() + ":" + jwt.getSubject();
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalOwner.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new AssistantProblem(HttpStatus.INTERNAL_SERVER_ERROR, "OWNER_SCOPE_UNAVAILABLE", "Không xác định được phạm vi hội thoại.");
    }
  }

  private ConversationView toView(ConversationState state, String notice) {
    ConversationView view = new ConversationView();
    view.setConversationId(state.getConversationId()); view.setVersion(state.getVersion());
    view.setLastRequestId(state.getLastRequestId()); view.setExpiresAt(Instant.ofEpochMilli(state.getExpiresAtEpochMillis()));
    view.setNotice(notice); view.setMessages(List.copyOf(state.getMessages()));
    return view;
  }

  private TurnResult runTurn(Jwt jwt, String ownerKey, UUID leaseToken, ChatRequest request, ConversationState storedState, Instant deadline) {
    ownerKey(jwt);
    if (Thread.currentThread().isInterrupted() || !clock.instant().isBefore(deadline)
        || !conversationStore.isLeaseCurrent(ownerKey, request.getConversationId(), leaseToken)) {
      throw new AssistantProblem(HttpStatus.CONFLICT, "LEASE_LOST", "Hội thoại không còn sẵn sàng cho lượt này.");
    }
    // Memory RAM mới mỗi lượt; framework không được ghi từng message/tool vào Redis.
    MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(30);
    List<ChatMessage> context;
    try {
      context = ChatMessageDeserializer.messagesFromJson(storedState.getContextJson());
    } catch (Exception error) {
      throw new AssistantProblem(HttpStatus.SERVICE_UNAVAILABLE, "CONVERSATION_CONTEXT_INVALID", "Không đọc được ngữ cảnh đã lưu.");
    }
    if (!context.isEmpty()) memory.add(ChatMessageDeserializer.messagesFromJson(boundedContext(context)));
    BuyerAssistantTools tools = new BuyerAssistantTools(buyerApiClient, conversationStore, validator, clock, jwt,
        ownerKey, request.getConversationId(), leaseToken, deadline);
    BuyerAssistantAgent agent = AiServices.builder(BuyerAssistantAgent.class).chatModel(chatModel).chatMemory(memory)
        .tools(tools).maxToolCallingRoundTrips(3).toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation()).build();
    String answer = agent.chat(request.getMessage());
    if (answer == null || answer.isBlank()) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "MODEL_EMPTY_REPLY", "Trợ lý chưa có câu trả lời phù hợp.");
    }
    answer = redactSensitiveText(answer);
    if (answer.length() > 8000) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "MODEL_REPLY_TOO_LARGE", "Câu trả lời vượt giới hạn. Vui lòng hỏi ngắn gọn hơn.");
    }
    List<ChatMessage> messages = new ArrayList<>();
    for (ChatMessage message : memory.messages()) {
      if (message instanceof AiMessage ai && ai.text() != null) {
        messages.add(ai.withText(redactSensitiveText(ai.text())));
      } else messages.add(message);
    }
    TurnResult result = new TurnResult();
    result.setAnswer(answer); result.setContextJson(boundedContext(messages));
    result.setDraft(tools.getPurchaseDraft()); result.setEvents(tools.getReferencedEvents());
    result.setOrders(tools.getReferencedOrders());
    return result;
  }

  private String redactSensitiveText(String text) {
    if (text == null) return null;
    return text.trim().replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*", "[REDACTED]")
        .replaceAll("[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}", "[REDACTED]")
        .replaceAll("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+", "[REDACTED]");
  }

  private String requestHash(ChatRequest request) {
    try {
      String canonical = request.getConversationId() + "\n" + request.getVersion() + "\n" + request.getRequestId()
          + "\n" + request.getMessage().trim();
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new AssistantProblem(HttpStatus.INTERNAL_SERVER_ERROR, "REQUEST_HASH_UNAVAILABLE", "Không xác minh được yêu cầu.");
    }
  }

  private String boundedContext(List<ChatMessage> messages) {
    List<ChatMessage> retained = new ArrayList<>();
    boolean started = false;
    Set<String> pendingTools = new HashSet<>();
    boolean finalAnswer = false;
    for (ChatMessage message : messages) {
      if (message instanceof SystemMessage) {
        if (started || !retained.isEmpty()) throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_CONTEXT_SEQUENCE", "Ngữ cảnh không hợp lệ.");
        retained.add(message);
        continue;
      }
      if (!started && !(message instanceof UserMessage)) continue; // Bỏ fragment đầu window đã mất user message.
      if (message instanceof UserMessage) {
        if (started && (!pendingTools.isEmpty() || !finalAnswer)) {
          throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_CONTEXT_SEQUENCE", "Ngữ cảnh không đủ một lượt hoàn chỉnh.");
        }
        started = true; finalAnswer = false;
      } else if (message instanceof AiMessage ai) {
        if (!pendingTools.isEmpty() || finalAnswer) throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_CONTEXT_SEQUENCE", "Ngữ cảnh tool không hợp lệ.");
        if (ai.hasToolExecutionRequests()) {
          for (var tool : ai.toolExecutionRequests()) {
            if (!pendingTools.add(tool.id() + "\u0000" + tool.name())) {
              throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_CONTEXT_SEQUENCE", "Tool request bị trùng.");
            }
          }
        } else finalAnswer = true;
      } else if (message instanceof ToolExecutionResultMessage tool) {
        if (!pendingTools.remove(tool.id() + "\u0000" + tool.toolName())) {
          throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_CONTEXT_SEQUENCE", "Tool result không có request phù hợp.");
        }
      } else throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_CONTEXT_SEQUENCE", "Kiểu ngữ cảnh không được hỗ trợ.");
      retained.add(message);
    }
    if (!started || !pendingTools.isEmpty() || !finalAnswer) {
      throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "INVALID_CONTEXT_SEQUENCE", "Lượt chat không đủ ngữ cảnh hoàn chỉnh.");
    }
    while (true) {
      String json = ChatMessageSerializer.messagesToJson(retained);
      // Không sửa protocol metadata/thought signatures: nếu chứa secret nhận diện được thì bỏ cả lượt.
      if (!json.equals(redactSensitiveText(json))) {
        throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "SENSITIVE_CONTEXT_REJECTED", "Ngữ cảnh chứa thông tin không được phép lưu.");
      }
      if (retained.size() <= 30 && json.getBytes(StandardCharsets.UTF_8).length <= 98304) return json;
      int start = retained.getFirst() instanceof SystemMessage ? 1 : 0;
      int nextTurn = start + 1;
      while (nextTurn < retained.size() && !(retained.get(nextTurn) instanceof UserMessage)) nextTurn++;
      if (nextTurn == retained.size()) {
        throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "CONTEXT_TOO_LARGE", "Lượt trò chuyện vượt giới hạn ngữ cảnh.");
      }
      retained.subList(start, nextTurn).clear();
    }
  }

  private List<TranscriptMessage> appendTranscript(ConversationState storedState, ChatRequest request, TurnResult result, Instant completedAt) {
    List<TranscriptMessage> transcript = new ArrayList<>(storedState.getMessages());
    TranscriptMessage user = new TranscriptMessage();
    user.setId(request.getRequestId()); user.setRole("user"); user.setText(request.getMessage()); user.setCreatedAt(completedAt);
    user.setEvents(List.of());
    TranscriptMessage assistant = new TranscriptMessage();
    assistant.setId(UUID.randomUUID()); assistant.setRole("assistant"); assistant.setText(result.getAnswer());
    assistant.setCreatedAt(completedAt); assistant.setDraft(result.getDraft()); assistant.setEvents(result.getEvents());
    assistant.setOrders(result.getOrders());
    transcript.add(user); transcript.add(assistant);
    while (transcript.size() > 50 || transcriptMapper.writeValueAsBytes(transcript).length > 98304) {
      if (transcript.size() <= 2) {
        throw new AssistantProblem(HttpStatus.BAD_GATEWAY, "TRANSCRIPT_TOO_LARGE", "Lượt trò chuyện vượt giới hạn hiển thị.");
      }
      transcript.subList(0, 2).clear();
    }
    return List.copyOf(transcript);
  }
}

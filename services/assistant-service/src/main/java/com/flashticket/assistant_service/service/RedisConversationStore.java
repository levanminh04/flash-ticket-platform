package com.flashticket.assistant_service.service;

import com.flashticket.assistant_service.dto.ChatRequest;
import com.flashticket.assistant_service.dto.ConversationState;
import com.flashticket.assistant_service.dto.ResetConversationRequest;
import com.flashticket.assistant_service.exception.AssistantProblem;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class RedisConversationStore {
  private final StringRedisTemplate redisTemplate;
  private final JsonMapper jsonMapper;
  @Value("${assistant.redis-key-prefix}") private String prefix;
  @Value("classpath:redis/open.lua") private Resource openResource;
  @Value("classpath:redis/begin-turn.lua") private Resource beginResource;
  @Value("classpath:redis/commit-turn.lua") private Resource commitResource;
  @Value("classpath:redis/reset.lua") private Resource resetResource;
  @Value("classpath:redis/lease.lua") private Resource leaseResource;
  private DefaultRedisScript<String> openScript;
  private DefaultRedisScript<String> beginScript;
  private DefaultRedisScript<String> commitScript;
  private DefaultRedisScript<String> resetScript;
  private DefaultRedisScript<Long> leaseScript;

  @PostConstruct
  public void initializeScripts() {
    if (prefix == null || !prefix.matches("[a-zA-Z0-9:_-]{1,160}")) {
      throw new IllegalStateException("ASSISTANT_REDIS_KEY_PREFIX_INVALID");
    }
    openScript = new DefaultRedisScript<>(); openScript.setLocation(openResource); openScript.setResultType(String.class);
    beginScript = new DefaultRedisScript<>(); beginScript.setLocation(beginResource); beginScript.setResultType(String.class);
    commitScript = new DefaultRedisScript<>(); commitScript.setLocation(commitResource); commitScript.setResultType(String.class);
    resetScript = new DefaultRedisScript<>(); resetScript.setLocation(resetResource); resetScript.setResultType(String.class);
    leaseScript = new DefaultRedisScript<>(); leaseScript.setLocation(leaseResource); leaseScript.setResultType(Long.class);
  }

  public ConversationState open(String ownerKey) {
    ConversationState state = new ConversationState();
    state.setConversationId(UUID.randomUUID()); state.setVersion(UUID.randomUUID());
    state.setContextJson("[]"); state.setMessages(List.of());
    return executeStateScript(openScript, ownerKey, List.of(jsonMapper.writeValueAsString(state)));
  }

  public ConversationState read(String ownerKey) {
    try {
      String json = redisTemplate.opsForValue().get(keys(ownerKey).getFirst());
      if (json == null) throw new AssistantProblem(HttpStatus.GONE, "CONVERSATION_EXPIRED", "Hội thoại đã hết hạn.");
      if (json.getBytes(StandardCharsets.UTF_8).length > 524288) throw new IllegalStateException();
      ConversationState state = jsonMapper.readValue(json, ConversationState.class);
      if (state.getConversationId() == null || state.getVersion() == null || state.getMessages() == null
          || state.getContextJson() == null || state.getExpiresAtEpochMillis() <= 0) throw new IllegalStateException();
      return state;
    } catch (AssistantProblem error) { throw error;
    } catch (Exception error) {
      throw new AssistantProblem(HttpStatus.SERVICE_UNAVAILABLE, "CONVERSATION_STORE_UNAVAILABLE", "Không đọc được hội thoại. Vui lòng thử lại.");
    }
  }

  public ConversationState beginTurn(String ownerKey, ChatRequest request, String requestHash, UUID leaseToken) {
    return executeStateScript(beginScript, ownerKey, List.of(request.getConversationId().toString(), request.getVersion().toString(),
        request.getRequestId().toString(), requestHash, leaseToken.toString()));
  }

  public ConversationState commitTurn(String ownerKey, UUID expectedConversationId, UUID expectedVersion, UUID leaseToken, ConversationState nextState) {
    return executeStateScript(commitScript, ownerKey, List.of(expectedConversationId.toString(), expectedVersion.toString(),
        leaseToken.toString(), jsonMapper.writeValueAsString(nextState)));
  }

  public ConversationState reset(String ownerKey, ResetConversationRequest request) {
    ConversationState state = new ConversationState();
    state.setConversationId(UUID.randomUUID()); state.setVersion(UUID.randomUUID());
    state.setContextJson("[]"); state.setMessages(List.of());
    return executeStateScript(resetScript, ownerKey, List.of(request.getConversationId().toString(),
        request.getVersion().toString(), jsonMapper.writeValueAsString(state)));
  }

  public boolean isLeaseCurrent(String ownerKey, UUID conversationId, UUID leaseToken) {
    try {
      return Long.valueOf(1).equals(redisTemplate.execute(leaseScript, keys(ownerKey), "check", conversationId.toString(), leaseToken.toString()));
    } catch (Exception error) {
      throw new AssistantProblem(HttpStatus.SERVICE_UNAVAILABLE, "CONVERSATION_STORE_UNAVAILABLE", "Không xác minh được hội thoại.");
    }
  }

  public void release(String ownerKey, UUID leaseToken) {
    try {
      redisTemplate.execute(leaseScript, keys(ownerKey), "release", "", leaseToken.toString());
    } catch (Exception ignored) {
      // Lease tự hết sau 60s; cleanup lỗi không được đổi kết quả đã commit.
    }
  }

  private ConversationState executeStateScript(DefaultRedisScript<String> script, String ownerKey, List<String> arguments) {
    try {
      String result = redisTemplate.execute(script, keys(ownerKey), arguments.toArray());
      if (result == null) throw new IllegalStateException();
      if (!result.startsWith("OK\n")) {
        HttpStatus status = switch (result) {
          case "CONVERSATION_EXPIRED" -> HttpStatus.GONE;
          case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
          case "CONVERSATION_CONFLICT", "CONVERSATION_BUSY", "REQUEST_ID_CONFLICT", "LEASE_LOST" -> HttpStatus.CONFLICT;
          default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        String code = status == HttpStatus.SERVICE_UNAVAILABLE ? "CONVERSATION_STORE_UNAVAILABLE" : result;
        throw new AssistantProblem(status, code, "Hội thoại không sẵn sàng. Vui lòng đọc lại trạng thái trước khi thử tiếp.");
      }
      String json = result.substring(3);
      if (json.getBytes(StandardCharsets.UTF_8).length > 524288) throw new IllegalStateException();
      ConversationState state = jsonMapper.readValue(json, ConversationState.class);
      if (state.getConversationId() == null || state.getVersion() == null || state.getMessages() == null
          || state.getContextJson() == null || state.getExpiresAtEpochMillis() <= 0) throw new IllegalStateException();
      return state;
    } catch (AssistantProblem error) { throw error;
    } catch (Exception error) {
      throw new AssistantProblem(HttpStatus.SERVICE_UNAVAILABLE, "CONVERSATION_STORE_UNAVAILABLE", "Không cập nhật được hội thoại. Vui lòng đọc lại trạng thái.");
    }
  }

  private List<String> keys(String ownerKey) {
    if (ownerKey == null || !ownerKey.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("INVALID_OWNER_KEY");
    String base = prefix + "{" + ownerKey + "}:";
    return List.of(base + "state", base + "lease", base + "quota");
  }
}

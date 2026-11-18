package com.flashticket.assistant_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.flashticket.assistant_service.dto.*;
import com.flashticket.assistant_service.exception.AssistantProblem;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class RedisConversationStoreIT {
  private LettuceConnectionFactory connection;
  private StringRedisTemplate redis;
  private RedisConversationStore first;
  private RedisConversationStore second;
  private final String owner = "a".repeat(64);
  private String prefix;
  private List<String> testKeys = List.of();

  @BeforeEach
  void setUp() {
    String host = System.getenv("REDIS_HOST");
    String port = System.getenv("REDIS_PORT");
    String configuredPrefix = System.getenv("ASSISTANT_REDIS_KEY_PREFIX");
    assumeTrue(host != null && !host.isBlank() && port != null && !port.isBlank()
        && configuredPrefix != null && !configuredPrefix.isBlank(), "Cần env-file Redis test được phép; không dùng giá trị mặc định.");
    // Namespace test trong env là điều kiện trước mọi kết nối/ghi Redis.
    assertTrue(configuredPrefix.contains(":test:") && configuredPrefix.matches("[a-zA-Z0-9:_-]{1,100}"), "Chỉ chạy với namespace chứa :test: riêng trong env-file.");
    prefix = configuredPrefix + UUID.randomUUID() + ":";
    String base = prefix + "{" + owner + "}:";
    testKeys = List.of(base + "state", base + "lease", base + "quota");
    var standalone = new RedisStandaloneConfiguration(host, Integer.parseInt(port));
    String password = System.getenv("REDIS_PASSWORD");
    assertNotNull(password, "Khai báo REDIS_PASSWORD trong env-file, có thể rỗng nếu test instance không auth.");
    if (!password.isEmpty()) standalone.setPassword(password);
    var client = LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2))
        .clientOptions(ClientOptions.builder().socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(2)).build()).build()).build();
    connection = new LettuceConnectionFactory(standalone, client); connection.afterPropertiesSet(); connection.start();
    redis = new StringRedisTemplate(connection); redis.afterPropertiesSet();
    first = new RedisConversationStore(redis, JsonMapper.builder().build());
    second = new RedisConversationStore(redis, JsonMapper.builder().build());
    for (var store : List.of(first, second)) {
      ReflectionTestUtils.setField(store, "prefix", prefix);
      ReflectionTestUtils.setField(store, "openResource", new ClassPathResource("redis/open.lua"));
      ReflectionTestUtils.setField(store, "beginResource", new ClassPathResource("redis/begin-turn.lua"));
      ReflectionTestUtils.setField(store, "commitResource", new ClassPathResource("redis/commit-turn.lua"));
      ReflectionTestUtils.setField(store, "resetResource", new ClassPathResource("redis/reset.lua"));
      ReflectionTestUtils.setField(store, "leaseResource", new ClassPathResource("redis/lease.lua"));
      store.initializeScripts();
    }
  }

  @AfterEach
  void tearDown() {
    try { if (redis != null) redis.delete(testKeys); }
    finally { if (connection != null) connection.destroy(); }
  }

  @Test
  void openAndReadDoNotRenewExistingTtl() {
    var opened = first.open(owner);
    assertTrue(opened.getMessages().isEmpty()); assertEquals("[]", opened.getContextJson());
    assertTrue(redis.getExpire(testKeys.getFirst(), TimeUnit.MILLISECONDS) > 86300000);
    redis.expire(testKeys.getFirst(), Duration.ofSeconds(5));
    long before = redis.getExpire(testKeys.getFirst(), TimeUnit.MILLISECONDS);
    assertEquals(opened.getConversationId(), second.open(owner).getConversationId());
    assertEquals(opened.getVersion(), first.read(owner).getVersion());
    long after = redis.getExpire(testKeys.getFirst(), TimeUnit.MILLISECONDS);
    assertTrue(after > 0 && after <= before);
    redis.delete(testKeys.getFirst());
    assertEquals("CONVERSATION_EXPIRED", assertThrows(AssistantProblem.class, () -> first.read(owner)).getCode());
    assertNotEquals(opened.getConversationId(), first.open(owner).getConversationId());
  }

  @Test
  void serializesReplicasAndFencesResetAndExpiredLease() {
    var state = first.open(owner);
    var request = new ChatRequest(); request.setConversationId(state.getConversationId()); request.setVersion(state.getVersion()); request.setRequestId(UUID.randomUUID()); request.setMessage("Hello");
    UUID oldLease = UUID.randomUUID(); UUID newLease = UUID.randomUUID();
    first.beginTurn(owner, request, "hash", oldLease);
    assertEquals("CONVERSATION_BUSY", assertThrows(AssistantProblem.class, () -> second.beginTurn(owner, request, "hash", newLease)).getCode());
    assertEquals("1", redis.opsForValue().get(testKeys.get(2)));
    var reset = new ResetConversationRequest(); reset.setConversationId(state.getConversationId()); reset.setVersion(state.getVersion());
    var fresh = second.reset(owner, reset);
    assertFalse(first.isLeaseCurrent(owner, state.getConversationId(), oldLease));
    assertThrows(AssistantProblem.class, () -> first.commitTurn(owner, state.getConversationId(), state.getVersion(), oldLease, state));
    request.setConversationId(fresh.getConversationId()); request.setVersion(fresh.getVersion());
    second.beginTurn(owner, request, "hash", newLease);
    first.release(owner, oldLease);
    assertTrue(second.isLeaseCurrent(owner, fresh.getConversationId(), newLease));
    // Xóa đúng lease test để mô phỏng Redis expiry, không chờ 60 giây.
    redis.delete(testKeys.get(1));
    assertEquals("LEASE_LOST", assertThrows(AssistantProblem.class, () -> second.commitTurn(owner, fresh.getConversationId(), fresh.getVersion(), newLease, fresh)).getCode());
    second.beginTurn(owner, request, "hash", newLease);
    redis.delete(testKeys.getFirst());
    assertEquals("CONVERSATION_EXPIRED", assertThrows(AssistantProblem.class, () -> second.commitTurn(owner, fresh.getConversationId(), fresh.getVersion(), newLease, fresh)).getCode());
    assertFalse(Boolean.TRUE.equals(redis.hasKey(testKeys.getFirst())));
  }

  @Test
  void commitsAtomicallyAndCountsOnlyNewAttemptsForQuota() {
    var state = first.open(owner);
    var request = new ChatRequest(); request.setConversationId(state.getConversationId()); request.setVersion(state.getVersion()); request.setRequestId(UUID.randomUUID()); request.setMessage("Hello");
    UUID lease = UUID.randomUUID(); first.beginTurn(owner, request, "hash", lease);
    redis.expire(testKeys.getFirst(), Duration.ofSeconds(5));
    var next = new ConversationState(); next.setConversationId(state.getConversationId()); next.setVersion(UUID.randomUUID()); next.setContextJson("[]"); next.setMessages(List.of());
    next.setLastRequestId(request.getRequestId()); next.setLastRequestHash("hash");
    var response = new ChatResponse(); response.setConversationId(next.getConversationId()); response.setVersion(next.getVersion()); response.setRequestId(request.getRequestId()); response.setMessage("Saved"); response.setEvents(List.of()); next.setLastResponse(response);
    var committed = first.commitTurn(owner, state.getConversationId(), state.getVersion(), lease, next);
    assertEquals(next.getVersion(), committed.getVersion()); assertEquals("Saved", second.read(owner).getLastResponse().getMessage());
    assertTrue(committed.getMessages().isEmpty()); assertEquals("[]", committed.getContextJson());
    assertFalse(Boolean.TRUE.equals(redis.hasKey(testKeys.get(1))));
    assertTrue(redis.getExpire(testKeys.getFirst(), TimeUnit.MILLISECONDS) > 86300000);
    redis.expire(testKeys.getFirst(), Duration.ofSeconds(5));
    long before = redis.getExpire(testKeys.getFirst(), TimeUnit.MILLISECONDS);
    assertEquals("Saved", second.beginTurn(owner, request, "hash", UUID.randomUUID()).getLastResponse().getMessage());
    assertEquals("1", redis.opsForValue().get(testKeys.get(2)));
    assertTrue(redis.getExpire(testKeys.getFirst(), TimeUnit.MILLISECONDS) <= before);
    assertEquals("REQUEST_ID_CONFLICT", assertThrows(AssistantProblem.class, () -> second.beginTurn(owner, request, "different", UUID.randomUUID())).getCode());
    request.setVersion(committed.getVersion());
    for (int attempt = 0; attempt < 5; attempt++) {
      request.setRequestId(UUID.randomUUID()); UUID attemptLease = UUID.randomUUID();
      first.beginTurn(owner, request, "new-hash", attemptLease); first.release(owner, attemptLease);
    }
    request.setRequestId(UUID.randomUUID());
    assertEquals("RATE_LIMITED", assertThrows(AssistantProblem.class, () -> second.beginTurn(owner, request, "new-hash", UUID.randomUUID())).getCode());
    for (String corrupt : List.of("broken", "{}", "[]")) {
      redis.opsForValue().set(testKeys.getFirst(), corrupt, Duration.ofSeconds(5));
      assertEquals("CONVERSATION_STORE_UNAVAILABLE", assertThrows(AssistantProblem.class, () -> first.read(owner)).getCode());
      assertEquals("CONVERSATION_STORE_UNAVAILABLE", assertThrows(AssistantProblem.class, () -> second.beginTurn(owner, request, "hash", UUID.randomUUID())).getCode());
    }
  }
}

package com.flashticket.assistant_service.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class AssistantConfiguration {
  @Value("${assistant.model.base-url}") private String baseUrl;
  @Value("${assistant.model.api-key}") private String apiKey;
  @Value("${assistant.model.name}") private String model;

  @Bean
  public ChatModel buyerChatModel() {
    return OpenAiChatModel.builder().baseUrl(baseUrl).apiKey(apiKey).modelName(model)
        .timeout(Duration.ofSeconds(15)).maxRetries(0).maxTokens(2048)
        .logRequests(false).logResponses(false).build();
  }

  @Bean
  public RestClient buyerRestClient() {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofSeconds(5));
    return RestClient.builder().requestFactory(factory).build();
  }

  @Bean(destroyMethod = "shutdown")
  public ExecutorService assistantExecutor() {
    // Không queue: provider chậm không được chiếm thêm hàng đợi vô hạn.
    return new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new SynchronousQueue<>(),
        new ThreadPoolExecutor.AbortPolicy());
  }

  @Bean
  public ThreadPoolTaskExecutor assistantSseExecutor() {
    // Worker SSE chờ lượt chat; dùng riêng để không chiếm chỗ của worker chạy model.
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(0);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setThreadNamePrefix("assistant-sse-");
    return executor;
  }

  @Bean
  public ThreadPoolTaskExecutor assistantSseTurnExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(0);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setThreadNamePrefix("assistant-sse-turn-");
    return executor;
  }

  @Bean
  public Clock assistantClock() {
    return Clock.systemUTC();
  }
}

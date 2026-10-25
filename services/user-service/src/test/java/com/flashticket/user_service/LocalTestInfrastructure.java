package com.flashticket.user_service;

import static org.mockito.Mockito.mock;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration(proxyBeanMethods = false)
class LocalTestInfrastructure {
  @Bean(destroyMethod = "shutdownNow")
  MongoServer testMongoServer() {
    return new MongoServer(new MemoryBackend());
  }

  @Bean(destroyMethod = "close")
  MongoClient testMongoClient(MongoServer server) {
    return MongoClients.create(server.bindAndGetConnectionString());
  }

  @Bean
  MongoTemplate mongoTemplate(MongoClient client) {
    return new MongoTemplate(client, "user_test");
  }

  @Bean
  @ConditionalOnProperty(name = "user.test.mock-jwt", havingValue = "true", matchIfMissing = true)
  JwtDecoder jwtDecoder() {
    return mock(JwtDecoder.class);
  }
}

package com.flashticket.booking_service.exception;

import static org.assertj.core.api.Assertions.*;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class ErrorClassifierTests {
  @Test
  void businessFailureRetainsCauseAndSafeMessage() {
    var cause = new IllegalStateException("SENTINEL-secret");
    var ex = new BusinessException(BusinessErrorCode.ORDER_EXPIRED, cause);
    var result = ErrorClassifier.classify(ex);
    assertThat(result.code()).isEqualTo("ORDER_EXPIRED");
    assertThat(result.message()).isEqualTo("Đơn hàng đã hết thời hạn giữ chỗ.");
    assertThat(result.exception()).isSameAs(ex);
    assertThat(ex.getCause()).isSameAs(cause);
    assertThat(result.errorType()).isEqualTo(BusinessException.class.getName());
  }

  static Stream<Arguments> nativeFailures() {
    return Stream.of(
        Arguments.of(new OptimisticLockingFailureException("secret"), "CONCURRENT_MODIFICATION"),
        Arguments.of(new CannotAcquireLockException("secret"), "RESOURCE_BUSY"),
        Arguments.of(new DataAccessResourceFailureException("secret"), "DATASTORE_UNAVAILABLE"),
        Arguments.of(new RedisConnectionFailureException("secret"), "DATASTORE_UNAVAILABLE"),
        Arguments.of(new QueryTimeoutException("secret"), "DATASTORE_TIMEOUT"),
        Arguments.of(
            new AmqpConnectException(new ConnectException("secret")), "MESSAGE_BROKER_UNAVAILABLE"),
        Arguments.of(
            new OAuth2AuthorizationException(new OAuth2Error("invalid_client")),
            "INTERNAL_AUTH_UNAVAILABLE"),
        Arguments.of(retryable(new SocketTimeoutException("secret")), "DEPENDENCY_TIMEOUT"),
        Arguments.of(retryable(new ConnectException("secret")), "DEPENDENCY_UNAVAILABLE"),
        Arguments.of(retryable(new IOException("secret")), "DEPENDENCY_FAILURE"),
        Arguments.of(httpFailure(401), "DEPENDENCY_FAILURE"),
        Arguments.of(new DataIntegrityViolationException("secret"), "INTERNAL_ERROR"),
        Arguments.of(new IllegalArgumentException("secret"), "INTERNAL_ERROR"),
        Arguments.of(
            new RuntimeException("secret", new SocketTimeoutException("secret")),
            "INTERNAL_ERROR"));
  }

  @ParameterizedTest
  @MethodSource("nativeFailures")
  void nativeFailuresKeepTheirClassification(Throwable error, String code) {
    var result = ErrorClassifier.classify(error);
    assertThat(result.code()).isEqualTo(code);
    assertThat(result.exception()).isSameAs(error);
    assertThat(result.message()).doesNotContain("secret");
  }

  @Test
  void wrappersKeepOriginalExceptionAndRelevantType() {
    var nativeFailure = new QueryTimeoutException("secret");
    var wrapper = new CompletionException(nativeFailure);
    var result = ErrorClassifier.classify(wrapper);
    assertThat(result.code()).isEqualTo("DATASTORE_TIMEOUT");
    assertThat(result.errorType()).isEqualTo(QueryTimeoutException.class.getName());
    assertThat(result.exception()).isSameAs(wrapper);
  }

  @Test
  void unknownOrCyclicCausesAreBounded() {
    RuntimeException cycle =
        new RuntimeException("secret") {
          @Override
          public synchronized Throwable getCause() {
            return this;
          }
        };
    assertThat(ErrorClassifier.classify(cycle).code()).isEqualTo("INTERNAL_ERROR");
    Throwable deep = new BusinessException(BusinessErrorCode.SEAT_UNAVAILABLE);
    for (int i = 0; i < 20; i++) deep = new CompletionException(deep);
    assertThat(ErrorClassifier.classify(deep).code()).isEqualTo("INTERNAL_ERROR");
    var result = ErrorClassifier.of("ROUTE_NOT_FOUND", null);
    assertThat(result.code()).isEqualTo("ROUTE_NOT_FOUND");
    assertThat(result.errorType()).isEqualTo("ROUTE_NOT_FOUND");
    assertThat(result.exception()).isNull();
    assertThat(ErrorClassifier.of("arbitrary-secret", null).code()).isEqualTo("INTERNAL_ERROR");
    assertThat(ErrorClassifier.classify(null).code()).isEqualTo("INTERNAL_ERROR");
  }

  private static Request request() {
    return Request.create(
        Request.HttpMethod.GET,
        "http://127.0.0.1/probe",
        Map.of(),
        (byte[]) null,
        StandardCharsets.UTF_8,
        null);
  }

  private static RetryableException retryable(Throwable cause) {
    return new RetryableException(
        -1, "SENTINEL-body", Request.HttpMethod.GET, cause, (Long) null, request());
  }

  private static FeignException httpFailure(int status) {
    return FeignException.errorStatus(
        "probe",
        Response.builder()
            .status(status)
            .reason("secret")
            .headers(Map.of())
            .request(request())
            .build());
  }
}

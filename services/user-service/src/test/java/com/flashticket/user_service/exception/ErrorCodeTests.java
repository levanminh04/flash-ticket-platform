package com.flashticket.user_service.exception;

import static org.assertj.core.api.Assertions.*;

import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoSocketReadException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ServerAddress;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.dao.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class ErrorCodeTests {
  static Stream<Arguments> nativeFailures() {
    return Stream.of(
        Arguments.of(new DuplicateKeyException("SENTINEL-uri"), ErrorCode.DATA_CONFLICT, 409),
        Arguments.of(
            new OptimisticLockingFailureException("SENTINEL"),
            ErrorCode.CONCURRENT_MODIFICATION,
            409),
        Arguments.of(new QueryTimeoutException("SENTINEL"), ErrorCode.DATASTORE_TIMEOUT, 503),
        Arguments.of(
            new MongoExecutionTimeoutException(50, "SENTINEL"), ErrorCode.DATASTORE_TIMEOUT, 503),
        Arguments.of(new MongoTimeoutException("SENTINEL"), ErrorCode.DATASTORE_UNAVAILABLE, 503),
        Arguments.of(
            new MongoSocketReadException("SENTINEL", new ServerAddress("localhost")),
            ErrorCode.DATASTORE_UNAVAILABLE,
            503),
        Arguments.of(
            new DataAccessResourceFailureException("SENTINEL"),
            ErrorCode.DATASTORE_UNAVAILABLE,
            503),
        Arguments.of(
            new AmqpConnectException(new java.net.ConnectException("SENTINEL")),
            ErrorCode.MESSAGE_BROKER_UNAVAILABLE,
            503),
        Arguments.of(
            new org.springframework.amqp.support.converter.MessageConversionException("SENTINEL"),
            ErrorCode.INVALID_MESSAGE,
            500),
        Arguments.of(new BadCredentialsException("SENTINEL"), ErrorCode.UNAUTHENTICATED, 401),
        Arguments.of(new AccessDeniedException("SENTINEL"), ErrorCode.ACCESS_DENIED, 403),
        Arguments.of(new IllegalArgumentException("SENTINEL"), ErrorCode.INTERNAL_ERROR, 500));
  }

  @ParameterizedTest
  @MethodSource("nativeFailures")
  void nativeFailuresKeepTheirClassification(Throwable failure, ErrorCode expected, int status) {
    var actual = ErrorCode.from(failure);
    assertThat(actual).isEqualTo(expected);
    assertThat(actual.status().value()).isEqualTo(status);
    assertThat(actual.message()).isNotBlank().doesNotContain("SENTINEL");
  }

  @Test
  void businessFailureKeepsItsCodeAndSafeMessage() {
    var cause = new IllegalStateException("SENTINEL");
    var failure = new BusinessException(ErrorCode.DATA_CONFLICT, cause);
    assertThat(ErrorCode.from(failure)).isEqualTo(ErrorCode.DATA_CONFLICT);
    assertThat(failure.getMessage()).doesNotContain("SENTINEL");
    assertThat(failure.getCause()).isSameAs(cause);
  }

  @Test
  void unwrapsOnlyBoundedTechnicalWrappers() {
    Throwable failure = new DuplicateKeyException("SENTINEL");
    failure =
        new CompletionException(new ExecutionException(new InvocationTargetException(failure)));
    assertThat(ErrorCode.from(failure)).isEqualTo(ErrorCode.DATA_CONFLICT);
    for (int i = 0; i < 32; i++) failure = new CompletionException(failure);
    assertThat(ErrorCode.from(failure)).isEqualTo(ErrorCode.INTERNAL_ERROR);
    assertThat(ErrorCode.from(null)).isEqualTo(ErrorCode.INTERNAL_ERROR);
  }
}

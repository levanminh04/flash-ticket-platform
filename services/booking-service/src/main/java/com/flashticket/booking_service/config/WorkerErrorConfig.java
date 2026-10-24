package com.flashticket.booking_service.config;

import com.flashticket.booking_service.exception.ErrorClassifier;
import com.flashticket.booking_service.observability.BookingContext;
import com.flashticket.booking_service.observability.FailureLog;
import java.util.Map;
import org.springframework.boot.task.SimpleAsyncTaskSchedulerCustomizer;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ErrorHandler;

@Configuration(proxyBeanMethods = false)
public class WorkerErrorConfig {

  @Bean
  ThreadPoolTaskSchedulerCustomizer bookingTaskSchedulerCustomizer() {
    return scheduler -> scheduler.setErrorHandler(workerErrorHandler());
  }

  @Bean
  SimpleAsyncTaskSchedulerCustomizer bookingSimpleSchedulerCustomizer() {
    return scheduler -> scheduler.setErrorHandler(workerErrorHandler());
  }

  private ErrorHandler workerErrorHandler() {
    return error -> {
      try (var ignored = BookingContext.open(Map.of())) {
        FailureLog.record(
            ErrorClassifier.classify(error),
            "booking.worker.failed",
            "worker.execute",
            FailureLog.Outcome.FAILED);
      }
    };
  }
}

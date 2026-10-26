package com.flashticket.event_service.messaging;

@FunctionalInterface
public interface MessagePublisher {
  void publish(OutboxStore.Message message);

  final class Failure extends RuntimeException {
    private final String code;

    public Failure(String code) {
      super(code);
      this.code = code;
    }

    public String code() {
      return code;
    }
  }
}

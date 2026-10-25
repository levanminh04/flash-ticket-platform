package com.flashticket.booking_service.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/** Bổ sung các field cố định cần thiết cho JSON log của Booking. */
public final class BookingLogFields
    implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
  /** Thêm schemaVersion dạng số và timestamp UTC cho mỗi log. */
  @Override
  public void customize(JsonWriter.Members<ILoggingEvent> members) {
    members.add("schemaVersion", 1);
    members.add("timestamp", event -> event.getInstant().toString());
  }
}

package com.flashticket.event_service.config;

import com.flashticket.event_service.observability.EventContext;
import java.beans.PropertyEditorSupport;
import java.util.UUID;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

@ControllerAdvice(basePackages = "com.flashticket.event_service.api")
public class EventWebConfig {
  @InitBinder
  public void canonicalUuid(WebDataBinder binder) {
    // A converter alone permits Spring's default UUID editor fallback after conversion fails.
    binder.registerCustomEditor(
        UUID.class,
        new PropertyEditorSupport() {
          @Override
          public void setAsText(String value) {
            UUID id = EventContext.uuid(value);
            if (id == null) throw new IllegalArgumentException("Invalid UUID");
            setValue(id);
          }
        });
  }
}

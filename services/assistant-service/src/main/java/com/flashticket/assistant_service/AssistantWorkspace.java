package com.flashticket.assistant_service;

public enum AssistantWorkspace {
  BUYER,
  ORGANIZER,
  ADMIN;

  public static AssistantWorkspace fromPath(String path) {
    return switch (path) {
      case null -> BUYER;
      case "organizer" -> ORGANIZER;
      case "admin" -> ADMIN;
      default -> throw new IllegalArgumentException("Unknown assistant workspace");
    };
  }
}

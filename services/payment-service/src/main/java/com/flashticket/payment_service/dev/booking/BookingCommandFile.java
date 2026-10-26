package com.flashticket.payment_service.dev.booking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Keeps the exact command for retry after a lost publisher confirmation. */
public final class BookingCommandFile {
  private BookingCommandFile() {}

  public static void save(Path path, String json) throws IOException {
    var parent = path.toAbsolutePath().getParent();
    if (parent != null) Files.createDirectories(parent);
    Files.writeString(path, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
  }

  public static String read(Path path) throws IOException {
    return Files.readString(path, StandardCharsets.UTF_8);
  }
}

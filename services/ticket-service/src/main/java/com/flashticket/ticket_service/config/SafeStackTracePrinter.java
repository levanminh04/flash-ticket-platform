package com.flashticket.ticket_service.config;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.boot.logging.StackTracePrinter;

/** Giữ loại exception và vị trí code, bỏ message có thể chứa secret. */
public final class SafeStackTracePrinter implements StackTracePrinter {
  /** In stack/cause có giới hạn độ dài và chống duyệt lặp khi cause tạo vòng. */
  @Override
  public void printStackTrace(Throwable exception, Appendable output) throws IOException {
    if (exception == null) return;
    var pending = new ArrayDeque<Throwable>();
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    var text = new StringBuilder();
    pending.add(exception);
    while (!pending.isEmpty() && seen.size() < 16 && text.length() < 8192) {
      Throwable current = pending.removeFirst();
      if (!seen.add(current)) continue;
      text.append(current.getClass().getName()).append('\n');
      StackTraceElement[] frames = current.getStackTrace();
      for (int i = 0; i < Math.min(frames.length, 32) && text.length() < 8192; i++) {
        text.append("\tat ").append(frames[i]).append('\n');
      }
      if (current.getCause() != null) pending.addLast(current.getCause());
      Throwable[] suppressed = current.getSuppressed();
      for (int i = 0; i < Math.min(suppressed.length, 16); i++) pending.addLast(suppressed[i]);
    }
    output.append(text, 0, Math.min(text.length(), 8192));
  }
}

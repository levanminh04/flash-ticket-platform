package com.flashticket.event_service.domain;

import java.util.ArrayList;
import java.util.regex.Pattern;

/** Parses drawing commands and numeric arguments only; never XML, URLs or script. */
final class SvgPath {
  private static final Pattern TOKEN =
      Pattern.compile("[A-Za-z]|[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

  private SvgPath() {}

  static boolean valid(String path) {
    var matcher = TOKEN.matcher(path);
    var tokens = new ArrayList<String>();
    int end = 0;
    while (matcher.find()) {
      String gap = path.substring(end, matcher.start());
      if (!gap.matches("[\\s,]*") || gap.chars().filter(c -> c == ',').count() > 1) return false;
      if (gap.contains(",")
          && (Character.isLetter(matcher.group().charAt(0))
              || tokens.isEmpty()
              || Character.isLetter(tokens.getLast().charAt(0)))) return false;
      tokens.add(matcher.group());
      end = matcher.end();
    }
    if (!path.substring(end).matches("\\s*")
        || tokens.isEmpty()
        || !tokens.getFirst().equalsIgnoreCase("M")) return false;
    int index = 0;
    char command = 0;
    while (index < tokens.size()) {
      String token = tokens.get(index);
      if (Character.isLetter(token.charAt(0))) {
        command = Character.toUpperCase(token.charAt(0));
        index++;
      }
      int count =
          switch (command) {
            case 'M', 'L', 'T' -> 2;
            case 'H', 'V' -> 1;
            case 'C' -> 6;
            case 'S', 'Q' -> 4;
            case 'A' -> 7;
            case 'Z' -> 0;
            default -> -1;
          };
      if (count < 0) return false;
      if (count == 0) {
        command = 0;
        continue;
      }
      if (index + count > tokens.size()) return false;
      for (int n = 0; n < count; n++) {
        double value;
        try {
          value = Double.parseDouble(tokens.get(index + n));
        } catch (NumberFormatException e) {
          return false;
        }
        if (!Double.isFinite(value)
            || (command == 'A' && (n == 0 || n == 1) && value < 0)
            || (command == 'A' && (n == 3 || n == 4) && value != 0 && value != 1)) return false;
      }
      index += count;
      if (command == 'M') command = 'L';
    }
    return true;
  }
}

package com.smartbus.gateway.util;

import java.util.regex.Pattern;

/**
 * Strips HTML tags from free-text inputs before they are persisted or returned in responses.
 * Prevents stored-XSS by ensuring that user-supplied strings containing markup cannot
 * be injected into downstream HTML contexts.
 */
public final class HtmlSanitizer {

  private static final Pattern HTML_TAGS = Pattern.compile("<[^>]*>");

  private HtmlSanitizer() {
  }

  /**
   * Returns the input with all {@code <...>} tag sequences removed, or {@code null} if
   * the input is {@code null}. Leading and trailing whitespace is also trimmed.
   */
  public static String strip(String input) {
    if (input == null) {
      return null;
    }
    return HTML_TAGS.matcher(input.trim()).replaceAll("");
  }
}

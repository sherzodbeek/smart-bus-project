package com.smartbus.schedule.util;

import java.util.regex.Pattern;

/**
 * Strips HTML tags from free-text inputs before they are persisted.
 */
public final class HtmlSanitizer {

  private static final Pattern HTML_TAGS = Pattern.compile("<[^>]*>");

  private HtmlSanitizer() {
  }

  public static String strip(String input) {
    if (input == null) {
      return null;
    }
    return HTML_TAGS.matcher(input.trim()).replaceAll("");
  }
}

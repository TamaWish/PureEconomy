package io.github.tamawish.pureeconomy.util;

import java.math.BigDecimal;

/** Parses non-negative decimal amounts from command input. */
public final class Amounts {

  private Amounts() {}

  /**
   * Parses a non-negative decimal amount, accepting optional thousands commas.
   *
   * @param raw player-supplied text; may be {@code null}
   * @return parsed amount, or {@code null} when invalid or negative
   */
  public static BigDecimal parse(String raw) {
    if (raw == null) {
      return null;
    }
    String cleaned = raw.trim().replace(",", "");
    if (cleaned.isEmpty()) {
      return null;
    }
    try {
      BigDecimal value = new BigDecimal(cleaned);
      if (value.compareTo(BigDecimal.ZERO) < 0) {
        return null;
      }
      return value;
    } catch (NumberFormatException e) {
      return null;
    }
  }
}

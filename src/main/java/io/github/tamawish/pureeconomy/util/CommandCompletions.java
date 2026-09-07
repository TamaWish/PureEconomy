package io.github.tamawish.pureeconomy.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared tab-completion helpers for PureEconomy commands. */
public final class CommandCompletions {

  private CommandCompletions() {}

  /**
   * Returns values whose lowercase form starts with the given prefix.
   *
   * @param values candidate completions
   * @param prefix partial input from the player; may be empty
   * @return matching values in the same order as {@code values}
   */
  public static List<String> filter(List<String> values, String prefix) {
    String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
    List<String> matches = new ArrayList<>();
    for (String value : values) {
      if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
        matches.add(value);
      }
    }
    return matches;
  }
}

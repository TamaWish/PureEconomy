package io.github.tamawish.pureeconomy.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Immutable definition of a configured currency. */
public final class Currency {

  private final String id;
  private final String singular;
  private final String plural;
  private final String symbol;
  private final int decimals;
  private final BigDecimal starting;
  private final BigDecimal max;
  private final boolean payable;
  private final String formatPattern;
  private final ThreadLocal<DecimalFormat> decimalFormats;

  /**
   * Creates a currency definition.
   *
   * @param id lowercase currency id
   * @param singular singular display name
   * @param plural plural display name
   * @param symbol optional prefix symbol; may be {@code null}
   * @param decimals number of fractional digits to keep
   * @param starting balance granted the first time an account sees this currency
   * @param max maximum balance, or negative when uncapped
   * @param payable whether {@code /pay} may move this currency
   */
  public Currency(
      String id,
      String singular,
      String plural,
      String symbol,
      int decimals,
      BigDecimal starting,
      BigDecimal max,
      boolean payable) {
    this.id = id.toLowerCase(Locale.ROOT);
    this.singular = singular;
    this.plural = plural;
    this.symbol = symbol == null ? "" : symbol;
    this.decimals = Math.max(0, decimals);
    this.starting = starting.setScale(this.decimals, RoundingMode.HALF_UP);
    this.max = max;
    this.payable = payable;

    StringBuilder pattern = new StringBuilder("#,##0");
    if (this.decimals > 0) {
      pattern.append('.');
      pattern.append("0".repeat(this.decimals));
    }
    this.formatPattern = pattern.toString();
    this.decimalFormats =
        ThreadLocal.withInitial(
            () -> {
              DecimalFormat format =
                  new DecimalFormat(formatPattern, DecimalFormatSymbols.getInstance(Locale.US));
              format.setRoundingMode(RoundingMode.HALF_UP);
              return format;
            });
  }

  /**
   * Returns the currency id.
   *
   * @return lowercase id used in config and commands
   */
  public String id() {
    return id;
  }

  /**
   * Returns the singular display name.
   *
   * @return singular name such as {@code Coin}
   */
  public String singular() {
    return singular;
  }

  /**
   * Returns the plural display name.
   *
   * @return plural name such as {@code Coins}
   */
  public String plural() {
    return plural;
  }

  /**
   * Returns the optional symbol prefix.
   *
   * @return symbol text, never {@code null}
   */
  public String symbol() {
    return symbol;
  }

  /**
   * Returns the configured decimal precision.
   *
   * @return non-negative number of fractional digits
   */
  public int decimals() {
    return decimals;
  }

  /**
   * Returns the starting wallet balance for new accounts.
   *
   * @return normalized starting amount
   */
  public BigDecimal starting() {
    return starting;
  }

  /**
   * Returns the configured maximum balance.
   *
   * @return maximum amount, or a negative value when uncapped
   */
  public BigDecimal max() {
    return max;
  }

  /**
   * Returns whether a finite maximum balance is configured.
   *
   * @return {@code true} when {@link #max()} is zero or positive
   */
  public boolean hasMax() {
    return max != null && max.compareTo(BigDecimal.ZERO) >= 0;
  }

  /**
   * Returns whether players may transfer this currency with {@code /pay}.
   *
   * @return {@code true} when player payments are allowed
   */
  public boolean payable() {
    return payable;
  }

  /**
   * Rounds an amount to this currency's scale.
   *
   * @param amount raw amount to normalize
   * @return amount scaled with {@link RoundingMode#HALF_UP}
   */
  public BigDecimal normalize(BigDecimal amount) {
    return amount.setScale(decimals, RoundingMode.HALF_UP);
  }

  /**
   * Returns whether {@code amount} is above the configured maximum.
   *
   * @param amount candidate balance
   * @return {@code true} when a max exists and {@code amount} exceeds it
   */
  public boolean exceedsMax(BigDecimal amount) {
    return hasMax() && amount.compareTo(max) > 0;
  }

  /**
   * Chooses singular or plural based on the absolute amount.
   *
   * @param amount amount being displayed
   * @return singular when the amount equals one, otherwise plural
   */
  public String displayName(BigDecimal amount) {
    return amount.compareTo(BigDecimal.ONE) == 0 ? singular : plural;
  }

  /**
   * Formats an amount with the currency symbol or display name.
   *
   * @param amount amount to format
   * @return human-readable balance text
   */
  public String format(BigDecimal amount) {
    BigDecimal normalized = normalize(amount);
    String formatted = decimalFormats.get().format(normalized);
    if (symbol.isEmpty()) {
      return formatted + " " + displayName(normalized);
    }
    return symbol + formatted;
  }

  /**
   * Returns the plural name used in most player-facing messages.
   *
   * @return plural display name
   */
  public String name() {
    return plural;
  }
}

package io.github.tamawish.pureeconomy.hook;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.Currency;
import java.math.BigDecimal;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** PlaceholderAPI expansion exposing wallet balances for every configured currency. */
final class PureEconomyExpansion extends PlaceholderExpansion {

  private static final String BALANCE_PREFIX = "balance_";
  private static final String FORMATTED_SUFFIX = "_formatted";

  private final PureEconomy plugin;

  /**
   * Creates the expansion.
   *
   * @param plugin owning plugin
   */
  PureEconomyExpansion(PureEconomy plugin) {
    this.plugin = plugin;
  }

  @Override
  public @NotNull String getIdentifier() {
    return "pureeconomy";
  }

  @Override
  public @NotNull String getAuthor() {
    return String.join(", ", plugin.getPluginMeta().getAuthors());
  }

  @Override
  public @NotNull String getVersion() {
    return plugin.getPluginMeta().getVersion();
  }

  @Override
  public boolean persist() {
    return true;
  }

  @Override
  public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
    if (player == null || !params.startsWith(BALANCE_PREFIX)) {
      return null;
    }

    String currencyId = params.substring(BALANCE_PREFIX.length());
    boolean formatted = currencyId.endsWith(FORMATTED_SUFFIX);
    if (formatted) {
      currencyId = currencyId.substring(0, currencyId.length() - FORMATTED_SUFFIX.length());
    }

    Currency currency = plugin.economy().currency(currencyId);
    if (currency == null) {
      return null;
    }

    plugin.economy().ensureAccount(player.getUniqueId(), player.getName());
    BigDecimal balance = plugin.economy().get(player.getUniqueId(), currency);
    return formatted ? currency.format(balance) : balance.toPlainString();
  }
}

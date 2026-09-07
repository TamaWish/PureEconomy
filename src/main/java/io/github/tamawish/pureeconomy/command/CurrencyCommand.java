package io.github.tamawish.pureeconomy.command;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.Currency;
import io.github.tamawish.pureeconomy.lang.Lang;
import io.github.tamawish.pureeconomy.permission.Permissions.Node;
import io.github.tamawish.pureeconomy.util.CommandCompletions;
import java.util.Collections;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Handles {@code /currency} listing of configured currencies. */
public final class CurrencyCommand implements CommandExecutor, TabCompleter {

  private final PureEconomy plugin;

  /**
   * Creates the command.
   *
   * @param plugin owning plugin
   */
  public CurrencyCommand(PureEconomy plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    Lang lang = plugin.lang();
    if (!plugin.permissions().has(sender, Node.CURRENCY)) {
      lang.send(sender, "no-permission");
      return true;
    }
    lang.send(sender, "currency-list-header", Lang.of("default", plugin.economy().defaultId()));
    for (String id : plugin.economy().currencyIds()) {
      Currency currency = plugin.economy().currency(id);
      lang.send(
          sender,
          "currency-list-line",
          Lang.of(
              "id",
              currency.id(),
              "singular",
              currency.singular(),
              "plural",
              currency.plural(),
              "symbol",
              currency.symbol()));
    }
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1) {
      return CommandCompletions.filter(plugin.economy().currencyIds(), args[0]);
    }
    return Collections.emptyList();
  }
}

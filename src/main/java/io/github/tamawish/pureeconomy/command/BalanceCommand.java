package io.github.tamawish.pureeconomy.command;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.Currency;
import io.github.tamawish.pureeconomy.economy.EconomyService;
import io.github.tamawish.pureeconomy.lang.Lang;
import io.github.tamawish.pureeconomy.permission.Permissions.Node;
import io.github.tamawish.pureeconomy.util.CommandCompletions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Handles {@code /balance} wallet and bank lookups. */
public final class BalanceCommand implements CommandExecutor, TabCompleter {

  private final PureEconomy plugin;

  /**
   * Creates the command and registers its tab completer.
   *
   * @param plugin owning plugin
   */
  public BalanceCommand(PureEconomy plugin) {
    this.plugin = plugin;
    plugin.getCommand("balance").setTabCompleter(this);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    Lang lang = plugin.lang();
    EconomyService eco = plugin.economy();

    if (!plugin.permissions().has(sender, Node.BALANCE)) {
      lang.send(sender, "no-permission");
      return true;
    }

    UUID target;
    Currency currency = null;

    if (args.length == 0) {
      if (!(sender instanceof Player player)) {
        lang.send(sender, "usage-balance");
        return true;
      }
      target = player.getUniqueId();
    } else if (args.length == 1) {
      Currency maybe = eco.currency(args[0]);
      if (maybe != null && sender instanceof Player player) {
        target = player.getUniqueId();
        currency = maybe;
      } else {
        if (!plugin.permissions().has(sender, Node.BALANCE_OTHERS)) {
          lang.send(sender, "no-permission");
          return true;
        }
        target = eco.resolve(args[0]);
        if (target == null) {
          lang.send(sender, "unknown-player", Lang.of("player", args[0]));
          return true;
        }
      }
    } else {
      if (!plugin.permissions().has(sender, Node.BALANCE_OTHERS)) {
        lang.send(sender, "no-permission");
        return true;
      }
      target = eco.resolve(args[0]);
      if (target == null) {
        lang.send(sender, "unknown-player", Lang.of("player", args[0]));
        return true;
      }
      currency = eco.currency(args[1]);
      if (currency == null) {
        lang.send(sender, "unknown-currency", Lang.of("currency", args[1]));
        return true;
      }
    }

    String name =
        sender instanceof Player player && player.getUniqueId().equals(target)
            ? player.getName()
            : eco.nameOf(target);

    if (currency != null) {
      String key =
          sender instanceof Player player && player.getUniqueId().equals(target)
              ? "balance-self"
              : "balance-other";
      lang.send(
          sender,
          key,
          Lang.of(
              "player",
              name,
              "currency",
              currency.name(),
              "amount",
              currency.format(eco.get(target, currency)),
              "bank",
              currency.format(eco.getBank(target, currency))));
      return true;
    }

    lang.send(sender, "balance-all-header", Lang.of("player", name));
    for (String id : eco.currencyIds()) {
      Currency entry = eco.currency(id);
      lang.send(
          sender,
          "balance-all-line",
          Lang.of(
              "currency",
              entry.name(),
              "amount",
              entry.format(eco.get(target, entry)),
              "bank",
              entry.format(eco.getBank(target, entry))));
    }
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1) {
      List<String> out = new ArrayList<>(plugin.economy().currencyIds());
      plugin.getServer().getOnlinePlayers().forEach(player -> out.add(player.getName()));
      return CommandCompletions.filter(out, args[0]);
    }
    if (args.length == 2) {
      return CommandCompletions.filter(plugin.economy().currencyIds(), args[1]);
    }
    return Collections.emptyList();
  }
}

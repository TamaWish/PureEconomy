package io.github.tamawish.pureeconomy.command;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.Currency;
import io.github.tamawish.pureeconomy.economy.EconomyService;
import io.github.tamawish.pureeconomy.lang.Lang;
import io.github.tamawish.pureeconomy.permission.Permissions.Node;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class BalanceCommand implements CommandExecutor, TabCompleter {

    private final PureEconomy plugin;

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

        String name = sender instanceof Player p && p.getUniqueId().equals(target)
                ? p.getName()
                : eco.nameOf(target);

        if (currency != null) {
            String key = sender instanceof Player p && p.getUniqueId().equals(target) ? "balance-self" : "balance-other";
            lang.send(sender, key, Lang.of(
                    "player", name,
                    "currency", currency.name(),
                    "amount", currency.format(eco.get(target, currency)),
                    "bank", currency.format(eco.getBank(target, currency))
            ));
            return true;
        }

        lang.send(sender, "balance-all-header", Lang.of("player", name));
        for (String id : eco.currencyIds()) {
            Currency c = eco.currency(id);
            lang.send(sender, "balance-all-line", Lang.of(
                    "currency", c.name(),
                    "amount", c.format(eco.get(target, c)),
                    "bank", c.format(eco.getBank(target, c))
            ));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(plugin.economy().currencyIds());
            plugin.getServer().getOnlinePlayers().forEach(p -> out.add(p.getName()));
            return filter(out, args[0]);
        }
        if (args.length == 2) {
            return filter(plugin.economy().currencyIds(), args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> in, String prefix) {
        String p = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : in) {
            if (s.toLowerCase().startsWith(p)) {
                out.add(s);
            }
        }
        return out;
    }
}

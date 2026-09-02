package io.github.tamawish.pureeconomy.command;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.Currency;
import io.github.tamawish.pureeconomy.economy.EconomyService;
import io.github.tamawish.pureeconomy.lang.Lang;
import io.github.tamawish.pureeconomy.util.Amounts;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class EcoCommand implements CommandExecutor, TabCompleter {

    private final PureEconomy plugin;

    public EcoCommand(PureEconomy plugin) {
        this.plugin = plugin;
        plugin.getCommand("eco").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Lang lang = plugin.lang();
        if (args.length == 0) {
            lang.send(sender, "usage-eco");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("pureeconomy.eco.reload")) {
                lang.send(sender, "no-permission");
                return true;
            }
            plugin.reloadAll();
            lang.send(sender, "eco-reload");
            return true;
        }

        if (args.length < 2) {
            lang.send(sender, "usage-eco");
            return true;
        }

        EconomyService eco = plugin.economy();
        UUID target = eco.resolve(args[1]);
        if (target == null) {
            lang.send(sender, "unknown-player", Lang.of("player", args[1]));
            return true;
        }

        Currency currency = args.length >= 4 ? eco.currency(args[3]) : eco.defaultCurrency();
        if (args.length >= 4 && currency == null) {
            lang.send(sender, "unknown-currency", Lang.of("currency", args[3]));
            return true;
        }
        if (currency == null) {
            lang.send(sender, "unknown-currency", Lang.of("currency", eco.defaultId()));
            return true;
        }

        String playerName = eco.nameOf(target);

        if (sub.equals("reset")) {
            if (!sender.hasPermission("pureeconomy.eco.reset")) {
                lang.send(sender, "no-permission");
                return true;
            }
            if (args.length >= 3) {
                currency = eco.currency(args[2]);
                if (currency == null) {
                    lang.send(sender, "unknown-currency", Lang.of("currency", args[2]));
                    return true;
                }
            }
            eco.reset(target, currency);
            lang.send(sender, "eco-reset", Lang.of("player", playerName, "currency", currency.name()));
            return true;
        }

        if (args.length < 3) {
            lang.send(sender, "usage-eco");
            return true;
        }

        BigDecimal amount = Amounts.parse(args[2]);
        if (amount == null) {
            lang.send(sender, "invalid-amount");
            return true;
        }

        switch (sub) {
            case "give" -> {
                if (!sender.hasPermission("pureeconomy.eco.give")) {
                    lang.send(sender, "no-permission");
                    return true;
                }
                if (!eco.add(target, currency, amount)) {
                    lang.send(sender, "max-balance", Lang.of("currency", currency.name()));
                    return true;
                }
                lang.send(sender, "eco-give", Lang.of("amount", currency.format(amount), "player", playerName));
            }
            case "take" -> {
                if (!sender.hasPermission("pureeconomy.eco.take")) {
                    lang.send(sender, "no-permission");
                    return true;
                }
                if (!eco.take(target, currency, amount)) {
                    lang.send(sender, "not-enough", Lang.of("currency", currency.name()));
                    return true;
                }
                lang.send(sender, "eco-take", Lang.of("amount", currency.format(amount), "player", playerName));
            }
            case "set" -> {
                if (!sender.hasPermission("pureeconomy.eco.set")) {
                    lang.send(sender, "no-permission");
                    return true;
                }
                if (!eco.set(target, currency, amount)) {
                    lang.send(sender, "max-balance", Lang.of("currency", currency.name()));
                    return true;
                }
                lang.send(sender, "eco-set", Lang.of(
                        "player", playerName,
                        "currency", currency.name(),
                        "amount", currency.format(amount)
                ));
            }
            default -> lang.send(sender, "usage-eco");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("give", "take", "set", "reset", "reload"), args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            List<String> names = new ArrayList<>();
            plugin.getServer().getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            return filter(plugin.economy().currencyIds(), args[2]);
        }
        if (args.length == 4) {
            return filter(plugin.economy().currencyIds(), args[3]);
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

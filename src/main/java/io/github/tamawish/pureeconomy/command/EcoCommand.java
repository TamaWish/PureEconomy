package io.github.tamawish.pureeconomy.command;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.Currency;
import io.github.tamawish.pureeconomy.economy.EconomyService;
import io.github.tamawish.pureeconomy.lang.Lang;
import io.github.tamawish.pureeconomy.permission.Permissions.Node;
import io.github.tamawish.pureeconomy.util.Amounts;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class EcoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("give", "take", "set", "reset", "bank", "reload");
    private static final List<String> BANK_ACTIONS = List.of("give", "take", "set", "reset");

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
            if (!plugin.permissions().has(sender, Node.ECO_RELOAD)) {
                lang.send(sender, "no-permission");
                return true;
            }
            plugin.reloadAll();
            lang.send(sender, "eco-reload");
            return true;
        }

        if (sub.equals("bank")) {
            return handleBank(sender, args);
        }

        if (args.length < 2) {
            lang.send(sender, "usage-eco");
            return true;
        }

        EconomyService eco = plugin.economy();
        UUID target = eco.resolve(args[1]);
        if (target == null) {
            lang.send(sender, "account-missing", Lang.of("player", args[1]));
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
            if (!plugin.permissions().has(sender, Node.ECO_RESET)) {
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
                if (!plugin.permissions().has(sender, Node.ECO_GIVE)) {
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
                if (!plugin.permissions().has(sender, Node.ECO_TAKE)) {
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
                if (!plugin.permissions().has(sender, Node.ECO_SET)) {
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

    private boolean handleBank(CommandSender sender, String[] args) {
        Lang lang = plugin.lang();
        if (args.length < 3) {
            lang.send(sender, "usage-eco-bank");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (!BANK_ACTIONS.contains(action)) {
            lang.send(sender, "usage-eco-bank");
            return true;
        }

        EconomyService eco = plugin.economy();
        UUID target = eco.resolve(args[2]);
        if (target == null) {
            lang.send(sender, "account-missing", Lang.of("player", args[2]));
            return true;
        }

        String playerName = eco.nameOf(target);

        if (action.equals("reset")) {
            if (!plugin.permissions().has(sender, Node.ECO_BANK_RESET)) {
                lang.send(sender, "no-permission");
                return true;
            }
            Currency currency = args.length >= 4 ? eco.currency(args[3]) : eco.defaultCurrency();
            if (currency == null) {
                lang.send(sender, "unknown-currency", Lang.of("currency", args.length >= 4 ? args[3] : eco.defaultId()));
                return true;
            }
            eco.resetBank(target, currency);
            lang.send(sender, "eco-bank-reset", Lang.of("player", playerName, "currency", currency.name()));
            return true;
        }

        if (args.length < 4) {
            lang.send(sender, "usage-eco-bank");
            return true;
        }

        BigDecimal amount = Amounts.parse(args[3]);
        if (amount == null) {
            lang.send(sender, "invalid-amount");
            return true;
        }

        Currency currency = args.length >= 5 ? eco.currency(args[4]) : eco.defaultCurrency();
        if (currency == null) {
            lang.send(sender, "unknown-currency", Lang.of("currency", args.length >= 5 ? args[4] : eco.defaultId()));
            return true;
        }

        switch (action) {
            case "give" -> {
                if (!plugin.permissions().has(sender, Node.ECO_BANK_GIVE)) {
                    lang.send(sender, "no-permission");
                    return true;
                }
                if (!eco.addBank(target, currency, amount)) {
                    lang.send(sender, "bank-max-balance", Lang.of("currency", currency.name()));
                    return true;
                }
                lang.send(sender, "eco-bank-give", Lang.of("amount", currency.format(amount), "player", playerName));
            }
            case "take" -> {
                if (!plugin.permissions().has(sender, Node.ECO_BANK_TAKE)) {
                    lang.send(sender, "no-permission");
                    return true;
                }
                if (!eco.takeBank(target, currency, amount)) {
                    lang.send(sender, "not-enough", Lang.of("currency", currency.name()));
                    return true;
                }
                lang.send(sender, "eco-bank-take", Lang.of("amount", currency.format(amount), "player", playerName));
            }
            case "set" -> {
                if (!plugin.permissions().has(sender, Node.ECO_BANK_SET)) {
                    lang.send(sender, "no-permission");
                    return true;
                }
                if (!eco.setBank(target, currency, amount)) {
                    lang.send(sender, "bank-max-balance", Lang.of("currency", currency.name()));
                    return true;
                }
                lang.send(sender, "eco-bank-set", Lang.of(
                        "player", playerName,
                        "currency", currency.name(),
                        "amount", currency.format(amount)
                ));
            }
            default -> lang.send(sender, "usage-eco-bank");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args[0].equalsIgnoreCase("reload")) {
            return Collections.emptyList();
        }
        if (args[0].equalsIgnoreCase("bank")) {
            if (args.length == 2) {
                return filter(BANK_ACTIONS, args[1]);
            }
            if (args.length == 3) {
                return filter(onlineNames(), args[2]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("reset")) {
                return filter(plugin.economy().currencyIds(), args[3]);
            }
            if (args.length == 5 && !args[1].equalsIgnoreCase("reset")) {
                return filter(plugin.economy().currencyIds(), args[4]);
            }
            return Collections.emptyList();
        }
        if (args.length == 2) {
            return filter(onlineNames(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            return filter(plugin.economy().currencyIds(), args[2]);
        }
        if (args.length == 4) {
            return filter(plugin.economy().currencyIds(), args[3]);
        }
        return Collections.emptyList();
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        plugin.getServer().getOnlinePlayers().forEach(p -> names.add(p.getName()));
        return names;
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

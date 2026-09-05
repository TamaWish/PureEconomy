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
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class BankCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ACTIONS = List.of("balance", "transfer", "withdraw");
    private final PureEconomy plugin;

    public BankCommand(PureEconomy plugin) {
        this.plugin = plugin;
        plugin.getCommand("bank").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Lang lang = plugin.lang();
        if (!(sender instanceof Player player)) {
            lang.send(sender, "player-only");
            return true;
        }
        if (!plugin.permissions().has(player, Node.BANK)) {
            lang.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendAllBalances(player);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if ("balance".equals(action) || "bal".equals(action)) {
            if (args.length > 2) {
                lang.send(sender, "usage-bank");
                return true;
            }
            if (args.length == 1) {
                sendAllBalances(player);
            } else {
                Currency currency = currencyOrError(sender, args[1]);
                if (currency != null) {
                    sendBalance(player, currency);
                }
            }
            return true;
        }

        boolean toBank = "transfer".equals(action) || "deposit".equals(action) || "tf".equals(action);
        boolean withdraw = "withdraw".equals(action);
        if ((!toBank && !withdraw) || args.length < 2 || args.length > 3) {
            lang.send(sender, "usage-bank");
            return true;
        }

        if (!plugin.permissions().has(player, toBank ? Node.BANK_TRANSFER : Node.BANK_WITHDRAW)) {
            lang.send(sender, "no-permission");
            return true;
        }

        BigDecimal amount = Amounts.parse(args[1]);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            lang.send(sender, "invalid-amount");
            return true;
        }

        EconomyService eco = plugin.economy();
        Currency currency = args.length == 3 ? currencyOrError(sender, args[2]) : eco.defaultCurrency();
        if (currency == null) {
            return true;
        }
        amount = currency.normalize(amount);

        if (toBank) {
            if (!eco.has(player.getUniqueId(), currency, amount)) {
                lang.send(sender, "not-enough", Lang.of("currency", currency.name()));
                return true;
            }
            if (currency.exceedsMax(eco.getBank(player.getUniqueId(), currency).add(amount))) {
                lang.send(sender, "bank-max-balance", Lang.of("currency", currency.name()));
                return true;
            }
            if (eco.transferToBank(player.getUniqueId(), currency, amount)) {
                lang.send(sender, "bank-transfer", Lang.of("amount", currency.format(amount)));
            } else {
                lang.send(sender, "bank-failed");
            }
            return true;
        }

        if (eco.getBank(player.getUniqueId(), currency).compareTo(amount) < 0) {
            lang.send(sender, "bank-not-enough", Lang.of("currency", currency.name()));
            return true;
        }
        if (currency.exceedsMax(eco.get(player.getUniqueId(), currency).add(amount))) {
            lang.send(sender, "max-balance", Lang.of("currency", currency.name()));
            return true;
        }
        if (eco.withdrawFromBank(player.getUniqueId(), currency, amount)) {
            lang.send(sender, "bank-withdraw", Lang.of("amount", currency.format(amount)));
        } else {
            lang.send(sender, "bank-failed");
        }
        return true;
    }

    private Currency currencyOrError(CommandSender sender, String id) {
        Currency currency = plugin.economy().currency(id);
        if (currency == null) {
            plugin.lang().send(sender, "unknown-currency", Lang.of("currency", id));
        }
        return currency;
    }

    private void sendBalance(Player player, Currency currency) {
        String amount = currency.format(plugin.economy().getBank(player.getUniqueId(), currency));
        plugin.lang().send(player, "bank-balance",
                Lang.of("currency", currency.name(), "amount", amount));
    }

    private void sendAllBalances(Player player) {
        plugin.lang().send(player, "bank-balance-header");
        for (String id : plugin.economy().currencyIds()) {
            Currency currency = plugin.economy().currency(id);
            plugin.lang().send(player, "bank-balance-line", Lang.of(
                    "currency", currency.name(),
                    "amount", currency.format(plugin.economy().getBank(player.getUniqueId(), currency))
            ));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(ACTIONS, args[0]);
        }
        if (args.length == 2 && ("balance".equalsIgnoreCase(args[0]) || "bal".equalsIgnoreCase(args[0]))) {
            return filter(plugin.economy().currencyIds(), args[1]);
        }
        if (args.length == 3) {
            return filter(plugin.economy().currencyIds(), args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(value);
            }
        }
        return matches;
    }
}

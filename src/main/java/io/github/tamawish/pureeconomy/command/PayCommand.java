package io.github.tamawish.pureeconomy.command;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.Currency;
import io.github.tamawish.pureeconomy.economy.EconomyService;
import io.github.tamawish.pureeconomy.lang.Lang;
import io.github.tamawish.pureeconomy.util.Amounts;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class PayCommand implements CommandExecutor, TabCompleter {

    private final PureEconomy plugin;

    public PayCommand(PureEconomy plugin) {
        this.plugin = plugin;
        plugin.getCommand("pay").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Lang lang = plugin.lang();
        if (!(sender instanceof Player player)) {
            lang.send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("pureeconomy.pay")) {
            lang.send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            lang.send(sender, "usage-pay");
            return true;
        }

        EconomyService eco = plugin.economy();
        UUID target = eco.resolve(args[0]);
        if (target == null) {
            lang.send(sender, "unknown-player", Lang.of("player", args[0]));
            return true;
        }
        if (target.equals(player.getUniqueId())) {
            lang.send(sender, "pay-self");
            return true;
        }

        BigDecimal amount = Amounts.parse(args[1]);
        if (amount == null) {
            lang.send(sender, "invalid-amount");
            return true;
        }

        Currency currency = args.length >= 3 ? eco.currency(args[2]) : eco.defaultCurrency();
        if (currency == null) {
            lang.send(sender, "unknown-currency", Lang.of("currency", args.length >= 3 ? args[2] : ""));
            return true;
        }
        if (!currency.payable()) {
            lang.send(sender, "pay-disabled");
            return true;
        }

        BigDecimal min = Amounts.parse(plugin.getConfig().getString("pay-minimum", "0.01"));
        if (min != null && amount.compareTo(min) < 0) {
            lang.send(sender, "pay-minimum", Lang.of("amount", currency.format(min)));
            return true;
        }

        amount = currency.normalize(amount);
        if (!eco.has(player.getUniqueId(), currency, amount)) {
            lang.send(sender, "not-enough", Lang.of("currency", currency.name()));
            return true;
        }

        BigDecimal next = eco.get(target, currency).add(amount);
        if (currency.exceedsMax(next)) {
            lang.send(sender, "max-balance", Lang.of("currency", currency.name()));
            return true;
        }

        if (!eco.transfer(player.getUniqueId(), target, currency, amount)) {
            lang.send(sender, "not-enough", Lang.of("currency", currency.name()));
            return true;
        }

        String pretty = currency.format(amount);
        lang.send(player, "pay-sent", Lang.of("amount", pretty, "player", eco.nameOf(target)));
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            lang.send(online, "pay-received", Lang.of("amount", pretty, "player", player.getName()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            plugin.getServer().getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return filter(names, args[0]);
        }
        if (args.length == 3) {
            return filter(plugin.economy().currencyIds(), args[2]);
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

package io.github.tamawish.pureeconomy.command;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.Currency;
import io.github.tamawish.pureeconomy.economy.EconomyService;
import io.github.tamawish.pureeconomy.lang.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BaltopCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 10;
    private final PureEconomy plugin;

    public BaltopCommand(PureEconomy plugin) {
        this.plugin = plugin;
        plugin.getCommand("baltop").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Lang lang = plugin.lang();
        if (!sender.hasPermission("pureeconomy.baltop")) {
            lang.send(sender, "no-permission");
            return true;
        }

        EconomyService eco = plugin.economy();
        Currency currency = eco.defaultCurrency();
        int page = 1;

        if (args.length >= 1) {
            Currency maybe = eco.currency(args[0]);
            if (maybe != null) {
                currency = maybe;
                if (args.length >= 2) {
                    page = parsePage(args[1], 1);
                }
            } else {
                page = parsePage(args[0], -1);
                if (page < 1) {
                    lang.send(sender, "unknown-currency", Lang.of("currency", args[0]));
                    return true;
                }
            }
        }

        int pages = eco.topPages(currency, PAGE_SIZE);
        if (page > pages) {
            lang.send(sender, "baltop-page");
            return true;
        }

        List<EconomyService.BalanceEntry> entries = eco.top(currency, page, PAGE_SIZE);
        if (entries.isEmpty()) {
            lang.send(sender, "baltop-empty");
            return true;
        }

        lang.send(sender, "baltop-header", Lang.of("currency", currency.name(), "page", page + "/" + pages));
        int rank = (page - 1) * PAGE_SIZE + 1;
        for (EconomyService.BalanceEntry entry : entries) {
            lang.send(sender, "baltop-line", Lang.of(
                    "rank", String.valueOf(rank++),
                    "player", entry.name(),
                    "amount", currency.format(entry.balance())
            ));
        }
        return true;
    }

    private static int parsePage(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(plugin.economy().currencyIds(), args[0]);
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

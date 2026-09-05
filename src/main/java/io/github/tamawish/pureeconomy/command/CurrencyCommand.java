package io.github.tamawish.pureeconomy.command;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.Currency;
import io.github.tamawish.pureeconomy.lang.Lang;
import io.github.tamawish.pureeconomy.permission.Permissions.Node;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CurrencyCommand implements CommandExecutor, TabCompleter {

    private final PureEconomy plugin;

    public CurrencyCommand(PureEconomy plugin) {
        this.plugin = plugin;
        plugin.getCommand("currency").setTabCompleter(this);
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
            Currency c = plugin.economy().currency(id);
            lang.send(sender, "currency-list-line", Lang.of(
                    "id", c.id(),
                    "singular", c.singular(),
                    "plural", c.plural(),
                    "symbol", c.symbol()
            ));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(plugin.economy().currencyIds(), args[0]);
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

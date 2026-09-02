package io.github.tamawish.pureeconomy.command;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.Currency;
import io.github.tamawish.pureeconomy.lang.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class CurrencyCommand implements CommandExecutor {

    private final PureEconomy plugin;

    public CurrencyCommand(PureEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Lang lang = plugin.lang();
        if (!sender.hasPermission("pureeconomy.currency")) {
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
}

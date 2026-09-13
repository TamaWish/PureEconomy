package io.github.tamawish.pureeconomy.command

import io.github.tamawish.pureeconomy.PureEconomy
import io.github.tamawish.pureeconomy.lang.Lang
import io.github.tamawish.pureeconomy.permission.Permissions.Node
import io.github.tamawish.pureeconomy.util.CommandCompletions
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/** Handles `/currency` listing of configured currencies. */
class CurrencyCommand(
    private val plugin: PureEconomy,
) : CommandExecutor,
    TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        val lang = plugin.lang()
        if (!plugin.permissions().has(sender, Node.CURRENCY)) {
            lang.send(sender, "no-permission")
            return true
        }
        lang.send(sender, "currency-list-header", Lang.of("default", plugin.economy().defaultId()))
        for (id in plugin.economy().currencyIds()) {
            val currency = plugin.economy().currency(id)!!
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
                    currency.symbol(),
                ),
            )
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (args.size == 1) {
            return CommandCompletions.filter(plugin.economy().currencyIds(), args[0])
        }
        return emptyList()
    }
}

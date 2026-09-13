package io.github.tamawish.pureeconomy.command

import io.github.tamawish.pureeconomy.PureEconomy
import io.github.tamawish.pureeconomy.lang.Lang
import io.github.tamawish.pureeconomy.permission.Permissions.Node
import io.github.tamawish.pureeconomy.util.CommandCompletions
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/** Handles `/baltop` richest-player listings. */
class BaltopCommand(
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
        if (!plugin.permissions().has(sender, Node.BALTOP)) {
            lang.send(sender, "no-permission")
            return true
        }

        val eco = plugin.economy()
        var currency = eco.defaultCurrency()!!
        var page = 1

        if (args.isNotEmpty()) {
            val maybe = eco.currency(args[0])
            if (maybe != null) {
                currency = maybe
                if (args.size >= 2) {
                    page = parsePage(args[1], 1)
                }
            } else {
                page = parsePage(args[0], -1)
                if (page < 1) {
                    lang.send(sender, "unknown-currency", Lang.of("currency", args[0]))
                    return true
                }
            }
        }

        val result = eco.topPage(currency, page, PAGE_SIZE)
        val pages = result.pages
        if (page > pages) {
            lang.send(sender, "baltop-page")
            return true
        }
        val entries = result.entries
        if (entries.isEmpty()) {
            lang.send(sender, "baltop-empty")
            return true
        }

        lang.send(
            sender,
            "baltop-header",
            Lang.of("currency", currency.name(), "page", "$page/$pages"),
        )
        var rank = (page - 1) * PAGE_SIZE + 1
        for (entry in entries) {
            lang.send(
                sender,
                "baltop-line",
                Lang.of(
                    "rank",
                    rank++.toString(),
                    "player",
                    entry.name,
                    "amount",
                    currency.format(entry.balance),
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

    companion object {
        private const val PAGE_SIZE = 10

        private fun parsePage(
            raw: String,
            fallback: Int,
        ): Int =
            try {
                raw.toInt()
            } catch (_: NumberFormatException) {
                fallback
            }
    }
}

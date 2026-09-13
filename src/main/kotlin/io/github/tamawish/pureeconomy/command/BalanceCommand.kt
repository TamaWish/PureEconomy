package io.github.tamawish.pureeconomy.command

import io.github.tamawish.pureeconomy.PureEconomy
import io.github.tamawish.pureeconomy.economy.Currency
import io.github.tamawish.pureeconomy.lang.Lang
import io.github.tamawish.pureeconomy.permission.Permissions.Node
import io.github.tamawish.pureeconomy.util.CommandCompletions
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID

/** Handles `/balance` wallet and bank lookups. */
class BalanceCommand(
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
        val eco = plugin.economy()

        if (!plugin.permissions().has(sender, Node.BALANCE)) {
            lang.send(sender, "no-permission")
            return true
        }

        val target: UUID
        var currency: Currency? = null

        when (args.size) {
            0 -> {
                if (sender !is Player) {
                    lang.send(sender, "usage-balance")
                    return true
                }
                target = sender.uniqueId
            }
            1 -> {
                val maybe = eco.currency(args[0])
                if (maybe != null && sender is Player) {
                    target = sender.uniqueId
                    currency = maybe
                } else {
                    if (!plugin.permissions().has(sender, Node.BALANCE_OTHERS)) {
                        lang.send(sender, "no-permission")
                        return true
                    }
                    val resolved = eco.resolve(args[0], lookupOffline = false)
                    if (resolved == null) {
                        lang.send(sender, "unknown-player", Lang.of("player", args[0]))
                        return true
                    }
                    target = resolved
                }
            }
            else -> {
                if (!plugin.permissions().has(sender, Node.BALANCE_OTHERS)) {
                    lang.send(sender, "no-permission")
                    return true
                }
                val resolved = eco.resolve(args[0], lookupOffline = false)
                if (resolved == null) {
                    lang.send(sender, "unknown-player", Lang.of("player", args[0]))
                    return true
                }
                target = resolved
                currency = eco.currency(args[1])
                if (currency == null) {
                    lang.send(sender, "unknown-currency", Lang.of("currency", args[1]))
                    return true
                }
            }
        }

        val name =
            if (sender is Player && sender.uniqueId == target) {
                sender.name
            } else {
                eco.nameOf(target)
            }

        if (currency != null) {
            val key =
                if (sender is Player && sender.uniqueId == target) {
                    "balance-self"
                } else {
                    "balance-other"
                }
            lang.send(
                sender,
                key,
                Lang.of(
                    "player",
                    name,
                    "currency",
                    currency.name(),
                    "amount",
                    currency.format(eco.get(target, currency)),
                    "bank",
                    currency.format(eco.getBank(target, currency)),
                ),
            )
            return true
        }

        lang.send(sender, "balance-all-header", Lang.of("player", name))
        for (id in eco.currencyIds()) {
            val entry = eco.currency(id)!!
            lang.send(
                sender,
                "balance-all-line",
                Lang.of(
                    "currency",
                    entry.name(),
                    "amount",
                    entry.format(eco.get(target, entry)),
                    "bank",
                    entry.format(eco.getBank(target, entry)),
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
            val out = ArrayList(plugin.economy().currencyIds())
            plugin.server.onlinePlayers.forEach { out.add(it.name) }
            return CommandCompletions.filter(out, args[0])
        }
        if (args.size == 2) {
            return CommandCompletions.filter(plugin.economy().currencyIds(), args[1])
        }
        return emptyList()
    }
}

package io.github.tamawish.pureeconomy.command

import io.github.tamawish.pureeconomy.PureEconomy
import io.github.tamawish.pureeconomy.api.EconomyStatus
import io.github.tamawish.pureeconomy.lang.Lang
import io.github.tamawish.pureeconomy.permission.Permissions.Node
import io.github.tamawish.pureeconomy.util.Amounts
import io.github.tamawish.pureeconomy.util.CommandCompletions
import io.github.tamawish.pureeconomy.util.Schedulers
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/** Handles `/pay` transfers between player wallets. */
class PayCommand(
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
        if (sender !is Player) {
            lang.send(sender, "player-only")
            return true
        }
        if (!plugin.permissions().has(sender, Node.PAY)) {
            lang.send(sender, "no-permission")
            return true
        }
        if (args.size < 2) {
            lang.send(sender, "usage-pay")
            return true
        }

        val eco = plugin.economy()
        val target = eco.resolve(args[0], lookupOffline = false)
        if (target == null) {
            lang.send(sender, "unknown-player", Lang.of("player", args[0]))
            return true
        }
        if (target == sender.uniqueId) {
            lang.send(sender, "pay-self")
            return true
        }

        var amount = Amounts.parse(args[1])
        if (amount == null) {
            lang.send(sender, "invalid-amount")
            return true
        }

        val currency = if (args.size >= 3) eco.currency(args[2]) else eco.defaultCurrency()
        if (currency == null) {
            lang.send(sender, "unknown-currency", Lang.of("currency", if (args.size >= 3) args[2] else ""))
            return true
        }
        if (!currency.payable()) {
            lang.send(sender, "pay-disabled")
            return true
        }

        val min = Amounts.parse(plugin.settings().getString("pay-minimum", "0.01"))
        if (min != null && amount < min) {
            lang.send(sender, "pay-minimum", Lang.of("amount", currency.format(min)))
            return true
        }

        amount = currency.normalize(amount)
        val paid = eco.transferResult(sender.uniqueId, target, currency, amount)
        when (paid.status) {
            EconomyStatus.INSUFFICIENT -> {
                lang.send(sender, "not-enough", Lang.of("currency", currency.name()))
                return true
            }
            EconomyStatus.MAX_BALANCE -> {
                lang.send(sender, "max-balance", Lang.of("currency", currency.name()))
                return true
            }
            EconomyStatus.SUCCESS -> {}
            else -> {
                lang.send(sender, "not-enough", Lang.of("currency", currency.name()))
                return true
            }
        }

        val pretty = currency.format(amount)
        lang.send(sender, "pay-sent", Lang.of("amount", pretty, "player", eco.nameOf(target)))
        val online = Bukkit.getPlayer(target)
        if (online != null) {
            val payerName = sender.name
            // Recipient may be on another Folia region — schedule on their entity thread.
            Schedulers.runAtEntity(plugin, online) {
                lang.send(online, "pay-received", Lang.of("amount", pretty, "player", payerName))
            }
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
            val names = ArrayList<String>()
            plugin.server.onlinePlayers.forEach { names.add(it.name) }
            return CommandCompletions.filter(names, args[0])
        }
        if (args.size == 3) {
            return CommandCompletions.filter(plugin.economy().currencyIds(), args[2])
        }
        return emptyList()
    }
}

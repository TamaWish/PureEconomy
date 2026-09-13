package io.github.tamawish.pureeconomy.command

import io.github.tamawish.pureeconomy.PureEconomy
import io.github.tamawish.pureeconomy.economy.Currency
import io.github.tamawish.pureeconomy.lang.Lang
import io.github.tamawish.pureeconomy.permission.Permissions.Node
import io.github.tamawish.pureeconomy.util.Amounts
import io.github.tamawish.pureeconomy.util.CommandCompletions
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.math.BigDecimal
import java.util.Locale

/** Handles `/bank` balance, deposit, and withdraw actions. */
class BankCommand(
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
        if (!plugin.permissions().has(sender, Node.BANK)) {
            lang.send(sender, "no-permission")
            return true
        }

        if (args.isEmpty()) {
            sendAllBalances(sender)
            return true
        }

        val action = args[0].lowercase(Locale.ROOT)
        if (action == "balance" || action == "bal") {
            if (args.size > 2) {
                lang.send(sender, "usage-bank")
                return true
            }
            if (args.size == 1) {
                sendAllBalances(sender)
            } else {
                val currency = currencyOrError(sender, args[1])
                if (currency != null) {
                    sendBalance(sender, currency)
                }
            }
            return true
        }

        val toBank = action == "transfer" || action == "deposit" || action == "tf"
        val withdraw = action == "withdraw"
        if ((!toBank && !withdraw) || args.size < 2 || args.size > 3) {
            lang.send(sender, "usage-bank")
            return true
        }

        if (!plugin.permissions().has(sender, if (toBank) Node.BANK_TRANSFER else Node.BANK_WITHDRAW)) {
            lang.send(sender, "no-permission")
            return true
        }

        var amount = Amounts.parse(args[1])
        if (amount == null || amount <= BigDecimal.ZERO) {
            lang.send(sender, "invalid-amount")
            return true
        }

        val eco = plugin.economy()
        val currency = if (args.size == 3) currencyOrError(sender, args[2]) else eco.defaultCurrency()
        if (currency == null) {
            return true
        }
        amount = currency.normalize(amount)

        if (toBank) {
            if (!eco.has(sender.uniqueId, currency, amount)) {
                lang.send(sender, "not-enough", Lang.of("currency", currency.name()))
                return true
            }
            if (currency.exceedsMax(eco.getBank(sender.uniqueId, currency).add(amount))) {
                lang.send(sender, "bank-max-balance", Lang.of("currency", currency.name()))
                return true
            }
            if (eco.transferToBank(sender.uniqueId, currency, amount)) {
                lang.send(sender, "bank-transfer", Lang.of("amount", currency.format(amount)))
            } else {
                lang.send(sender, "bank-failed")
            }
            return true
        }

        if (eco.getBank(sender.uniqueId, currency) < amount) {
            lang.send(sender, "bank-not-enough", Lang.of("currency", currency.name()))
            return true
        }
        if (currency.exceedsMax(eco.get(sender.uniqueId, currency).add(amount))) {
            lang.send(sender, "max-balance", Lang.of("currency", currency.name()))
            return true
        }
        if (eco.withdrawFromBank(sender.uniqueId, currency, amount)) {
            lang.send(sender, "bank-withdraw", Lang.of("amount", currency.format(amount)))
        } else {
            lang.send(sender, "bank-failed")
        }
        return true
    }

    private fun currencyOrError(
        sender: CommandSender,
        id: String,
    ): Currency? {
        val currency = plugin.economy().currency(id)
        if (currency == null) {
            plugin.lang().send(sender, "unknown-currency", Lang.of("currency", id))
        }
        return currency
    }

    private fun sendBalance(
        player: Player,
        currency: Currency,
    ) {
        val amount = currency.format(plugin.economy().getBank(player.uniqueId, currency))
        plugin.lang().send(player, "bank-balance", Lang.of("currency", currency.name(), "amount", amount))
    }

    private fun sendAllBalances(player: Player) {
        plugin.lang().send(player, "bank-balance-header")
        for (id in plugin.economy().currencyIds()) {
            val currency = plugin.economy().currency(id)!!
            plugin.lang().send(
                player,
                "bank-balance-line",
                Lang.of(
                    "currency",
                    currency.name(),
                    "amount",
                    currency.format(plugin.economy().getBank(player.uniqueId, currency)),
                ),
            )
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (args.size == 1) {
            return CommandCompletions.filter(ACTIONS, args[0])
        }
        if (args.size == 2 &&
            (args[0].equals("balance", ignoreCase = true) || args[0].equals("bal", ignoreCase = true))
        ) {
            return CommandCompletions.filter(plugin.economy().currencyIds(), args[1])
        }
        if (args.size == 3) {
            return CommandCompletions.filter(plugin.economy().currencyIds(), args[2])
        }
        return emptyList()
    }

    companion object {
        private val ACTIONS = listOf("balance", "transfer", "withdraw")
    }
}

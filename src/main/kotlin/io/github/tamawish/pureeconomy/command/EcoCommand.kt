package io.github.tamawish.pureeconomy.command

import io.github.tamawish.pureeconomy.PureEconomy
import io.github.tamawish.pureeconomy.lang.Lang
import io.github.tamawish.pureeconomy.permission.Permissions.Node
import io.github.tamawish.pureeconomy.util.Amounts
import io.github.tamawish.pureeconomy.util.CommandCompletions
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import java.util.Locale

/** Handles `/eco` administrative wallet and bank adjustments. */
class EcoCommand(
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
        if (args.isEmpty()) {
            lang.send(sender, "usage-eco")
            return true
        }

        val sub = args[0].lowercase(Locale.ROOT)
        if (sub == "reload") {
            if (!plugin.permissions().has(sender, Node.ECO_RELOAD)) {
                lang.send(sender, "no-permission")
                return true
            }
            plugin.reloadAll()
            lang.send(sender, "eco-reload")
            return true
        }

        if (sub == "bank") {
            return handleBank(sender, args)
        }

        if (args.size < 2) {
            lang.send(sender, "usage-eco")
            return true
        }

        val eco = plugin.economy()
        val target = eco.resolve(args[1], lookupOffline = false)
        if (target == null) {
            lang.send(sender, "account-missing", Lang.of("player", args[1]))
            return true
        }

        var currency = if (args.size >= 4) eco.currency(args[3]) else eco.defaultCurrency()
        if (args.size >= 4 && currency == null) {
            lang.send(sender, "unknown-currency", Lang.of("currency", args[3]))
            return true
        }
        if (currency == null) {
            lang.send(sender, "unknown-currency", Lang.of("currency", eco.defaultId()))
            return true
        }

        val playerName = eco.nameOf(target)

        if (sub == "reset") {
            if (!plugin.permissions().has(sender, Node.ECO_RESET)) {
                lang.send(sender, "no-permission")
                return true
            }
            if (args.size >= 3) {
                currency = eco.currency(args[2])
                if (currency == null) {
                    lang.send(sender, "unknown-currency", Lang.of("currency", args[2]))
                    return true
                }
            }
            eco.reset(target, currency)
            lang.send(sender, "eco-reset", Lang.of("player", playerName, "currency", currency.name()))
            return true
        }

        if (args.size < 3) {
            lang.send(sender, "usage-eco")
            return true
        }

        val amount = Amounts.parse(args[2])
        if (amount == null) {
            lang.send(sender, "invalid-amount")
            return true
        }

        when (sub) {
            "give" -> {
                if (!plugin.permissions().has(sender, Node.ECO_GIVE)) {
                    lang.send(sender, "no-permission")
                    return true
                }
                if (!eco.add(target, currency, amount)) {
                    lang.send(sender, "max-balance", Lang.of("currency", currency.name()))
                    return true
                }
                lang.send(
                    sender,
                    "eco-give",
                    Lang.of("amount", currency.format(amount), "player", playerName),
                )
            }
            "take" -> {
                if (!plugin.permissions().has(sender, Node.ECO_TAKE)) {
                    lang.send(sender, "no-permission")
                    return true
                }
                if (!eco.take(target, currency, amount)) {
                    lang.send(sender, "not-enough", Lang.of("currency", currency.name()))
                    return true
                }
                lang.send(
                    sender,
                    "eco-take",
                    Lang.of("amount", currency.format(amount), "player", playerName),
                )
            }
            "set" -> {
                if (!plugin.permissions().has(sender, Node.ECO_SET)) {
                    lang.send(sender, "no-permission")
                    return true
                }
                if (!eco.set(target, currency, amount)) {
                    lang.send(sender, "max-balance", Lang.of("currency", currency.name()))
                    return true
                }
                lang.send(
                    sender,
                    "eco-set",
                    Lang.of(
                        "player",
                        playerName,
                        "currency",
                        currency.name(),
                        "amount",
                        currency.format(amount),
                    ),
                )
            }
            else -> lang.send(sender, "usage-eco")
        }
        return true
    }

    private fun handleBank(
        sender: CommandSender,
        args: Array<out String>,
    ): Boolean {
        val lang = plugin.lang()
        if (args.size < 3) {
            lang.send(sender, "usage-eco-bank")
            return true
        }

        val action = args[1].lowercase(Locale.ROOT)
        if (action !in BANK_ACTIONS) {
            lang.send(sender, "usage-eco-bank")
            return true
        }

        val eco = plugin.economy()
        val target = eco.resolve(args[2], lookupOffline = false)
        if (target == null) {
            lang.send(sender, "account-missing", Lang.of("player", args[2]))
            return true
        }

        val playerName = eco.nameOf(target)

        if (action == "reset") {
            if (!plugin.permissions().has(sender, Node.ECO_BANK_RESET)) {
                lang.send(sender, "no-permission")
                return true
            }
            val currency = if (args.size >= 4) eco.currency(args[3]) else eco.defaultCurrency()
            if (currency == null) {
                lang.send(
                    sender,
                    "unknown-currency",
                    Lang.of("currency", if (args.size >= 4) args[3] else eco.defaultId()),
                )
                return true
            }
            eco.resetBank(target, currency)
            lang.send(
                sender,
                "eco-bank-reset",
                Lang.of("player", playerName, "currency", currency.name()),
            )
            return true
        }

        if (args.size < 4) {
            lang.send(sender, "usage-eco-bank")
            return true
        }

        val amount = Amounts.parse(args[3])
        if (amount == null) {
            lang.send(sender, "invalid-amount")
            return true
        }

        val currency = if (args.size >= 5) eco.currency(args[4]) else eco.defaultCurrency()
        if (currency == null) {
            lang.send(
                sender,
                "unknown-currency",
                Lang.of("currency", if (args.size >= 5) args[4] else eco.defaultId()),
            )
            return true
        }

        when (action) {
            "give" -> {
                if (!plugin.permissions().has(sender, Node.ECO_BANK_GIVE)) {
                    lang.send(sender, "no-permission")
                    return true
                }
                if (!eco.addBank(target, currency, amount)) {
                    lang.send(sender, "bank-max-balance", Lang.of("currency", currency.name()))
                    return true
                }
                lang.send(
                    sender,
                    "eco-bank-give",
                    Lang.of("amount", currency.format(amount), "player", playerName),
                )
            }
            "take" -> {
                if (!plugin.permissions().has(sender, Node.ECO_BANK_TAKE)) {
                    lang.send(sender, "no-permission")
                    return true
                }
                if (!eco.takeBank(target, currency, amount)) {
                    lang.send(sender, "not-enough", Lang.of("currency", currency.name()))
                    return true
                }
                lang.send(
                    sender,
                    "eco-bank-take",
                    Lang.of("amount", currency.format(amount), "player", playerName),
                )
            }
            "set" -> {
                if (!plugin.permissions().has(sender, Node.ECO_BANK_SET)) {
                    lang.send(sender, "no-permission")
                    return true
                }
                if (!eco.setBank(target, currency, amount)) {
                    lang.send(sender, "bank-max-balance", Lang.of("currency", currency.name()))
                    return true
                }
                lang.send(
                    sender,
                    "eco-bank-set",
                    Lang.of(
                        "player",
                        playerName,
                        "currency",
                        currency.name(),
                        "amount",
                        currency.format(amount),
                    ),
                )
            }
            else -> lang.send(sender, "usage-eco-bank")
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
            return CommandCompletions.filter(SUBS, args[0])
        }
        if (args[0].equals("reload", ignoreCase = true)) {
            return emptyList()
        }
        if (args[0].equals("bank", ignoreCase = true)) {
            if (args.size == 2) {
                return CommandCompletions.filter(BANK_ACTIONS, args[1])
            }
            if (args.size == 3) {
                return CommandCompletions.filter(onlineNames(), args[2])
            }
            if (args.size == 4 && args[1].equals("reset", ignoreCase = true)) {
                return CommandCompletions.filter(plugin.economy().currencyIds(), args[3])
            }
            if (args.size == 5 && !args[1].equals("reset", ignoreCase = true)) {
                return CommandCompletions.filter(plugin.economy().currencyIds(), args[4])
            }
            return emptyList()
        }
        if (args.size == 2) {
            return CommandCompletions.filter(onlineNames(), args[1])
        }
        if (args.size == 3 && args[0].equals("reset", ignoreCase = true)) {
            return CommandCompletions.filter(plugin.economy().currencyIds(), args[2])
        }
        if (args.size == 4) {
            return CommandCompletions.filter(plugin.economy().currencyIds(), args[3])
        }
        return emptyList()
    }

    private fun onlineNames(): List<String> {
        val names = ArrayList<String>()
        plugin.server.onlinePlayers.forEach { names.add(it.name) }
        return names
    }

    companion object {
        private val SUBS = listOf("give", "take", "set", "reset", "bank", "reload")
        private val BANK_ACTIONS = listOf("give", "take", "set", "reset")
    }
}

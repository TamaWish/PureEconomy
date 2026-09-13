package io.github.tamawish.pureeconomy.network

import java.math.BigDecimal
import java.util.UUID

interface NetworkActor {
    val uuid: UUID?
    val name: String
    fun hasPermission(permission: String): Boolean
    fun send(message: String)
}

interface NetworkPlayers {
    fun online(name: String): Pair<UUID, String>?
    fun notify(uuid: UUID, message: String)
    fun names(): Collection<String>
}

class NetworkCommands(
    private val config: NetworkConfig,
    private val database: NetworkDatabase,
    private val players: NetworkPlayers,
    private val reload: () -> Boolean,
) {
    fun execute(command: String, actor: NetworkActor, args: List<String>) {
        if (!database.fingerprintMatches()) return actor.send(message("config-mismatch"))
        when (command.lowercase()) {
            "balance", "bal", "money" -> balance(actor, args)
            "bank" -> bank(actor, args)
            "pay" -> pay(actor, args)
            "baltop", "balancetop", "moneytop" -> baltop(actor, args)
            "currency", "currencies" -> currencies(actor)
            "eco" -> eco(actor, args)
        }
    }

    fun suggestions(command: String, actor: NetworkActor, args: List<String>): List<String> {
        val candidates = when {
            command == "eco" && args.size == 1 -> listOf("give", "take", "set", "reset", "bank", "reload")
            command == "bank" && args.size == 1 -> listOf("balance", "transfer", "deposit", "withdraw")
            command == "balance" && args.size == 1 -> players.names() + config.currencies.keys
            command == "pay" && args.size == 1 -> players.names()
            command == "pay" && args.size == 3 -> config.currencies.keys
            command == "baltop" && args.size == 1 -> config.currencies.keys
            else -> emptyList()
        }
        val prefix = args.lastOrNull()?.lowercase() ?: ""
        return candidates.filter { it.lowercase().startsWith(prefix) }.sorted()
    }

    private fun balance(actor: NetworkActor, args: List<String>) {
        if (!allowed(actor, "pureeconomy.balance")) return
        var target = actor.uuid
        var currencyArg: String? = null
        if (args.isNotEmpty() && config.currencies[args[0].lowercase()] == null) {
            if (!allowed(actor, "pureeconomy.balance.others")) return
            target = resolve(args[0]); currencyArg = args.getOrNull(1)
        } else if (args.isNotEmpty()) currencyArg = args[0]
        if (target == null) return actor.send(message("player-only"))
        val currencies = if (currencyArg == null) config.currencies.values else listOfNotNull(currency(currencyArg, actor))
        val targetName = database.name(target)
        currencies.forEach {
            val balance = database.balance(target, it)
            actor.send(message("balance", "player" to targetName, "currency" to it.plural, "amount" to it.format(balance.wallet), "bank" to it.format(balance.bank)))
        }
    }

    private fun bank(actor: NetworkActor, args: List<String>) {
        val uuid = player(actor) ?: return
        if (!allowed(actor, "pureeconomy.bank")) return
        val action = args.firstOrNull()?.lowercase() ?: "balance"
        if (action == "balance" || (args.size == 1 && config.currencies.containsKey(action))) {
            val selected = if (action == "balance") args.getOrNull(1) else action
            val currencies = if (selected == null) config.currencies.values else listOfNotNull(currency(selected, actor))
            currencies.forEach { val b = database.balance(uuid, it); actor.send(message("bank-balance", "currency" to it.plural, "amount" to it.format(b.bank))) }
            return
        }
        val toBank = action in setOf("transfer", "deposit", "tf")
        if (!toBank && action != "withdraw") return actor.send(message("usage-bank"))
        if (!allowed(actor, if (toBank) "pureeconomy.bank.transfer" else "pureeconomy.bank.withdraw")) return
        val amount = amount(args.getOrNull(1), actor) ?: return
        val currency = currency(args.getOrNull(2), actor) ?: return
        if (!database.move(uuid, currency, amount, toBank).success) actor.send(message("bank-failed"))
        else actor.send(message(if (toBank) "bank-transfer" else "bank-withdraw", "amount" to currency.format(amount)))
    }

    private fun pay(actor: NetworkActor, args: List<String>) {
        val from = player(actor) ?: return
        if (!allowed(actor, "pureeconomy.pay")) return
        if (args.size < 2) return actor.send(message("usage-pay"))
        val target = resolve(args[0]) ?: return actor.send(message("unknown-player", "player" to args[0]))
        if (target == from) return actor.send(message("pay-self"))
        val amount = amount(args[1], actor) ?: return
        val currency = currency(args.getOrNull(2), actor) ?: return
        if (amount < config.payMinimum) return actor.send(message("pay-minimum", "amount" to currency.format(config.payMinimum)))
        if (!database.transfer(from, target, currency, amount).success) return actor.send(message("transaction-failed"))
        actor.send(message("pay-sent", "amount" to currency.format(amount), "player" to database.name(target)))
        players.notify(target, message("pay-received", "amount" to currency.format(amount), "player" to actor.name))
    }

    private fun baltop(actor: NetworkActor, args: List<String>) {
        if (!allowed(actor, "pureeconomy.baltop")) return
        val currency = currency(args.firstOrNull(), actor) ?: return
        val page = args.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val result = database.top(currency, page, 10)
        actor.send(message("baltop-header", "currency" to currency.plural, "page" to page.toString(), "pages" to result.second.toString()))
        result.first.forEachIndexed { index, row -> actor.send("${(page - 1) * 10 + index + 1}. ${row.name} - ${currency.format(row.amount)}") }
    }

    private fun currencies(actor: NetworkActor) {
        if (!allowed(actor, "pureeconomy.currency")) return
        actor.send(message("currency-header", "default" to config.defaultCurrency))
        config.currencies.values.sortedBy { it.id }.forEach { actor.send("- ${it.id}: ${it.singular}/${it.plural} (${it.symbol})") }
    }

    private fun eco(actor: NetworkActor, args: List<String>) {
        if (args.firstOrNull()?.lowercase() == "reload") {
            if (allowed(actor, "pureeconomy.eco.reload")) actor.send(message(if (reload()) "reload" else "reload-failed")); return
        }
        var bank = false; var offset = 0
        if (args.firstOrNull()?.lowercase() == "bank") { bank = true; offset = 1 }
        val action = args.getOrNull(offset)?.lowercase() ?: return actor.send(message("usage-eco"))
        if (action !in setOf("give", "take", "set", "reset")) return actor.send(message("usage-eco"))
        if (!allowed(actor, "pureeconomy.eco.${if (bank) "bank." else ""}$action")) return
        val targetName = args.getOrNull(offset + 1) ?: return actor.send(message("usage-eco"))
        val target = resolve(targetName) ?: return actor.send(message("unknown-player", "player" to targetName))
        val amountIndex = offset + 2
        val amount = if (action == "reset") null else amount(args.getOrNull(amountIndex), actor) ?: return
        val currency = currency(args.getOrNull(if (action == "reset") amountIndex else amountIndex + 1), actor) ?: return
        val ok = when (action) {
            "give" -> database.add(target, currency, amount!!, bank)
            "take" -> database.add(target, currency, -amount!!, bank)
            "set" -> database.set(target, currency, amount!!, bank)
            else -> database.set(target, currency, if (bank) BigDecimal.ZERO else currency.starting, bank)
        }
        actor.send(message(if (ok.success) "eco-success" else "transaction-failed", "action" to action, "player" to database.name(target), "currency" to currency.plural, "amount" to currency.format(amount ?: if (bank) BigDecimal.ZERO else currency.starting)))
    }

    private fun resolve(name: String) = players.online(name)?.first ?: database.resolve(name)
    private fun player(actor: NetworkActor): UUID? = actor.uuid.also { if (it == null) actor.send(message("player-only")) }
    private fun allowed(actor: NetworkActor, permission: String): Boolean =
        (!NetworkPermissionPolicy.requiresExplicitGrant(permission) ||
            NetworkPermissionPolicy.grantsFor(permission).any(actor::hasPermission))
            .also { if (!it) actor.send(message("no-permission")) }
    private fun amount(raw: String?, actor: NetworkActor): BigDecimal? = try { BigDecimal(raw).takeIf { it > BigDecimal.ZERO } ?: throw NumberFormatException() } catch (_: Exception) { actor.send(message("invalid-amount")); null }
    private fun currency(raw: String?, actor: NetworkActor): NetworkCurrency? {
        val id = raw?.lowercase() ?: config.defaultCurrency
        return config.currencies[id] ?: run { actor.send(message("unknown-currency", "currency" to id)); null }
    }
    private fun message(key: String, vararg values: Pair<String, String>): String {
        var output = config.messages[key] ?: DEFAULT_MESSAGES[key] ?: key
        values.forEach { output = output.replace("{${it.first}}", it.second) }
        return output
    }

    companion object {
        private val DEFAULT_MESSAGES = mapOf(
            "no-permission" to "You cannot do that.", "player-only" to "Players only.",
            "unknown-player" to "Player not found: {player}", "unknown-currency" to "Unknown currency: {currency}",
            "invalid-amount" to "Invalid amount.", "pay-self" to "You cannot pay yourself.",
            "pay-minimum" to "Minimum transfer is {amount}.", "transaction-failed" to "The transaction could not be completed.",
            "config-mismatch" to "Economy configuration differs from the proxy; transactions are locked.",
            "balance" to "{player}'s {currency}: {amount} (bank {bank})", "bank-balance" to "Banked {currency}: {amount}",
            "bank-transfer" to "Transferred {amount} to your bank.", "bank-withdraw" to "Withdrew {amount} from your bank.",
            "bank-failed" to "Could not complete that bank transfer.", "pay-sent" to "Sent {amount} to {player}.",
            "pay-received" to "Received {amount} from {player}.", "baltop-header" to "Top {currency} (page {page}/{pages})",
            "currency-header" to "Currencies (default: {default})", "eco-success" to "{action} completed for {player}: {amount}",
            "reload" to "Reloaded network configuration.", "reload-failed" to "Could not reload network configuration.",
            "usage-pay" to "Usage: /pay <player> <amount> [currency]", "usage-bank" to "Usage: /bank <balance|transfer|withdraw> [amount] [currency]",
            "usage-eco" to "Usage: /eco <give|take|set|reset|bank|reload> ...",
        )
    }
}

package io.github.tamawish.pureeconomy.hook

import io.github.tamawish.pureeconomy.PureEconomy
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer

/** PlaceholderAPI expansion exposing wallet balances for every configured currency. */
internal class PureEconomyExpansion(
    private val plugin: PureEconomy,
) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "pureeconomy"

    override fun getAuthor(): String = plugin.description.authors.joinToString(", ")

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun onRequest(
        player: OfflinePlayer?,
        params: String,
    ): String? {
        if (player == null) {
            return null
        }
        val bank = params.startsWith(BANK_PREFIX)
        val prefix = if (bank) BANK_PREFIX else BALANCE_PREFIX
        if (!params.startsWith(prefix)) {
            return null
        }

        var currencyId = params.substring(prefix.length)
        val formatted = currencyId.endsWith(FORMATTED_SUFFIX)
        if (formatted) {
            currencyId = currencyId.substring(0, currencyId.length - FORMATTED_SUFFIX.length)
        }

        val currency = plugin.economy().currency(currencyId) ?: return null
        val balance = plugin.economy().peekBalance(player.uniqueId, currency, bank)
        return if (formatted) currency.format(balance) else balance.toPlainString()
    }

    companion object {
        private const val BALANCE_PREFIX = "balance_"
        private const val BANK_PREFIX = "bank_"
        private const val FORMATTED_SUFFIX = "_formatted"
    }
}

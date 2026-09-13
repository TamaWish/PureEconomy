package io.github.tamawish.pureeconomy.hook

import io.github.tamawish.pureeconomy.PureEconomy
import io.github.tamawish.pureeconomy.economy.Currency
import io.github.tamawish.pureeconomy.economy.EconomyService
import net.milkbowl.vault.economy.AbstractEconomy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.OfflinePlayer
import java.math.BigDecimal
import java.util.UUID

/** Vault [net.milkbowl.vault.economy.Economy] adapter for PureEconomy's default currency. */
class VaultEconomy(
    private val plugin: PureEconomy,
) : AbstractEconomy() {
    private fun eco(): EconomyService = plugin.economy()

    private fun cur(): Currency? = eco().defaultCurrency()

    private fun id(name: String): UUID? = eco().resolve(name, lookupOffline = false)

    override fun isEnabled(): Boolean = plugin.isEnabled && cur() != null

    override fun getName(): String = "PureEconomy"

    override fun hasBankSupport(): Boolean = false

    override fun fractionalDigits(): Int = cur()?.decimals() ?: 2

    override fun format(amount: Double): String {
        val currency = cur() ?: return amount.toString()
        return currency.format(BigDecimal.valueOf(amount))
    }

    override fun currencyNamePlural(): String = cur()?.plural() ?: "Coins"

    override fun currencyNameSingular(): String = cur()?.singular() ?: "Coin"

    override fun hasAccount(playerName: String): Boolean = id(playerName) != null

    override fun hasAccount(
        playerName: String,
        worldName: String,
    ): Boolean = hasAccount(playerName)

    override fun hasAccount(player: OfflinePlayer?): Boolean = player != null

    override fun hasAccount(
        player: OfflinePlayer?,
        worldName: String,
    ): Boolean = hasAccount(player)

    override fun getBalance(playerName: String): Double {
        val uuid = id(playerName)
        val currency = cur()
        if (uuid == null || currency == null) {
            return 0.0
        }
        return eco().get(uuid, currency).toDouble()
    }

    override fun getBalance(
        playerName: String,
        world: String,
    ): Double = getBalance(playerName)

    override fun getBalance(player: OfflinePlayer?): Double {
        val currency = cur()
        if (player == null || currency == null) {
            return 0.0
        }
        eco().ensureAccount(player.uniqueId, player.name)
        return eco().get(player.uniqueId, currency).toDouble()
    }

    override fun getBalance(
        player: OfflinePlayer?,
        world: String,
    ): Double = getBalance(player)

    override fun has(
        playerName: String,
        amount: Double,
    ): Boolean {
        val uuid = id(playerName)
        val currency = cur()
        if (uuid == null || currency == null) {
            return false
        }
        return eco().has(uuid, currency, BigDecimal.valueOf(amount))
    }

    override fun has(
        playerName: String,
        worldName: String,
        amount: Double,
    ): Boolean = has(playerName, amount)

    override fun has(
        player: OfflinePlayer?,
        amount: Double,
    ): Boolean {
        val currency = cur()
        if (player == null || currency == null) {
            return false
        }
        return eco().has(player.uniqueId, currency, BigDecimal.valueOf(amount))
    }

    override fun has(
        player: OfflinePlayer?,
        worldName: String,
        amount: Double,
    ): Boolean = has(player, amount)

    override fun withdrawPlayer(
        playerName: String,
        amount: Double,
    ): EconomyResponse {
        val uuid =
            id(playerName)
                ?: return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "Unknown player")
        return withdraw(uuid, amount)
    }

    override fun withdrawPlayer(
        playerName: String,
        worldName: String,
        amount: Double,
    ): EconomyResponse = withdrawPlayer(playerName, amount)

    override fun withdrawPlayer(
        player: OfflinePlayer?,
        amount: Double,
    ): EconomyResponse {
        if (player == null) {
            return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "Unknown player")
        }
        return withdraw(player.uniqueId, amount)
    }

    override fun withdrawPlayer(
        player: OfflinePlayer?,
        worldName: String,
        amount: Double,
    ): EconomyResponse = withdrawPlayer(player, amount)

    private fun withdraw(
        uuid: UUID,
        amount: Double,
    ): EconomyResponse {
        val currency = cur() ?: return fail("No default currency")
        if (!amount.isFinite() || amount <= 0) {
            return fail("Amount must be finite and positive")
        }
        val value = currency.normalize(BigDecimal.valueOf(amount))
        if (value <= BigDecimal.ZERO) {
            return fail("Amount is below the minimum currency precision")
        }
        val result = eco().takeResult(uuid, currency, value, bank = false)
        if (!result.success) {
            return EconomyResponse(
                0.0,
                (result.from ?: eco().get(uuid, currency)).toDouble(),
                EconomyResponse.ResponseType.FAILURE,
                if (result.status == io.github.tamawish.pureeconomy.api.EconomyStatus.INSUFFICIENT) {
                    "Insufficient funds"
                } else {
                    result.status.name
                },
            )
        }
        return EconomyResponse(
            value.toDouble(),
            (result.from ?: value).toDouble(),
            EconomyResponse.ResponseType.SUCCESS,
            null,
        )
    }

    override fun depositPlayer(
        playerName: String,
        amount: Double,
    ): EconomyResponse {
        val uuid = id(playerName) ?: return fail("Unknown player")
        return deposit(uuid, amount)
    }

    override fun depositPlayer(
        playerName: String,
        worldName: String,
        amount: Double,
    ): EconomyResponse = depositPlayer(playerName, amount)

    override fun depositPlayer(
        player: OfflinePlayer?,
        amount: Double,
    ): EconomyResponse {
        if (player == null) {
            return fail("Unknown player")
        }
        return deposit(player.uniqueId, amount)
    }

    override fun depositPlayer(
        player: OfflinePlayer?,
        worldName: String,
        amount: Double,
    ): EconomyResponse = depositPlayer(player, amount)

    private fun deposit(
        uuid: UUID,
        amount: Double,
    ): EconomyResponse {
        val currency = cur() ?: return fail("No default currency")
        if (!amount.isFinite() || amount <= 0) {
            return fail("Amount must be finite and positive")
        }
        val value = currency.normalize(BigDecimal.valueOf(amount))
        if (value <= BigDecimal.ZERO) {
            return fail("Amount is below the minimum currency precision")
        }
        val result = eco().addResult(uuid, currency, value, bank = false)
        if (!result.success) {
            return fail("Max balance")
        }
        return EconomyResponse(
            value.toDouble(),
            (result.from ?: value).toDouble(),
            EconomyResponse.ResponseType.SUCCESS,
            null,
        )
    }

    override fun createBank(
        name: String,
        player: String,
    ): EconomyResponse = noBank()

    override fun createBank(
        name: String,
        player: OfflinePlayer,
    ): EconomyResponse = noBank()

    override fun deleteBank(name: String): EconomyResponse = noBank()

    override fun bankBalance(name: String): EconomyResponse = noBank()

    override fun bankHas(
        name: String,
        amount: Double,
    ): EconomyResponse = noBank()

    override fun bankWithdraw(
        name: String,
        amount: Double,
    ): EconomyResponse = noBank()

    override fun bankDeposit(
        name: String,
        amount: Double,
    ): EconomyResponse = noBank()

    override fun isBankOwner(
        name: String,
        playerName: String,
    ): EconomyResponse = noBank()

    override fun isBankOwner(
        name: String,
        player: OfflinePlayer,
    ): EconomyResponse = noBank()

    override fun isBankMember(
        name: String,
        playerName: String,
    ): EconomyResponse = noBank()

    override fun isBankMember(
        name: String,
        player: OfflinePlayer,
    ): EconomyResponse = noBank()

    override fun getBanks(): List<String> = emptyList()

    override fun createPlayerAccount(playerName: String): Boolean {
        val uuid = id(playerName) ?: return false
        eco().ensureAccount(uuid, playerName)
        return true
    }

    override fun createPlayerAccount(
        playerName: String,
        worldName: String,
    ): Boolean = createPlayerAccount(playerName)

    override fun createPlayerAccount(player: OfflinePlayer?): Boolean {
        if (player == null) {
            return false
        }
        eco().ensureAccount(player.uniqueId, player.name)
        return true
    }

    override fun createPlayerAccount(
        player: OfflinePlayer?,
        worldName: String,
    ): Boolean = createPlayerAccount(player)

    companion object {
        private fun noBank(): EconomyResponse = EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks")

        private fun fail(reason: String): EconomyResponse = EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, reason)
    }
}

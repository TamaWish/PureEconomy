package io.github.tamawish.pureeconomy.api.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.math.BigDecimal
import java.util.UUID

/**
 * Fired after a successful wallet or bank mutation. Not cancellable — Vault and commands have
 * already applied the change.
 *
 * Listeners run on the **global region** (Folia) or the **main thread** (Paper). Hop to the
 * player entity thread with [io.github.tamawish.pureeconomy.util.Schedulers.runAtEntity] before
 * touching entity state.
 */
class EconomyTransactionEvent(
    val playerId: UUID,
    val otherPlayerId: UUID?,
    val currencyId: String,
    val amount: BigDecimal,
    val bank: Boolean,
    val cause: Cause,
    val newBalance: BigDecimal,
) : Event() {
    enum class Cause {
        SET,
        DEPOSIT,
        WITHDRAW,
        PAY,
        BANK_MOVE,
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}

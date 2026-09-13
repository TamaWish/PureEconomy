package io.github.tamawish.pureeconomy.api

import io.github.tamawish.pureeconomy.economy.Currency
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurrencyMetadataTest {
    @Test
    fun currencyExposesSymbolDecimalsAndPayable() {
        val currency =
            Currency(
                "coins",
                "Coin",
                "Coins",
                "$",
                2,
                BigDecimal.TEN,
                BigDecimal("-1"),
                true,
            )
        assertEquals("$", currency.symbol())
        assertEquals(2, currency.decimals())
        assertTrue(currency.payable())
        assertEquals("$10.00", currency.format(BigDecimal.TEN))
    }
}

package io.github.tamawish.pureeconomy.lang

import io.github.tamawish.pureeconomy.PureEconomy
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LangTest {
    @Test
    fun parseKeepsGradientTagsForAdventure() {
        val component = Lang.parse(PureEconomy.BRAND_PAPER)
        assertEquals(
            "Eco",
            PlainTextComponentSerializer.plainText().serialize(component),
        )
        val legacy = Lang.color("<green><bold>Eco</bold>")
        assertTrue(legacy.contains("Eco"))
        assertTrue(legacy.contains("&") || legacy.contains("\u00A7"))
    }

    @Test
    fun colorDoesNotLeakGradientHexIntoBukkitStrings() {
        val legacy = Lang.color(PureEconomy.DEFAULT_PREFIX_PAPER + "<green>Balance updated.")
        val visible = legacy.replace(Regex("[&\u00A7]."), "")
        assertTrue(visible.contains("Eco"))
        assertTrue(visible.contains("Balance updated."))
        assertTrue(!legacy.contains("C8FF7A", ignoreCase = true))
        assertTrue(!legacy.contains("3DDC84", ignoreCase = true))
        assertTrue(!legacy.contains("&x") && !legacy.contains("\u00A7x"))
    }

    @Test
    fun parseAcceptsLegacyAmpersand() {
        val component = Lang.parse("&aEco")
        assertEquals("Eco", PlainTextComponentSerializer.plainText().serialize(component))
    }
}

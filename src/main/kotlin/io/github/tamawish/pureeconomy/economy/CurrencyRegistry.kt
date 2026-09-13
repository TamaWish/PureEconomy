package io.github.tamawish.pureeconomy.economy

import io.github.tamawish.pureeconomy.config.PluginSettings
import java.math.BigDecimal
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import java.util.regex.Pattern

/** Loaded currency definitions from `config.yml`. */
class CurrencyRegistry(
    private val settings: PluginSettings,
    private val logger: Logger,
) {
    private val currencies: MutableMap<String, Currency> = ConcurrentHashMap()

    @Volatile
    private var sortedIds: List<String> = emptyList()

    @Volatile
    var defaultId: String = "coins"
        private set

    fun load() {
        currencies.clear()
        sortedIds = emptyList()
        defaultId =
            (settings.getString("default-currency", "coins") ?: "coins").lowercase(Locale.ROOT)
        val section = settings.getSection("currencies")
        if (section == null) {
            logger.warning("No currencies defined in config.yml")
            return
        }
        for (key in section.getRoutesAsStrings(false)) {
            if (!section.isSection(key)) {
                continue
            }
            val currencySection = section.getSection(key)
            val id = key.lowercase(Locale.ROOT)
            if (!CURRENCY_ID.matcher(id).matches()) {
                logger.warning(
                    "Ignoring invalid currency ID '$key' (use lowercase letters, numbers, and underscores only).",
                )
                continue
            }
            currencies[id] =
                Currency(
                    id,
                    currencySection.getString("singular", id),
                    currencySection.getString("plural", id),
                    currencySection.getString("symbol", ""),
                    currencySection.getInt("decimals", 2),
                    bd(currencySection.getString("starting-balance", "0")),
                    bd(currencySection.getString("max-balance", "-1")),
                    currencySection.getBoolean("payable", true),
                )
        }
        val ids = ArrayList(currencies.keys)
        ids.sort()
        sortedIds = Collections.unmodifiableList(ids)
        if (!currencies.containsKey(defaultId) && currencies.isNotEmpty()) {
            defaultId = currencies.keys.iterator().next()
            logger.warning("default-currency missing; using $defaultId")
        }
    }

    fun hasCurrencies(): Boolean = currencies.isNotEmpty()

    fun currency(id: String?): Currency? {
        if (id.isNullOrBlank()) {
            return defaultCurrency()
        }
        return currencies[id.lowercase(Locale.ROOT)]
    }

    fun defaultCurrency(): Currency? = currencies[defaultId]

    fun currencyIds(): List<String> = sortedIds

    fun currencies(): Map<String, Currency> = Collections.unmodifiableMap(currencies)

    companion object {
        private val CURRENCY_ID: Pattern = Pattern.compile("[a-z0-9_]+")

        private fun bd(raw: String?): BigDecimal =
            try {
                BigDecimal(raw!!.trim())
            } catch (_: Exception) {
                BigDecimal.ZERO
            }
    }
}

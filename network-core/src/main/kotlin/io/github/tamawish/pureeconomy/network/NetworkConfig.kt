package io.github.tamawish.pureeconomy.network

import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.math.BigDecimal
import java.security.MessageDigest

data class NetworkConfig(
    val mysql: Mysql,
    val defaultCurrency: String,
    val payMinimum: BigDecimal,
    val currencies: Map<String, NetworkCurrency>,
    val messages: Map<String, String>,
) {
    data class Mysql(
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val password: String,
        val poolSize: Int,
        val connectionTimeoutMs: Long,
        val queryTimeoutSeconds: Int,
        val deadlockRetries: Int,
    )

    val fingerprint: String by lazy {
        val canonical = buildString {
            append("default=").append(defaultCurrency).append('\n')
            currencies.toSortedMap().forEach { (id, currency) ->
                append(id).append('|').append(currency.singular).append('|').append(currency.plural)
                    .append('|').append(currency.symbol).append('|').append(currency.decimals)
                    .append('|').append(currency.starting.toPlainString()).append('|')
                    .append(currency.maximum?.toPlainString() ?: "-1").append('|')
                    .append(currency.payable).append('\n')
            }
        }
        MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun load(input: InputStream): NetworkConfig {
            val root = Yaml().load<Map<String, Any?>>(input) ?: emptyMap()
            fun section(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: emptyMap()
            val mysql = section(section(root["storage"])["mysql"])
            val currencies = section(root["currencies"]).mapValues { (id, raw) ->
                val value = section(raw)
                val max = BigDecimal(value["max-balance"].toString())
                NetworkCurrency(
                    id.lowercase(), value["singular"]?.toString() ?: id,
                    value["plural"]?.toString() ?: id, value["symbol"]?.toString() ?: "",
                    (value["decimals"] as? Number)?.toInt() ?: 2,
                    BigDecimal(value["starting-balance"]?.toString() ?: "0"),
                    max.takeUnless { it.signum() < 0 }, value["payable"] as? Boolean ?: true,
                )
            }
            require(currencies.isNotEmpty()) { "At least one currency must be configured" }
            val defaultCurrency = root["default-currency"]?.toString()?.lowercase() ?: "coins"
            require(currencies.containsKey(defaultCurrency)) { "default-currency '$defaultCurrency' is not defined" }
            return NetworkConfig(
                Mysql(
                    mysql["host"]?.toString() ?: "localhost", (mysql["port"] as? Number)?.toInt() ?: 3306,
                    mysql["database"]?.toString() ?: "pureeconomy", mysql["username"]?.toString() ?: "root",
                    mysql["password"]?.toString() ?: "", (mysql["pool-size"] as? Number)?.toInt() ?: 8,
                    (mysql["connection-timeout-ms"] as? Number)?.toLong() ?: 5000,
                    (mysql["query-timeout-seconds"] as? Number)?.toInt() ?: 5,
                    (mysql["deadlock-retries"] as? Number)?.toInt() ?: 3,
                ), defaultCurrency, BigDecimal(root["pay-minimum"]?.toString() ?: "0.01"), currencies,
                section(root["messages"]).mapValues { it.value.toString() },
            )
        }
    }
}

data class NetworkCurrency(
    val id: String,
    val singular: String,
    val plural: String,
    val symbol: String,
    val decimals: Int,
    val starting: BigDecimal,
    val maximum: BigDecimal?,
    val payable: Boolean,
) {
    fun normalize(value: BigDecimal): BigDecimal = value.setScale(decimals, java.math.RoundingMode.DOWN)
    fun format(value: BigDecimal): String = symbol + normalize(value).toPlainString()
}

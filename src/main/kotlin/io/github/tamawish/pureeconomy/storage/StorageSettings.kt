package io.github.tamawish.pureeconomy.storage

import io.github.tamawish.pureeconomy.config.PluginSettings
import java.util.Locale

/** Parsed `storage` / `storage.cache` block from `config.yml`. */
data class StorageSettings(
    val type: Type,
    val sqliteFile: String,
    val mysqlHost: String,
    val mysqlPort: Int,
    val mysqlDatabase: String,
    val mysqlUsername: String,
    val mysqlPassword: String,
    val poolSize: Int,
    val maxAccounts: Long,
    val expireMinutes: Long,
    val baltopTtlSeconds: Long,
    val networkTtlMillis: Long,
) {
    enum class Type {
        YAML,
        SQLITE,
        MYSQL,
    }

    companion object {
        fun from(settings: PluginSettings): StorageSettings {
            val raw = (settings.getString("storage.type", "yaml") ?: "yaml").lowercase(Locale.ROOT)
            val type =
                when (raw) {
                    "sqlite" -> Type.SQLITE
                    "mysql" -> Type.MYSQL
                    else -> Type.YAML
                }
            return StorageSettings(
                type = type,
                sqliteFile = settings.getString("storage.sqlite.file", "economy.db") ?: "economy.db",
                mysqlHost = settings.getString("storage.mysql.host", "localhost") ?: "localhost",
                mysqlPort = settings.getInt("storage.mysql.port", 3306),
                mysqlDatabase = settings.getString("storage.mysql.database", "pureeconomy") ?: "pureeconomy",
                mysqlUsername = settings.getString("storage.mysql.username", "root") ?: "root",
                mysqlPassword = settings.getString("storage.mysql.password", "") ?: "",
                poolSize = maxOf(1, settings.getInt("storage.mysql.pool-size", 8)),
                maxAccounts = maxOf(64L, settings.getInt("storage.cache.max-accounts", 2000).toLong()),
                expireMinutes = maxOf(1L, settings.getInt("storage.cache.expire-minutes", 30).toLong()),
                baltopTtlSeconds = maxOf(1L, settings.getInt("storage.cache.baltop-ttl-seconds", 15).toLong()),
                networkTtlMillis =
                    settings.getInt("storage.cache.network-ttl-millis", 250).toLong().coerceIn(100L, 500L),
            )
        }
    }
}

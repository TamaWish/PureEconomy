package io.github.tamawish.pureeconomy.storage

import io.github.tamawish.pureeconomy.PureEconomy
import java.io.File

/** Creates the configured [PlayerStorage] and runs YAML→SQL import when the SQL store is empty. */
object StorageFactory {
    fun create(
        plugin: PureEconomy,
        settings: StorageSettings,
    ): PlayerStorage {
        val yamlFile = File(plugin.dataFolder, "data.yml")
        return when (settings.type) {
            StorageSettings.Type.YAML -> {
                plugin.logger.info("Economy storage: YAML ($yamlFile)")
                YamlStorage(yamlFile, plugin.logger)
            }
            StorageSettings.Type.SQLITE -> {
                val file = File(plugin.dataFolder, settings.sqliteFile)
                plugin.logger.info("Economy storage: SQLite (${file.absolutePath})")
                val sql = SqlStorage(HikariFactory.sqlite(file, "pureeconomy-sqlite"), plugin.logger, mysql = false)
                YamlToSqlMigrator.migrateIfEmpty(yamlFile, sql, plugin.logger)
                sql
            }
            StorageSettings.Type.MYSQL -> {
                plugin.logger.info("Economy storage: MySQL (${settings.mysqlHost}:${settings.mysqlPort}/${settings.mysqlDatabase})")
                val sql = SqlStorage(HikariFactory.mysql(settings, "pureeconomy-mysql"), plugin.logger, mysql = true)
                YamlToSqlMigrator.migrateIfEmpty(yamlFile, sql, plugin.logger)
                sql
            }
        }
    }
}

package io.github.tamawish.pureeconomy.config

import dev.dejvokep.boostedyaml.YamlDocument
import dev.dejvokep.boostedyaml.block.implementation.Section
import io.github.tamawish.pureeconomy.PureEconomy
import java.io.File
import java.util.logging.Level

/** Facade over the plugin's BoostedYAML `config.yml` document. */
class PluginSettings(
    private val plugin: PureEconomy,
) {
    private var document: YamlDocument? = null

    /** Loads or reloads `config.yml`, merging missing jar keys and keeping custom keys. */
    fun reload() {
        val folder = plugin.dataFolder
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val file = File(folder, "config.yml")
        val stream =
            plugin.getResource("config.yml")
                ?: error("Missing jar resource config.yml")
        stream.use { jarStream ->
            document = CommentedYaml.load(file, jarStream)
        }
    }

    /** Returns the live document; must call [reload] first. */
    fun document(): YamlDocument = document ?: error("PluginSettings not loaded — call reload() first")

    fun getString(
        path: String,
        def: String? = null,
    ): String? = document().getString(path, def)

    /** Returns the stored value at [path], or null when absent. */
    fun get(path: String): Any? = document().getOptional(path).orElse(null)

    fun getBoolean(
        path: String,
        def: Boolean = false,
    ): Boolean = document().getBoolean(path, def)

    fun getLong(
        path: String,
        def: Long = 0L,
    ): Long = document().getLong(path, def)

    fun getInt(
        path: String,
        def: Int = 0,
    ): Int = document().getInt(path, def)

    fun contains(path: String): Boolean = document().contains(path)

    /** Returns a section at [path], or null when absent. */
    fun getSection(path: String): Section? =
        if (document().contains(path) && document().isSection(path)) {
            document().getSection(path)
        } else {
            null
        }

    /** Reloads from disk; logs and keeps the previous document on failure. */
    fun reloadSafe() {
        try {
            document()?.reload() ?: reload()
        } catch (exception: Exception) {
            plugin.logger.log(Level.SEVERE, "Could not reload config.yml", exception)
        }
    }
}

package io.github.tamawish.pureeconomy.lang

import dev.dejvokep.boostedyaml.YamlDocument
import io.github.tamawish.pureeconomy.PureEconomy
import io.github.tamawish.pureeconomy.config.CommentedYaml
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.CommandSender
import java.io.File
import java.util.LinkedHashMap
import java.util.logging.Level

/** Loads language YAML files and sends Adventure [Component] messages. */
class Lang(
    private val plugin: PureEconomy,
) {
    private var yaml: YamlDocument? = null

    init {
        reload()
    }

    /**
     * Reloads the language file from disk using `language` in config.yml.
     *
     * Uses BoostedYAML with jar `lang/en.yml` (or jar locale if present) as defaults so missing keys
     * and comments are written on save.
     */
    fun reload() {
        val folder = File(plugin.dataFolder, "lang")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        var code = plugin.settings().getString("language", "en")
        if (code.isNullOrBlank()) {
            code = "en"
        }
        val preferred = File(folder, "$code.yml")
        val file: File
        val defaultsResource: String
        if (preferred.exists()) {
            file = preferred
            defaultsResource =
                if (plugin.getResource("lang/$code.yml") != null) {
                    "lang/$code.yml"
                } else {
                    "lang/en.yml"
                }
        } else {
            val english = File(folder, "en.yml")
            file = english
            defaultsResource = "lang/en.yml"
            if (code != "en") {
                plugin.logger.warning("Missing lang/$code.yml — using en.yml")
            }
        }
        val stream =
            plugin.getResource(defaultsResource)
                ?: plugin.getResource("lang/en.yml")
                ?: error("Missing jar resource lang/en.yml")
        try {
            stream.use { jarStream ->
                yaml = CommentedYaml.load(file, jarStream)
            }
        } catch (exception: Exception) {
            plugin.logger.log(Level.SEVERE, "Could not load language file ${file.name}", exception)
            throw exception
        }
    }

    /**
     * Returns the raw template string for a key without color translation.
     *
     * @param key language key under the loaded YAML file
     * @return template text, or [key] when missing
     */
    fun raw(key: String): String {
        val msg = yaml?.getString(key)
        return msg ?: key
    }

    /**
     * Builds a legacy-colored string after substituting placeholders.
     *
     * @param key language key under the loaded YAML file
     * @param placeholders optional name → value map; may be `null`
     * @return colored legacy string suitable for APIs that still need plain text
     */
    fun get(
        key: String,
        placeholders: Map<String, String>?,
    ): String = color(applyPlaceholders(key, placeholders))

    /**
     * Builds an Adventure component after substituting placeholders.
     *
     * Prefers MiniMessage; falls back to legacy `&` codes for older lang files.
     *
     * @param key language key under the loaded YAML file
     * @param placeholders optional name → value map; may be `null`
     * @return message component ready for [net.kyori.adventure.audience.Audience.sendMessage]
     */
    fun component(
        key: String,
        placeholders: Map<String, String>?,
    ): Component = parse(applyPlaceholders(key, placeholders))

    /**
     * Sends a language key with no placeholders to the sender.
     *
     * @param sender recipient of the message
     * @param key language key under the loaded YAML file
     */
    fun send(
        sender: CommandSender,
        key: String,
    ) {
        send(sender, key, null)
    }

    /**
     * Sends a language key with placeholders to the sender.
     *
     * @param sender recipient of the message
     * @param key language key under the loaded YAML file
     * @param placeholders name → value map used for `{name}` tokens
     */
    fun send(
        sender: CommandSender,
        key: String,
        placeholders: Map<String, String>?,
    ) {
        if (plugin.isEnabled) {
            sender.sendMessage(component(key, placeholders))
        }
    }

    private fun applyPlaceholders(
        key: String,
        placeholders: Map<String, String>?,
    ): String {
        var msg =
            raw(key)
                .replace("{brand}", plugin.brandEco())
                .replace("{prefix}", plugin.resolvedPrefix())
        if (placeholders != null) {
            for ((name, value) in placeholders) {
                msg = msg.replace("{$name}", value)
            }
        }
        return msg
    }

    companion object {
        private val AMPERSAND = LegacyComponentSerializer.legacyAmpersand()

        // Named colors only — hexColors() emits `&x` leftover digits in Bukkit string APIs.
        private val AMPERSAND_NO_HEX =
            LegacyComponentSerializer
                .builder()
                .character('&')
                .build()
        private val MINI = MiniMessage.miniMessage()

        /**
         * Translates MiniMessage or `&` color codes into a legacy-colored string.
         *
         * Gradients flatten to named colors so hex digits do not leak into string APIs.
         *
         * @param text text that may contain MiniMessage tags or `&` codes; may be `null`
         * @return colored string, or empty when [text] is `null`
         */
        @JvmStatic
        fun color(text: String?): String {
            if (text == null) {
                return ""
            }
            return AMPERSAND_NO_HEX.serialize(parse(text))
        }

        /**
         * Deserializes MiniMessage or `&`-coded text into an Adventure component.
         *
         * @param text text that may contain MiniMessage tags or `&` codes; may be `null`
         * @return adventure component, or empty when [text] is `null`
         */
        @JvmStatic
        fun colorComponent(text: String?): Component {
            if (text == null) {
                return Component.empty()
            }
            return parse(text)
        }

        /**
         * Parses a message template: MiniMessage first, legacy `&` when the string looks legacy or
         * MiniMessage fails.
         *
         * @param text template text; may be `null`
         * @return parsed component
         */
        @JvmStatic
        fun parse(text: String?): Component {
            if (text.isNullOrEmpty()) {
                return Component.empty()
            }
            if (looksLegacy(text)) {
                return AMPERSAND.deserialize(text)
            }
            return try {
                MINI.deserialize(text)
            } catch (_: Exception) {
                AMPERSAND.deserialize(text)
            }
        }

        private fun looksLegacy(text: String): Boolean = text.indexOf('&') >= 0 && text.indexOf('<') < 0

        /**
         * Builds a placeholder map from alternating key/value pairs.
         *
         * @param kv alternating keys and values; odd trailing entries are ignored
         * @return ordered map of placeholders
         */
        @JvmStatic
        fun of(vararg kv: String): Map<String, String> {
            val map = LinkedHashMap<String, String>()
            var i = 0
            while (i + 1 < kv.size) {
                map[kv[i]] = kv[i + 1]
                i += 2
            }
            return map
        }
    }
}

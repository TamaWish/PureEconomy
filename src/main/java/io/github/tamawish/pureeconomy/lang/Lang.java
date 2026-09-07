package io.github.tamawish.pureeconomy.lang;

import io.github.tamawish.pureeconomy.PureEconomy;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

/** Loads language YAML files and sends Adventure {@link Component} messages. */
public final class Lang {

  private static final LegacyComponentSerializer AMPERSAND =
      LegacyComponentSerializer.legacyAmpersand();

  private final PureEconomy plugin;
  private YamlConfiguration yaml;

  /**
   * Creates a language manager and loads the configured locale file.
   *
   * @param plugin owning plugin instance
   */
  public Lang(PureEconomy plugin) {
    this.plugin = plugin;
    reload();
  }

  /** Reloads the language file from disk using {@code language} in config.yml. */
  public void reload() {
    File folder = new File(plugin.getDataFolder(), "lang");
    if (!folder.exists()) {
      folder.mkdirs();
    }
    String code = plugin.getConfig().getString("language", "en");
    if (code == null || code.isBlank()) {
      code = "en";
    }
    File file = new File(folder, code + ".yml");
    if (!file.exists()) {
      File english = new File(folder, "en.yml");
      if (!english.exists()) {
        plugin.saveResource("lang/en.yml", false);
      }
      file = english;
      if (!"en".equals(code)) {
        plugin.getLogger().warning("Missing lang/" + code + ".yml — using en.yml");
      }
    }
    yaml = YamlConfiguration.loadConfiguration(file);
    InputStream def = plugin.getResource("lang/en.yml");
    if (def != null) {
      yaml.setDefaults(
          YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
    }
  }

  /**
   * Returns the raw template string for a key without color translation.
   *
   * @param key language key under the loaded YAML file
   * @return template text, or {@code key} when missing
   */
  public String raw(String key) {
    String msg = yaml.getString(key);
    return msg != null ? msg : key;
  }

  /**
   * Builds a legacy-colored string after substituting placeholders.
   *
   * @param key language key under the loaded YAML file
   * @param placeholders optional {@code name} → value map; may be {@code null}
   * @return colored legacy string suitable for APIs that still need plain text
   */
  public String get(String key, Map<String, String> placeholders) {
    return color(applyPlaceholders(key, placeholders));
  }

  /**
   * Builds an Adventure component after substituting placeholders.
   *
   * @param key language key under the loaded YAML file
   * @param placeholders optional {@code name} → value map; may be {@code null}
   * @return message component ready for {@link net.kyori.adventure.audience.Audience#sendMessage}
   */
  public Component component(String key, Map<String, String> placeholders) {
    return AMPERSAND.deserialize(applyPlaceholders(key, placeholders));
  }

  /**
   * Sends a language key with no placeholders to the sender.
   *
   * @param sender recipient of the message
   * @param key language key under the loaded YAML file
   */
  public void send(CommandSender sender, String key) {
    send(sender, key, null);
  }

  /**
   * Sends a language key with placeholders to the sender.
   *
   * @param sender recipient of the message
   * @param key language key under the loaded YAML file
   * @param placeholders {@code name} → value map used for {@code {name}} tokens
   */
  public void send(CommandSender sender, String key, Map<String, String> placeholders) {
    if (plugin.adventure() == null) {
      return;
    }
    plugin.adventure().sender(sender).sendMessage(component(key, placeholders));
  }

  /**
   * Translates {@code &} color codes into a legacy-colored string.
   *
   * @param text text that may contain {@code &} codes; may be {@code null}
   * @return colored string, or empty when {@code text} is {@code null}
   */
  public static String color(String text) {
    if (text == null) {
      return "";
    }
    return AMPERSAND.serialize(AMPERSAND.deserialize(text));
  }

  /**
   * Deserializes {@code &}-coded text into an Adventure component.
   *
   * @param text text that may contain {@code &} codes; may be {@code null}
   * @return adventure component, or empty when {@code text} is {@code null}
   */
  public static Component colorComponent(String text) {
    if (text == null) {
      return Component.empty();
    }
    return AMPERSAND.deserialize(text);
  }

  /**
   * Builds a placeholder map from alternating key/value pairs.
   *
   * @param kv alternating keys and values; odd trailing entries are ignored
   * @return ordered map of placeholders
   */
  public static Map<String, String> of(String... kv) {
    LinkedHashMap<String, String> map = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      map.put(kv[i], kv[i + 1]);
    }
    return map;
  }

  private String applyPlaceholders(String key, Map<String, String> placeholders) {
    String msg = raw(key).replace("{prefix}", raw("prefix"));
    if (placeholders != null) {
      for (Map.Entry<String, String> entry : placeholders.entrySet()) {
        msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
      }
    }
    return msg;
  }
}

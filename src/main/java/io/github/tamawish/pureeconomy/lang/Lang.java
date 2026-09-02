package io.github.tamawish.pureeconomy.lang;

import io.github.tamawish.pureeconomy.PureEconomy;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Lang {

    private final PureEconomy plugin;
    private YamlConfiguration yaml;

    public Lang(PureEconomy plugin) {
        this.plugin = plugin;
        reload();
    }

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
            yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
        }
    }

    public String raw(String key) {
        String msg = yaml.getString(key);
        return msg != null ? msg : key;
    }

    public String get(String key, Map<String, String> placeholders) {
        String msg = raw(key);
        String prefix = color(raw("prefix"));
        msg = msg.replace("{prefix}", prefix);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                msg = msg.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return color(msg);
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(get(key, null));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(get(key, placeholders));
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static Map<String, String> of(String... kv) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }
}

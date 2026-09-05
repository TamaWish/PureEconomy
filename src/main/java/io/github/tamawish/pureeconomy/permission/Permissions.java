package io.github.tamawish.pureeconomy.permission;

import io.github.tamawish.pureeconomy.PureEconomy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

public final class Permissions {

    public enum Node {
        BALANCE("balance", "pureeconomy.balance", PermissionDefault.TRUE),
        BALANCE_OTHERS("balance-others", "pureeconomy.balance.others", PermissionDefault.OP),
        BANK("bank", "pureeconomy.bank", PermissionDefault.TRUE),
        BANK_TRANSFER("bank-transfer", "pureeconomy.bank.transfer", PermissionDefault.TRUE),
        BANK_WITHDRAW("bank-withdraw", "pureeconomy.bank.withdraw", PermissionDefault.TRUE),
        PAY("pay", "pureeconomy.pay", PermissionDefault.TRUE),
        BALTOP("baltop", "pureeconomy.baltop", PermissionDefault.TRUE),
        CURRENCY("currency", "pureeconomy.currency", PermissionDefault.TRUE),
        ECO("eco", "pureeconomy.eco", PermissionDefault.OP),
        ECO_GIVE("eco-give", "pureeconomy.eco.give", PermissionDefault.OP),
        ECO_TAKE("eco-take", "pureeconomy.eco.take", PermissionDefault.OP),
        ECO_SET("eco-set", "pureeconomy.eco.set", PermissionDefault.OP),
        ECO_RESET("eco-reset", "pureeconomy.eco.reset", PermissionDefault.OP),
        ECO_RELOAD("eco-reload", "pureeconomy.eco.reload", PermissionDefault.OP),
        ECO_BANK("eco-bank", "pureeconomy.eco.bank", PermissionDefault.OP),
        ECO_BANK_GIVE("eco-bank-give", "pureeconomy.eco.bank.give", PermissionDefault.OP),
        ECO_BANK_TAKE("eco-bank-take", "pureeconomy.eco.bank.take", PermissionDefault.OP),
        ECO_BANK_SET("eco-bank-set", "pureeconomy.eco.bank.set", PermissionDefault.OP),
        ECO_BANK_RESET("eco-bank-reset", "pureeconomy.eco.bank.reset", PermissionDefault.OP),
        ADMIN("admin", "pureeconomy.admin", PermissionDefault.OP);

        private final String configKey;
        private final String fallbackNode;
        private final PermissionDefault fallbackDefault;

        Node(String configKey, String fallbackNode, PermissionDefault fallbackDefault) {
            this.configKey = configKey;
            this.fallbackNode = fallbackNode;
            this.fallbackDefault = fallbackDefault;
        }
    }

    private final PureEconomy plugin;

    public Permissions(PureEconomy plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        if (plugin.getConfig().getConfigurationSection("permissions") == null) {
            plugin.getLogger().info("No permissions: section in config.yml — using built-in defaults. "
                    + "Copy that section from the jar's config.yml to change nodes or who has them.");
        }
        for (Node node : Node.values()) {
            String name = name(node);
            PermissionDefault def = defaultOf(node);
            Permission existing = Bukkit.getPluginManager().getPermission(name);
            if (existing == null) {
                Bukkit.getPluginManager().addPermission(new Permission(name, def));
            } else {
                existing.setDefault(def);
            }
        }
    }

    public String name(Node node) {
        String value = plugin.getConfig().getString("permissions." + node.configKey + ".node", node.fallbackNode);
        if (value == null || value.isBlank()) {
            return node.fallbackNode;
        }
        return value.trim();
    }

    public boolean has(CommandSender sender, Node node) {
        return sender.hasPermission(name(node));
    }

    private PermissionDefault defaultOf(Node node) {
        String raw = plugin.getConfig().getString("permissions." + node.configKey + ".default", "");
        return parseDefault(raw, node.fallbackDefault);
    }

    static PermissionDefault parseDefault(String raw, PermissionDefault fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase()) {
            case "everyone", "true", "all", "yes" -> PermissionDefault.TRUE;
            case "op", "operator", "ops" -> PermissionDefault.OP;
            case "nobody", "false", "none", "no" -> PermissionDefault.FALSE;
            default -> fallback;
        };
    }
}

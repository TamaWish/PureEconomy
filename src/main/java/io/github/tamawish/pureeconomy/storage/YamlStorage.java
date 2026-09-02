package io.github.tamawish.pureeconomy.storage;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.PlayerAccount;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class YamlStorage {

    private final PureEconomy plugin;
    private final File file;
    private final YamlConfiguration yaml = new YamlConfiguration();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final Set<UUID> known = ConcurrentHashMap.newKeySet();

    public YamlStorage(PureEconomy plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        loadIndex();
    }

    private synchronized void loadIndex() {
        if (!file.exists()) {
            return;
        }
        try {
            yaml.load(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load data.yml", e);
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                known.add(uuid);
                String name = root.getString(key + ".name");
                if (name != null) {
                    nameIndex.put(name.toLowerCase(Locale.ROOT), uuid);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public PlayerAccount load(UUID uuid) {
        synchronized (this) {
            ConfigurationSection section = yaml.getConfigurationSection("players." + uuid);
            if (section == null) {
                return null;
            }
            PlayerAccount acc = new PlayerAccount(uuid);
            acc.setName(section.getString("name"));
            ConfigurationSection bals = section.getConfigurationSection("balances");
            if (bals != null) {
                for (String id : bals.getKeys(false)) {
                    String path = "players." + uuid + ".balances." + id;
                    acc.set(id.toLowerCase(Locale.ROOT), parseBalance(path, bals.getString(id, "0")));
                }
            }
            ConfigurationSection bankBals = section.getConfigurationSection("bank-balances");
            if (bankBals != null) {
                for (String id : bankBals.getKeys(false)) {
                    String path = "players." + uuid + ".bank-balances." + id;
                    acc.setBank(id.toLowerCase(Locale.ROOT), parseBalance(path, bankBals.getString(id, "0")));
                }
            }
            acc.markClean();
            known.add(uuid);
            if (acc.name() != null) {
                nameIndex.put(acc.name().toLowerCase(Locale.ROOT), uuid);
            }
            return acc;
        }
    }

    public void save(PlayerAccount acc) {
        saveAll(Collections.singleton(acc));
    }

    public void saveAll(Collection<PlayerAccount> accounts) {
        Map<PlayerAccount, PlayerAccount.Snapshot> snapshots = new HashMap<>();
        for (PlayerAccount account : accounts) {
            snapshots.put(account, account.snapshot());
        }

        boolean saved;
        synchronized (this) {
            for (PlayerAccount.Snapshot snapshot : snapshots.values()) {
                applySnapshot(snapshot);
            }
            saved = writeYaml();
        }
        if (saved) {
            snapshots.forEach((account, snapshot) -> account.markClean(snapshot.revision()));
        }
    }

    private void applySnapshot(PlayerAccount.Snapshot snapshot) {
        String path = "players." + snapshot.uuid();
        if (snapshot.name() != null) {
            yaml.set(path + ".name", snapshot.name());
            nameIndex.put(snapshot.name().toLowerCase(Locale.ROOT), snapshot.uuid());
        }
        for (Map.Entry<String, BigDecimal> e : snapshot.balances().entrySet()) {
            yaml.set(path + ".balances." + e.getKey(), e.getValue().toPlainString());
        }
        for (Map.Entry<String, BigDecimal> e : snapshot.bankBalances().entrySet()) {
            yaml.set(path + ".bank-balances." + e.getKey(), e.getValue().toPlainString());
        }
        known.add(snapshot.uuid());
    }

    private boolean writeYaml() {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            yaml.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", e);
            if (temp.exists() && !temp.delete()) {
                plugin.getLogger().warning("Could not delete temporary data file: " + temp.getName());
            }
            return false;
        }
    }

    private BigDecimal parseBalance(String path, String raw) {
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid balance at " + path + ": " + raw + " — using 0");
            return BigDecimal.ZERO;
        }
    }

    public UUID uuidByName(String name) {
        return nameIndex.get(name.toLowerCase(Locale.ROOT));
    }

    public Set<UUID> allKnownUuids() {
        return Collections.unmodifiableSet(known);
    }
}

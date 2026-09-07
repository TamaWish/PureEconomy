package io.github.tamawish.pureeconomy.storage;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.PlayerAccount;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** YAML-backed persistence for player wallets and banks. */
public final class YamlStorage {

  private final PureEconomy plugin;
  private final File file;
  private final YamlConfiguration yaml = new YamlConfiguration();
  private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
  private final Set<UUID> known = ConcurrentHashMap.newKeySet();

  /**
   * Creates storage bound to {@code data.yml} under the plugin data folder.
   *
   * @param plugin owning plugin
   */
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
        // Skip corrupt UUID keys.
      }
    }
  }

  /**
   * Loads one player account from the in-memory YAML document.
   *
   * @param uuid player unique id
   * @return loaded account, or {@code null} when absent
   */
  public PlayerAccount load(UUID uuid) {
    synchronized (this) {
      ConfigurationSection section = yaml.getConfigurationSection("players." + uuid);
      if (section == null) {
        return null;
      }
      PlayerAccount account = new PlayerAccount(uuid);
      account.setName(section.getString("name"));
      ConfigurationSection balances = section.getConfigurationSection("balances");
      if (balances != null) {
        for (String id : balances.getKeys(false)) {
          String path = "players." + uuid + ".balances." + id;
          account.set(id.toLowerCase(Locale.ROOT), parseBalance(path, balances.getString(id, "0")));
        }
      }
      ConfigurationSection bankBalances = section.getConfigurationSection("bank-balances");
      if (bankBalances != null) {
        for (String id : bankBalances.getKeys(false)) {
          String path = "players." + uuid + ".bank-balances." + id;
          account.setBank(
              id.toLowerCase(Locale.ROOT), parseBalance(path, bankBalances.getString(id, "0")));
        }
      }
      account.markClean();
      known.add(uuid);
      if (account.name() != null) {
        nameIndex.put(account.name().toLowerCase(Locale.ROOT), uuid);
      }
      return account;
    }
  }

  /**
   * Persists a single account.
   *
   * @param account account to write
   */
  public void save(PlayerAccount account) {
    saveAll(Collections.singleton(account));
  }

  /**
   * Snapshots accounts then writes them atomically to {@code data.yml}.
   *
   * @param accounts accounts to persist
   */
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
    for (Map.Entry<String, BigDecimal> entry : snapshot.balances().entrySet()) {
      yaml.set(path + ".balances." + entry.getKey(), entry.getValue().toPlainString());
    }
    for (Map.Entry<String, BigDecimal> entry : snapshot.bankBalances().entrySet()) {
      yaml.set(path + ".bank-balances." + entry.getKey(), entry.getValue().toPlainString());
    }
    known.add(snapshot.uuid());
  }

  private boolean writeYaml() {
    File temp = new File(file.getParentFile(), file.getName() + ".tmp");
    try {
      yaml.save(temp);
      try {
        Files.move(
            temp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
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

  /**
   * Looks up a UUID from the lowercase name index.
   *
   * @param name player name
   * @return matching UUID, or {@code null} when unknown
   */
  public UUID uuidByName(String name) {
    return nameIndex.get(name.toLowerCase(Locale.ROOT));
  }

  /**
   * Returns every UUID that has appeared in storage or memory.
   *
   * @return unmodifiable set of known player ids
   */
  public Set<UUID> allKnownUuids() {
    return Collections.unmodifiableSet(known);
  }
}

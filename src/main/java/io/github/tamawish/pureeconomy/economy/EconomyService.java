package io.github.tamawish.pureeconomy.economy;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.storage.YamlStorage;
import io.github.tamawish.pureeconomy.util.Schedulers;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** In-memory multi-currency economy service backed by {@link YamlStorage}. */
public final class EconomyService {

  private static final Pattern CURRENCY_ID = Pattern.compile("[a-z0-9_]+");

  private final PureEconomy plugin;
  private final YamlStorage storage;
  private final Map<String, Currency> currencies = new ConcurrentHashMap<>();
  private final Map<UUID, PlayerAccount> accounts = new ConcurrentHashMap<>();
  private String defaultId = "coins";

  /**
   * Creates the economy service.
   *
   * @param plugin owning plugin
   * @param storage YAML persistence layer
   */
  public EconomyService(PureEconomy plugin, YamlStorage storage) {
    this.plugin = plugin;
    this.storage = storage;
  }

  /** Reloads currency definitions from {@code config.yml}. */
  public void loadCurrencies() {
    currencies.clear();
    defaultId = plugin.getConfig().getString("default-currency", "coins").toLowerCase(Locale.ROOT);
    ConfigurationSection section = plugin.getConfig().getConfigurationSection("currencies");
    if (section == null) {
      plugin.getLogger().warning("No currencies defined in config.yml");
      return;
    }
    for (String key : section.getKeys(false)) {
      ConfigurationSection currencySection = section.getConfigurationSection(key);
      if (currencySection == null) {
        continue;
      }
      String id = key.toLowerCase(Locale.ROOT);
      if (!CURRENCY_ID.matcher(id).matches()) {
        plugin
            .getLogger()
            .warning(
                "Ignoring invalid currency ID '"
                    + key
                    + "' (use lowercase letters, numbers, and underscores only).");
        continue;
      }
      BigDecimal start = bd(currencySection.getString("starting-balance", "0"));
      BigDecimal max = bd(currencySection.getString("max-balance", "-1"));
      Currency currency =
          new Currency(
              id,
              currencySection.getString("singular", id),
              currencySection.getString("plural", id),
              currencySection.getString("symbol", ""),
              currencySection.getInt("decimals", 2),
              start,
              max,
              currencySection.getBoolean("payable", true));
      currencies.put(id, currency);
    }
    if (!currencies.containsKey(defaultId) && !currencies.isEmpty()) {
      defaultId = currencies.keySet().iterator().next();
      plugin.getLogger().warning("default-currency missing; using " + defaultId);
    }
  }

  /**
   * Returns whether at least one currency is loaded.
   *
   * @return {@code true} when currencies were configured successfully
   */
  public boolean hasCurrencies() {
    return !currencies.isEmpty();
  }

  /**
   * Resolves a currency by id, falling back to the default when blank.
   *
   * @param id currency id; blank selects the default
   * @return matching currency, or {@code null} when unknown
   */
  public Currency currency(String id) {
    if (id == null || id.isBlank()) {
      return defaultCurrency();
    }
    return currencies.get(id.toLowerCase(Locale.ROOT));
  }

  /**
   * Returns the configured default currency.
   *
   * @return default currency, or {@code null} when none are loaded
   */
  public Currency defaultCurrency() {
    return currencies.get(defaultId);
  }

  /**
   * Returns the default currency id.
   *
   * @return lowercase default id
   */
  public String defaultId() {
    return defaultId;
  }

  /**
   * Returns sorted currency ids.
   *
   * @return mutable sorted copy of currency ids
   */
  public List<String> currencyIds() {
    List<String> ids = new ArrayList<>(currencies.keySet());
    Collections.sort(ids);
    return ids;
  }

  /**
   * Returns an unmodifiable view of loaded currencies.
   *
   * @return currencies keyed by id
   */
  public Map<String, Currency> currencies() {
    return Collections.unmodifiableMap(currencies);
  }

  /**
   * Loads or creates the cached account for a player.
   *
   * @param uuid player unique id
   * @return in-memory account
   */
  public PlayerAccount account(UUID uuid) {
    return accounts.computeIfAbsent(
        uuid,
        id -> {
          PlayerAccount loaded = storage.load(id);
          if (loaded == null) {
            loaded = new PlayerAccount(id);
          }
          seedStarting(loaded);
          return loaded;
        });
  }

  /**
   * Ensures an account exists and records the latest player name.
   *
   * @param uuid player unique id
   * @param name latest known name; may be {@code null}
   */
  public void ensureAccount(UUID uuid, String name) {
    PlayerAccount account = account(uuid);
    synchronized (account) {
      if (name != null) {
        account.setName(name);
      }
      seedStarting(account);
    }
  }

  private void seedStarting(PlayerAccount account) {
    for (Currency currency : currencies.values()) {
      if (!account.has(currency.id())) {
        account.set(currency.id(), currency.starting());
      }
    }
  }

  /**
   * Returns a player's wallet balance for a currency.
   *
   * @param uuid player unique id
   * @param currency currency definition
   * @return normalized wallet balance
   */
  public BigDecimal get(UUID uuid, Currency currency) {
    PlayerAccount account = account(uuid);
    synchronized (account) {
      return currency.normalize(account.get(currency.id()));
    }
  }

  /**
   * Returns whether the wallet holds at least {@code amount}.
   *
   * @param uuid player unique id
   * @param currency currency definition
   * @param amount required amount
   * @return {@code true} when the wallet balance is sufficient
   */
  public boolean has(UUID uuid, Currency currency, BigDecimal amount) {
    return get(uuid, currency).compareTo(currency.normalize(amount)) >= 0;
  }

  /**
   * Sets a wallet balance.
   *
   * @param uuid player unique id
   * @param currency currency definition
   * @param raw desired amount before normalization
   * @return {@code false} when the amount would exceed the currency maximum
   */
  public boolean set(UUID uuid, Currency currency, BigDecimal raw) {
    return setBalance(uuid, currency, raw, false);
  }

  /**
   * Adds to a wallet balance.
   *
   * @param uuid player unique id
   * @param currency currency definition
   * @param raw amount to add before normalization
   * @return {@code false} when the result would exceed the currency maximum
   */
  public boolean add(UUID uuid, Currency currency, BigDecimal raw) {
    return addBalance(uuid, currency, raw, false);
  }

  /**
   * Removes from a wallet balance.
   *
   * @param uuid player unique id
   * @param currency currency definition
   * @param raw amount to remove before normalization
   * @return {@code false} when funds are insufficient
   */
  public boolean take(UUID uuid, Currency currency, BigDecimal raw) {
    return takeBalance(uuid, currency, raw, false);
  }

  /**
   * Returns a player's bank balance for a currency.
   *
   * @param uuid player unique id
   * @param currency currency definition
   * @return normalized bank balance
   */
  public BigDecimal getBank(UUID uuid, Currency currency) {
    PlayerAccount account = account(uuid);
    synchronized (account) {
      return currency.normalize(account.getBank(currency.id()));
    }
  }

  /**
   * Sets a bank balance.
   *
   * @param uuid player unique id
   * @param currency currency definition
   * @param raw desired amount before normalization
   * @return {@code false} when the amount is negative or exceeds the maximum
   */
  public boolean setBank(UUID uuid, Currency currency, BigDecimal raw) {
    return setBalance(uuid, currency, raw, true);
  }

  /**
   * Adds to a bank balance.
   *
   * @param uuid player unique id
   * @param currency currency definition
   * @param raw amount to add before normalization
   * @return {@code false} when the result would exceed the currency maximum
   */
  public boolean addBank(UUID uuid, Currency currency, BigDecimal raw) {
    return addBalance(uuid, currency, raw, true);
  }

  /**
   * Removes from a bank balance.
   *
   * @param uuid player unique id
   * @param currency currency definition
   * @param raw amount to remove before normalization
   * @return {@code false} when funds are insufficient
   */
  public boolean takeBank(UUID uuid, Currency currency, BigDecimal raw) {
    return takeBalance(uuid, currency, raw, true);
  }

  /**
   * Clears a bank balance to zero.
   *
   * @param uuid player unique id
   * @param currency currency definition
   */
  public void resetBank(UUID uuid, Currency currency) {
    setBank(uuid, currency, BigDecimal.ZERO);
  }

  /**
   * Moves funds from wallet to bank atomically.
   *
   * @param uuid player unique id
   * @param currency currency definition
   * @param raw amount to move before normalization
   * @return {@code false} when the wallet lacks funds or the bank would exceed its maximum
   */
  public boolean transferToBank(UUID uuid, Currency currency, BigDecimal raw) {
    BigDecimal amount = currency.normalize(raw);
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      return false;
    }
    PlayerAccount account = account(uuid);
    synchronized (account) {
      BigDecimal wallet = currency.normalize(account.get(currency.id()));
      BigDecimal bank = currency.normalize(account.getBank(currency.id()));
      BigDecimal bankNext = bank.add(amount);
      if (wallet.compareTo(amount) < 0 || currency.exceedsMax(bankNext)) {
        return false;
      }
      account.set(currency.id(), wallet.subtract(amount));
      account.setBank(currency.id(), bankNext);
      return true;
    }
  }

  /**
   * Moves funds from bank to wallet atomically.
   *
   * @param uuid player unique id
   * @param currency currency definition
   * @param raw amount to move before normalization
   * @return {@code false} when the bank lacks funds or the wallet would exceed its maximum
   */
  public boolean withdrawFromBank(UUID uuid, Currency currency, BigDecimal raw) {
    BigDecimal amount = currency.normalize(raw);
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      return false;
    }
    PlayerAccount account = account(uuid);
    synchronized (account) {
      BigDecimal wallet = currency.normalize(account.get(currency.id()));
      BigDecimal bank = currency.normalize(account.getBank(currency.id()));
      BigDecimal walletNext = wallet.add(amount);
      if (bank.compareTo(amount) < 0 || currency.exceedsMax(walletNext)) {
        return false;
      }
      account.setBank(currency.id(), bank.subtract(amount));
      account.set(currency.id(), walletNext);
      return true;
    }
  }

  /**
   * Moves funds between two player wallets atomically.
   *
   * @param from sender unique id
   * @param to recipient unique id
   * @param currency currency definition
   * @param raw amount to move before normalization
   * @return {@code false} when the currency is not payable, funds are missing, or the max is hit
   */
  public boolean transfer(UUID from, UUID to, Currency currency, BigDecimal raw) {
    BigDecimal amount = currency.normalize(raw);
    if (amount.compareTo(BigDecimal.ZERO) <= 0 || !currency.payable()) {
      return false;
    }
    return withAccounts(
        from,
        to,
        () -> {
          PlayerAccount fromAccount = account(from);
          PlayerAccount toAccount = account(to);
          BigDecimal fromBalance = currency.normalize(fromAccount.get(currency.id()));
          if (fromBalance.compareTo(amount) < 0) {
            return false;
          }
          BigDecimal targetNext = currency.normalize(toAccount.get(currency.id())).add(amount);
          if (currency.exceedsMax(targetNext)) {
            return false;
          }
          fromAccount.set(currency.id(), fromBalance.subtract(amount));
          toAccount.set(currency.id(), targetNext);
          return true;
        });
  }

  /**
   * Resets a wallet to the currency starting balance without changing the bank.
   *
   * @param uuid player unique id
   * @param currency currency definition
   */
  public void reset(UUID uuid, Currency currency) {
    set(uuid, currency, currency.starting());
  }

  /**
   * Returns one page of richest wallets for a currency.
   *
   * @param currency currency definition
   * @param page one-based page number
   * @param pageSize entries per page
   * @return page slice, possibly empty
   */
  public List<BalanceEntry> top(Currency currency, int page, int pageSize) {
    List<BalanceEntry> list = new ArrayList<>();
    for (UUID uuid : storage.allKnownUuids()) {
      PlayerAccount account = account(uuid);
      BigDecimal balance;
      String name;
      synchronized (account) {
        balance = currency.normalize(account.get(currency.id()));
        name = account.name();
      }
      if (balance.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }
      if (name == null) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        name = offline.getName() != null ? offline.getName() : uuid.toString();
      }
      list.add(new BalanceEntry(uuid, name, balance));
    }
    list.sort(Comparator.comparing(BalanceEntry::balance).reversed());
    int from = Math.max(0, (page - 1) * pageSize);
    if (from >= list.size()) {
      return Collections.emptyList();
    }
    int to = Math.min(list.size(), from + pageSize);
    return list.subList(from, to);
  }

  /**
   * Returns how many baltop pages exist for a currency.
   *
   * @param currency currency definition
   * @param pageSize entries per page
   * @return page count, always at least one
   */
  public int topPages(Currency currency, int pageSize) {
    int count = 0;
    for (UUID uuid : storage.allKnownUuids()) {
      PlayerAccount account = account(uuid);
      synchronized (account) {
        if (account.get(currency.id()).compareTo(BigDecimal.ZERO) > 0) {
          count++;
        }
      }
    }
    return Math.max(1, (int) Math.ceil(count / (double) pageSize));
  }

  /**
   * Writes one cached account to disk on the calling thread.
   *
   * @param uuid player unique id
   */
  public void savePlayer(UUID uuid) {
    PlayerAccount account = accounts.get(uuid);
    if (account != null) {
      storage.save(account);
    }
  }

  /**
   * Writes one cached account to disk asynchronously.
   *
   * @param uuid player unique id
   */
  public void savePlayerAsync(UUID uuid) {
    PlayerAccount account = accounts.get(uuid);
    if (account == null) {
      return;
    }
    Schedulers.runAsync(plugin, () -> storage.save(account));
  }

  /** Collects dirty accounts and writes them asynchronously. */
  public void saveDirtyAsync() {
    List<PlayerAccount> dirty = collectDirty();
    if (!dirty.isEmpty()) {
      Schedulers.runAsync(plugin, () -> storage.saveAll(dirty));
    }
  }

  /** Writes every cached account synchronously. Intended for plugin disable. */
  public void saveAll() {
    if (!accounts.isEmpty()) {
      storage.saveAll(accounts.values());
    }
  }

  /**
   * Resolves a player name to a UUID using online players, storage, then offline lookup.
   *
   * @param name player name
   * @return unique id, or {@code null} when unknown
   */
  public UUID resolve(String name) {
    Player online = Bukkit.getPlayerExact(name);
    if (online != null) {
      ensureAccount(online.getUniqueId(), online.getName());
      return online.getUniqueId();
    }
    UUID fromStore = storage.uuidByName(name);
    if (fromStore != null) {
      return fromStore;
    }
    OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
    if (offline.hasPlayedBefore() || offline.isOnline()) {
      ensureAccount(offline.getUniqueId(), offline.getName() != null ? offline.getName() : name);
      return offline.getUniqueId();
    }
    return null;
  }

  /**
   * Returns the best known display name for a player id.
   *
   * @param uuid player unique id
   * @return cached name, offline name, or the UUID string
   */
  public String nameOf(UUID uuid) {
    PlayerAccount account = accounts.get(uuid);
    if (account != null && account.name() != null) {
      return account.name();
    }
    OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
    return offline.getName() != null ? offline.getName() : uuid.toString();
  }

  private List<PlayerAccount> collectDirty() {
    List<PlayerAccount> dirty = new ArrayList<>();
    for (PlayerAccount account : accounts.values()) {
      if (account.dirty()) {
        dirty.add(account);
      }
    }
    return dirty;
  }

  private boolean setBalance(UUID uuid, Currency currency, BigDecimal raw, boolean bank) {
    BigDecimal amount = currency.normalize(raw);
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      amount = BigDecimal.ZERO;
    }
    if (currency.exceedsMax(amount)) {
      return false;
    }
    PlayerAccount account = account(uuid);
    synchronized (account) {
      if (bank) {
        account.setBank(currency.id(), amount);
      } else {
        account.set(currency.id(), amount);
      }
    }
    return true;
  }

  private boolean addBalance(UUID uuid, Currency currency, BigDecimal raw, boolean bank) {
    PlayerAccount account = account(uuid);
    synchronized (account) {
      BigDecimal current =
          currency.normalize(bank ? account.getBank(currency.id()) : account.get(currency.id()));
      BigDecimal next = current.add(currency.normalize(raw));
      if (next.compareTo(BigDecimal.ZERO) < 0) {
        next = BigDecimal.ZERO;
      }
      if (currency.exceedsMax(next)) {
        return false;
      }
      if (bank) {
        account.setBank(currency.id(), next);
      } else {
        account.set(currency.id(), next);
      }
    }
    return true;
  }

  private boolean takeBalance(UUID uuid, Currency currency, BigDecimal raw, boolean bank) {
    BigDecimal amount = currency.normalize(raw);
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      return false;
    }
    PlayerAccount account = account(uuid);
    synchronized (account) {
      BigDecimal current =
          currency.normalize(bank ? account.getBank(currency.id()) : account.get(currency.id()));
      if (current.compareTo(amount) < 0) {
        return false;
      }
      BigDecimal next = current.subtract(amount);
      if (bank) {
        account.setBank(currency.id(), next);
      } else {
        account.set(currency.id(), next);
      }
    }
    return true;
  }

  private boolean withAccounts(UUID first, UUID second, Supplier<Boolean> action) {
    PlayerAccount firstAccount = account(first);
    if (first.equals(second)) {
      synchronized (firstAccount) {
        return action.get();
      }
    }
    PlayerAccount secondAccount = account(second);
    if (first.compareTo(second) < 0) {
      synchronized (firstAccount) {
        synchronized (secondAccount) {
          return action.get();
        }
      }
    }
    synchronized (secondAccount) {
      synchronized (firstAccount) {
        return action.get();
      }
    }
  }

  private static BigDecimal bd(String raw) {
    try {
      return new BigDecimal(raw.trim());
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  /**
   * One baltop row.
   *
   * @param uuid player unique id
   * @param name display name
   * @param balance wallet balance used for ranking
   */
  public record BalanceEntry(UUID uuid, String name, BigDecimal balance) {}
}

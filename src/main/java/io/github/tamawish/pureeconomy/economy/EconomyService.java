package io.github.tamawish.pureeconomy.economy;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.storage.YamlStorage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;

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

public final class EconomyService {

    private final PureEconomy plugin;
    private final YamlStorage storage;
    private final Map<String, Currency> currencies = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerAccount> accounts = new ConcurrentHashMap<>();
    private String defaultId = "coins";

    public EconomyService(PureEconomy plugin, YamlStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void loadCurrencies() {
        currencies.clear();
        defaultId = plugin.getConfig().getString("default-currency", "coins").toLowerCase(Locale.ROOT);
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("currencies");
        if (section == null) {
            plugin.getLogger().warning("No currencies defined in config.yml");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection c = section.getConfigurationSection(key);
            if (c == null) {
                continue;
            }
            String id = key.toLowerCase(Locale.ROOT);
            BigDecimal start = bd(c.getString("starting-balance", "0"));
            BigDecimal max = bd(c.getString("max-balance", "-1"));
            Currency currency = new Currency(
                    id,
                    c.getString("singular", id),
                    c.getString("plural", id),
                    c.getString("symbol", ""),
                    c.getInt("decimals", 2),
                    start,
                    max,
                    c.getBoolean("payable", true)
            );
            currencies.put(id, currency);
        }
        if (!currencies.containsKey(defaultId) && !currencies.isEmpty()) {
            defaultId = currencies.keySet().iterator().next();
            plugin.getLogger().warning("default-currency missing; using " + defaultId);
        }
    }

    public boolean hasCurrencies() {
        return !currencies.isEmpty();
    }

    public Currency currency(String id) {
        if (id == null || id.isBlank()) {
            return defaultCurrency();
        }
        return currencies.get(id.toLowerCase(Locale.ROOT));
    }

    public Currency defaultCurrency() {
        return currencies.get(defaultId);
    }

    public String defaultId() {
        return defaultId;
    }

    public List<String> currencyIds() {
        List<String> ids = new ArrayList<>(currencies.keySet());
        Collections.sort(ids);
        return ids;
    }

    public Map<String, Currency> currencies() {
        return Collections.unmodifiableMap(currencies);
    }

    public PlayerAccount account(UUID uuid) {
        return accounts.computeIfAbsent(uuid, id -> {
            PlayerAccount loaded = storage.load(id);
            if (loaded == null) {
                loaded = new PlayerAccount(id);
            }
            seedStarting(loaded);
            return loaded;
        });
    }

    public void ensureAccount(UUID uuid, String name) {
        PlayerAccount acc = account(uuid);
        synchronized (acc) {
            if (name != null) {
                acc.setName(name);
            }
            seedStarting(acc);
        }
    }

    private void seedStarting(PlayerAccount acc) {
        for (Currency currency : currencies.values()) {
            if (!acc.has(currency.id())) {
                acc.set(currency.id(), currency.starting());
            }
        }
    }

    public BigDecimal get(UUID uuid, Currency currency) {
        PlayerAccount acc = account(uuid);
        synchronized (acc) {
            return currency.normalize(acc.get(currency.id()));
        }
    }

    public boolean has(UUID uuid, Currency currency, BigDecimal amount) {
        return get(uuid, currency).compareTo(currency.normalize(amount)) >= 0;
    }

    public boolean set(UUID uuid, Currency currency, BigDecimal raw) {
        BigDecimal amount = currency.normalize(raw);
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            amount = BigDecimal.ZERO;
        }
        if (currency.exceedsMax(amount)) {
            return false;
        }
        PlayerAccount acc = account(uuid);
        synchronized (acc) {
            acc.set(currency.id(), amount);
        }
        return true;
    }

    public boolean add(UUID uuid, Currency currency, BigDecimal raw) {
        PlayerAccount acc = account(uuid);
        synchronized (acc) {
            BigDecimal next = currency.normalize(acc.get(currency.id())).add(currency.normalize(raw));
            if (next.compareTo(BigDecimal.ZERO) < 0) {
                next = BigDecimal.ZERO;
            }
            if (currency.exceedsMax(next)) {
                return false;
            }
            acc.set(currency.id(), next);
        }
        return true;
    }

    public boolean take(UUID uuid, Currency currency, BigDecimal raw) {
        BigDecimal amount = currency.normalize(raw);
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        PlayerAccount acc = account(uuid);
        synchronized (acc) {
            BigDecimal current = currency.normalize(acc.get(currency.id()));
            if (current.compareTo(amount) < 0) {
                return false;
            }
            acc.set(currency.id(), current.subtract(amount));
        }
        return true;
    }

    public BigDecimal getBank(UUID uuid, Currency currency) {
        PlayerAccount acc = account(uuid);
        synchronized (acc) {
            return currency.normalize(acc.getBank(currency.id()));
        }
    }

    public boolean setBank(UUID uuid, Currency currency, BigDecimal raw) {
        BigDecimal amount = currency.normalize(raw);
        if (amount.compareTo(BigDecimal.ZERO) < 0 || currency.exceedsMax(amount)) {
            return false;
        }
        PlayerAccount acc = account(uuid);
        synchronized (acc) {
            acc.setBank(currency.id(), amount);
        }
        return true;
    }

    public boolean transferToBank(UUID uuid, Currency currency, BigDecimal raw) {
        BigDecimal amount = currency.normalize(raw);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        PlayerAccount acc = account(uuid);
        synchronized (acc) {
            BigDecimal wallet = currency.normalize(acc.get(currency.id()));
            BigDecimal bank = currency.normalize(acc.getBank(currency.id()));
            BigDecimal bankNext = bank.add(amount);
            if (wallet.compareTo(amount) < 0 || currency.exceedsMax(bankNext)) {
                return false;
            }
            acc.set(currency.id(), wallet.subtract(amount));
            acc.setBank(currency.id(), bankNext);
            return true;
        }
    }

    public boolean withdrawFromBank(UUID uuid, Currency currency, BigDecimal raw) {
        BigDecimal amount = currency.normalize(raw);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        PlayerAccount acc = account(uuid);
        synchronized (acc) {
            BigDecimal wallet = currency.normalize(acc.get(currency.id()));
            BigDecimal bank = currency.normalize(acc.getBank(currency.id()));
            BigDecimal walletNext = wallet.add(amount);
            if (bank.compareTo(amount) < 0 || currency.exceedsMax(walletNext)) {
                return false;
            }
            acc.setBank(currency.id(), bank.subtract(amount));
            acc.set(currency.id(), walletNext);
            return true;
        }
    }

    public boolean transfer(UUID from, UUID to, Currency currency, BigDecimal raw) {
        BigDecimal amount = currency.normalize(raw);
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || !currency.payable()) {
            return false;
        }
        return withAccounts(from, to, () -> {
            PlayerAccount fromAcc = account(from);
            PlayerAccount toAcc = account(to);
            BigDecimal fromBal = currency.normalize(fromAcc.get(currency.id()));
            if (fromBal.compareTo(amount) < 0) {
                return false;
            }
            BigDecimal targetNext = currency.normalize(toAcc.get(currency.id())).add(amount);
            if (currency.exceedsMax(targetNext)) {
                return false;
            }
            fromAcc.set(currency.id(), fromBal.subtract(amount));
            toAcc.set(currency.id(), targetNext);
            return true;
        });
    }

    public void reset(UUID uuid, Currency currency) {
        set(uuid, currency, currency.starting());
    }

    public List<BalanceEntry> top(Currency currency, int page, int pageSize) {
        List<BalanceEntry> list = new ArrayList<>();
        for (UUID uuid : storage.allKnownUuids()) {
            PlayerAccount acc = account(uuid);
            BigDecimal bal;
            String name;
            synchronized (acc) {
                bal = currency.normalize(acc.get(currency.id()));
                name = acc.name();
            }
            if (bal.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (name == null) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
                name = off.getName() != null ? off.getName() : uuid.toString();
            }
            list.add(new BalanceEntry(uuid, name, bal));
        }
        list.sort(Comparator.comparing((BalanceEntry e) -> e.balance()).reversed());
        int from = Math.max(0, (page - 1) * pageSize);
        if (from >= list.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(list.size(), from + pageSize);
        return list.subList(from, to);
    }

    public int topPages(Currency currency, int pageSize) {
        int count = 0;
        for (UUID uuid : storage.allKnownUuids()) {
            PlayerAccount acc = account(uuid);
            synchronized (acc) {
                if (acc.get(currency.id()).compareTo(BigDecimal.ZERO) > 0) {
                    count++;
                }
            }
        }
        return Math.max(1, (int) Math.ceil(count / (double) pageSize));
    }

    public void savePlayer(UUID uuid) {
        PlayerAccount acc = accounts.get(uuid);
        if (acc != null) {
            storage.save(acc);
        }
    }

    public void saveDirty() {
        List<PlayerAccount> dirty = new ArrayList<>();
        for (PlayerAccount acc : accounts.values()) {
            if (acc.dirty()) {
                dirty.add(acc);
            }
        }
        if (!dirty.isEmpty()) {
            storage.saveAll(dirty);
        }
    }

    public void saveAll() {
        if (!accounts.isEmpty()) {
            storage.saveAll(accounts.values());
        }
    }

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
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        if (off.hasPlayedBefore() || off.isOnline()) {
            ensureAccount(off.getUniqueId(), off.getName() != null ? off.getName() : name);
            return off.getUniqueId();
        }
        return null;
    }

    public String nameOf(UUID uuid) {
        PlayerAccount acc = accounts.get(uuid);
        if (acc != null && acc.name() != null) {
            return acc.name();
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        return off.getName() != null ? off.getName() : uuid.toString();
    }

    private boolean withAccounts(UUID first, UUID second, Supplier<Boolean> action) {
        PlayerAccount acc1 = account(first);
        if (first.equals(second)) {
            synchronized (acc1) {
                return action.get();
            }
        }
        PlayerAccount acc2 = account(second);
        if (first.compareTo(second) < 0) {
            synchronized (acc1) {
                synchronized (acc2) {
                    return action.get();
                }
            }
        }
        synchronized (acc2) {
            synchronized (acc1) {
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

    public record BalanceEntry(UUID uuid, String name, BigDecimal balance) {
    }
}

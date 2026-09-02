package io.github.tamawish.pureeconomy.economy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerAccount {

    private final UUID uuid;
    private volatile String name;
    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> bankBalances = new ConcurrentHashMap<>();
    private volatile boolean dirty;
    private long revision;

    public PlayerAccount(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public synchronized void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            this.dirty = true;
            this.revision++;
        }
    }

    public boolean has(String currencyId) {
        return balances.containsKey(currencyId);
    }

    public BigDecimal get(String currencyId) {
        return balances.getOrDefault(currencyId, BigDecimal.ZERO);
    }

    public synchronized void set(String currencyId, BigDecimal amount) {
        balances.put(currencyId, amount);
        dirty = true;
        revision++;
    }

    public Map<String, BigDecimal> balances() {
        return balances;
    }

    public BigDecimal getBank(String currencyId) {
        return bankBalances.getOrDefault(currencyId, BigDecimal.ZERO);
    }

    public synchronized void setBank(String currencyId, BigDecimal amount) {
        bankBalances.put(currencyId, amount);
        dirty = true;
        revision++;
    }

    public Map<String, BigDecimal> bankBalances() {
        return bankBalances;
    }

    public boolean dirty() {
        return dirty;
    }

    public synchronized void markClean() {
        dirty = false;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(uuid, name, new HashMap<>(balances), new HashMap<>(bankBalances), revision);
    }

    public synchronized void markClean(long savedRevision) {
        if (revision == savedRevision) {
            dirty = false;
        }
    }

    public record Snapshot(UUID uuid, String name, Map<String, BigDecimal> balances,
                           Map<String, BigDecimal> bankBalances, long revision) {
    }
}

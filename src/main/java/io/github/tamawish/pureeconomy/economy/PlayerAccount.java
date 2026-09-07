package io.github.tamawish.pureeconomy.economy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory wallet and bank balances for one player. */
public final class PlayerAccount {

  private final UUID uuid;
  private volatile String name;
  private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
  private final Map<String, BigDecimal> bankBalances = new ConcurrentHashMap<>();
  private volatile boolean dirty;
  private long revision;

  /**
   * Creates an empty account for the given player id.
   *
   * @param uuid player unique id
   */
  public PlayerAccount(UUID uuid) {
    this.uuid = uuid;
  }

  /**
   * Returns the account owner id.
   *
   * @return player unique id
   */
  public UUID uuid() {
    return uuid;
  }

  /**
   * Returns the last known player name.
   *
   * @return cached name, or {@code null} when unknown
   */
  public String name() {
    return name;
  }

  /**
   * Updates the cached player name when it changes.
   *
   * @param name latest player name; ignored when {@code null}
   */
  public synchronized void setName(String name) {
    if (name != null && !name.equals(this.name)) {
      this.name = name;
      this.dirty = true;
      this.revision++;
    }
  }

  /**
   * Returns whether a wallet entry exists for the currency.
   *
   * @param currencyId currency id
   * @return {@code true} when the wallet map contains the id
   */
  public boolean has(String currencyId) {
    return balances.containsKey(currencyId);
  }

  /**
   * Returns the wallet balance for a currency.
   *
   * @param currencyId currency id
   * @return wallet balance, or zero when missing
   */
  public BigDecimal get(String currencyId) {
    return balances.getOrDefault(currencyId, BigDecimal.ZERO);
  }

  /**
   * Sets the wallet balance for a currency and marks the account dirty.
   *
   * @param currencyId currency id
   * @param amount new wallet balance
   */
  public synchronized void set(String currencyId, BigDecimal amount) {
    balances.put(currencyId, amount);
    dirty = true;
    revision++;
  }

  /**
   * Returns the live wallet map. Callers must not mutate it outside account methods.
   *
   * @return wallet balances by currency id
   */
  public Map<String, BigDecimal> balances() {
    return balances;
  }

  /**
   * Returns the bank balance for a currency.
   *
   * @param currencyId currency id
   * @return bank balance, or zero when missing
   */
  public BigDecimal getBank(String currencyId) {
    return bankBalances.getOrDefault(currencyId, BigDecimal.ZERO);
  }

  /**
   * Sets the bank balance for a currency and marks the account dirty.
   *
   * @param currencyId currency id
   * @param amount new bank balance
   */
  public synchronized void setBank(String currencyId, BigDecimal amount) {
    bankBalances.put(currencyId, amount);
    dirty = true;
    revision++;
  }

  /**
   * Returns the live bank map. Callers must not mutate it outside account methods.
   *
   * @return bank balances by currency id
   */
  public Map<String, BigDecimal> bankBalances() {
    return bankBalances;
  }

  /**
   * Returns whether the account has unsaved changes.
   *
   * @return {@code true} when a flush is needed
   */
  public boolean dirty() {
    return dirty;
  }

  /** Clears the dirty flag without checking the revision. */
  public synchronized void markClean() {
    dirty = false;
  }

  /**
   * Captures an immutable copy of the account for asynchronous persistence.
   *
   * @return snapshot of name, balances, and revision
   */
  public synchronized Snapshot snapshot() {
    return new Snapshot(uuid, name, new HashMap<>(balances), new HashMap<>(bankBalances), revision);
  }

  /**
   * Clears the dirty flag only when no writes happened after {@code savedRevision}.
   *
   * @param savedRevision revision that was successfully written to disk
   */
  public synchronized void markClean(long savedRevision) {
    if (revision == savedRevision) {
      dirty = false;
    }
  }

  /**
   * Immutable copy of an account used while writing YAML off-thread.
   *
   * @param uuid player unique id
   * @param name cached name; may be {@code null}
   * @param balances wallet copy
   * @param bankBalances bank copy
   * @param revision revision at snapshot time
   */
  public record Snapshot(
      UUID uuid,
      String name,
      Map<String, BigDecimal> balances,
      Map<String, BigDecimal> bankBalances,
      long revision) {}
}

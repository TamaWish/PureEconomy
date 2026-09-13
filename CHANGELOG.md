# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased](https://github.com/TamaWish/PureEconomy/compare/v1.2.0...HEAD)

## [1.2.0](https://github.com/TamaWish/PureEconomy/compare/v1.1.0...v1.2.0) - 2026-09-13

### Added

- Paper `libraries:` loader for Kotlin stdlib, HikariCP, Caffeine, SQLite, and MySQL Connector/J (downloaded once into `libraries/`)
- `EconomyResult` / `EconomyStatus` plus additive `PureEconomyAPI.depositResult`, `withdrawResult`, and `transferResult` (Boolean methods unchanged)
- Short-TTL, stale-while-refresh Caffeine cache in front of network MySQL reads (`storage.cache.network-ttl-millis`, 100–500ms)
- SQL schema v2: `DECIMAL(20,8)` amounts, ranking indexes, automatic VARCHAR → DECIMAL migration
- Native Velocity and BungeeCord/Waterfall plugins with network-wide player and administration commands
- Platform-neutral network core with authoritative MySQL transactions, deterministic row locking, deadlock retries, and bounded query timeouts
- Proxy-owned currency configuration fingerprints; mismatched backends fail closed instead of applying incompatible economy rules
- Separate `PureEconomy-Velocity` and `PureEconomy-Bungee` release artifacts

### Changed

- **Paper / Purpur / Folia only** on the Bukkit artifact. Spigot and CraftBukkit are not supported. Chat uses Paper Adventure (`CommandSender.sendMessage(Component)`).
- Plugin JAR is ~0.8MB (was ~25MB). JDBC drivers, Kotlin stdlib, Hikari, and Caffeine are no longer shaded. bStats and BoostedYAML remain relocated inside the jar.
- Network backends no longer open a second MySQL pool or hydrate the local ledger. PlaceholderAPI `peekBalance` is memory-only; cold and expired entries queue one deduplicated background refresh.
- Currency fingerprint is cached in memory and refreshed on the existing 30s timer, not on every mutation.
- `EconomyTransactionEvent` is dispatched on the global region (Folia) / main thread (Paper).
- SQL saves use UPSERT; `/baltop` uses `ORDER BY amount DESC LIMIT`. Bukkit-facing command work remains on the correct Paper/Folia thread, while cache refreshes, join bookkeeping, and persistence are scheduled explicitly in the background.
- Coroutines removed; async work uses `Schedulers.runAsync`.
- Proxy JARs relocate MySQL Connector/J in addition to Hikari.
- Network-enabled backends use authoritative MySQL reads and mutations, while standalone YAML/SQLite installations retain local persistence.

### Fixed

- Network-mode PlaceholderAPI / scoreboard peeks no longer open a MySQL transaction (and no longer INSERT on read)
- Sync Bukkit events are no longer fired from async network command threads
- Whole Bukkit command executors are no longer moved to an async thread
- Network account creation on join no longer waits for MySQL on the player-region thread
- Dirty accounts evicted during an active save pass are retained for the next pass instead of being discarded
- Network transfers and bank moves preserve distinct `INSUFFICIENT`, `MAX_BALANCE`, and invalid-operation results
- `/pay` and Vault withdraw/deposit no longer do extra balance reads around a mutation
- Permission defaults use Bukkit values `true` / `op` / `false` (`everyone` was not a valid default, so `/balance`, `/bank`, `/pay`, and other player commands denied everyone including operators)

## [1.1.0](https://github.com/TamaWish/PureEconomy/compare/v1.0.2...v1.1.0) - 2026-09-09

### Added

- `PureEconomyAPI` registered on Bukkit `ServicesManager` for multi-currency SoftDepend integrators (Vault remains default-currency only for shops): wallets, banks, pay/transfer, name resolve, baltop, currency metadata
- Post-success `EconomyTransactionEvent` for wallet/bank/pay mutations
- Optional SQLite / MySQL storage via Hikari (`storage.type`); YAML remains the default. Empty SQL databases import `data.yml` once
- Caffeine cache for hot accounts and TTL-cached `/baltop` rankings
- PlaceholderAPI bank placeholders `%pureeconomy_bank_<id>%` and `%pureeconomy_bank_<id>_formatted%`
- MiniMessage language support (shipped `lang/en.yml` uses MiniMessage tags; legacy `&` codes still work)
- Lime→emerald MiniMessage `{brand}` / shipped `{prefix}` on Paper-family servers; solid green on Spigot via shaded `adventure-platform-bukkit`
- BoostedYAML comment-preserving merge for `config.yml` and `lang/*.yml` (missing jar keys inserted on load/reload; custom disk-only keys kept)
- Folia-aware coroutine dispatchers for async I/O (update checker HTTP, dirty account saves)

### Changed

- Language parsing prefers MiniMessage, with legacy `&` fallback for existing server lang files
- Plugin sources converted from Java to Kotlin 2 (Java 21 toolchain); build uses Gradle Kotlin DSL + ktlint
- `/eco reload` and startup merge new config/lang keys onto disk (comments from the jar template) instead of Bukkit `setDefaults` only

## [1.0.2](https://github.com/TamaWish/PureEconomy/compare/v1.0.1...v1.0.2) - 2026-09-08

### Fixed

- Spigot enable crash (`NoClassDefFoundError: net.kyori.adventure.text.Component`) by shading Adventure via `adventure-platform-bukkit` and sending messages through `BukkitAudiences` instead of Paper-only `sendMessage(Component)`
- Replaced Paper-only `getPluginMeta()` calls with `getDescription()` so version/author lookups work on Spigot
- Folia scheduling: never fall back to `BukkitScheduler` on Paper/Folia (it throws); use global/async/entity schedulers consistently
- `/pay` recipient notices now run on the recipient's entity region (Folia-safe)
- Safer command registration (null-safe `plugin.yml` lookups; tab completers registered with executors)
- Guard Adventure sends after disable; warn if `/eco reload` leaves no currencies

## [1.0.1](https://github.com/TamaWish/PureEconomy/compare/v1.0.0...v1.0.1) - 2026-09-05

### Added

- Optional asynchronous GitHub release checker (on by default); logs newer releases to console and shows `pureeconomy.admin` players a clickable release link on join
- `/eco bank give|take|set|reset` for admin bank adjustments, with matching `pureeconomy.eco.bank.*` permissions
- `permissions:` section in `config.yml` to rename permission nodes and set `everyone` / `op` / `nobody` defaults; `/eco reload` applies changes (existing `config.yml` files are not overwritten — paste the section in, or built-in `pureeconomy.*` defaults stay in effect)
- `/balance` now also shows the matching bank amount (after upgrading from 1.0.0, update `balance-self`, `balance-other`, and `balance-all-line` in `lang/en.yml` to include `{bank}`, or delete that file and restart so the default is recopied)
- Tab completion for `/currency`
- Error message when `/bank transfer` or `/bank withdraw` fails after pre-checks
- `addBank`, `takeBank`, and `resetBank` on `EconomyService` for admin and integrator bank changes

### Changed

- Currency IDs that do not match `[a-z0-9_]` are skipped at load with a warning
- `/eco reset` remains wallet-only; use `/eco bank reset` to clear a bank balance
- `/eco` now uses the `account-missing` language key when a player account cannot be resolved

## [1.0.0](https://github.com/TamaWish/PureEconomy/releases/tag/v1.0.0) - 2026-09-02

### Added

- Initial public release: multi-currency wallets with optional personal bank balances
- Player commands for balance checks, payments (`/pay`), bank transfer/withdraw, and paginated `/baltop` leaderboards
- Admin `/eco` tools for give, take, set, reset, and reload
- Configurable currencies (names, symbols, precision, starting balances, limits, and transfer settings)
- Optional Vault hook for the configured default currency
- Optional PlaceholderAPI placeholders for every configured currency
- Folia-compatible scheduling and thread-safe balance operations
- Atomic YAML persistence with automatic saves
- Optional anonymous bStats metrics

# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased](https://github.com/TamaWish/PureEconomy/compare/v1.0.2...HEAD)

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

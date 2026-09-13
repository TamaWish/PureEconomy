# Contributing to PureEconomy

Thanks for considering a contribution. Docs, bug reports, ideas, translations, tests, and code are all welcome.

## Table of Contents

- [Ways to contribute](#ways-to-contribute)
- [I have a question](#i-have-a-question)
- [Reporting bugs](#reporting-bugs)
- [Security vulnerabilities](#security-vulnerabilities)
- [Suggesting enhancements](#suggesting-enhancements)
- [Finding something to work on](#finding-something-to-work-on)
- [Development setup](#development-setup)
- [Making changes](#making-changes)
- [Style guides](#style-guides)
- [Commit messages](#commit-messages)
- [Pull requests](#pull-requests)
- [Legal](#legal)
- [Community / getting help](#community--getting-help)

## Ways to contribute

- Ask how-to or setup questions on [Discord](https://discord.gg/kbKZzxDETU)
- Report bugs and propose features via [GitHub Issues](https://github.com/TamaWish/PureEconomy/issues)
- Improve docs (`README.md`, release notes)
- Add or improve translations under `src/main/resources/lang/`
- Add or extend unit tests under `src/test/`
- Open focused pull requests for code fixes and features

Do not use the public issue tracker for security reports (see [Security vulnerabilities](#security-vulnerabilities)).

PureEconomy is economy-only (balances, payments, bank, Vault, PlaceholderAPI). Kits, homes, chat, shops, interest, and GUIs belong elsewhere — for example [AuraUtils](https://github.com/TamaWish/AuraUtils) for utilities. Discuss out-of-scope ideas in an issue before investing in a PR.

## I have a question

1. Check [README.md](README.md) first.
2. Search [existing issues](https://github.com/TamaWish/PureEconomy/issues).
3. For chat and server support, use [Discord](https://discord.gg/kbKZzxDETU).
4. For a clear bug or feature request, open a [GitHub Issue](https://github.com/TamaWish/PureEconomy/issues).

This repository does not use GitHub Discussions.

## Reporting bugs

Before filing:

- Confirm you are on the [latest release](https://github.com/TamaWish/PureEconomy/releases) (or a current build from `main`)
- Rule out config or permission mistakes using [README.md](README.md)
- Search for an existing issue

A useful report includes:

- Expected behavior vs actual behavior
- Steps to reproduce
- Plugin version, Minecraft version, and server software (Paper, Purpur, Folia, …)
- Relevant logs or stack traces (trim secrets and player data)

Open an issue at [GitHub Issues](https://github.com/TamaWish/PureEconomy/issues). There is no issue template yet; the points above are enough.

## Security vulnerabilities

> [!WARNING]
> Do not report security issues in the public tracker.

There is no `SECURITY.md` in this repository yet, and private vulnerability reporting is not enabled on GitHub. Contact the maintainers privately (for example via [Discord](https://discord.gg/kbKZzxDETU)) and wait for guidance before disclosing details publicly.

## Suggesting enhancements

1. Search [existing issues](https://github.com/TamaWish/PureEconomy/issues) first.
2. Describe the problem, your proposed solution, alternatives you considered, and why it helps most servers.
3. Keep proposals aligned with PureEconomy’s focused economy scope (see [Ways to contribute](#ways-to-contribute)).
4. Open an issue for larger changes and discuss them before investing in a PR.

## Finding something to work on

Browse open issues, especially:

- [`good first issue`](https://github.com/TamaWish/PureEconomy/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)
- [`help wanted`](https://github.com/TamaWish/PureEconomy/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22)

Other common labels: `bug`, `enhancement`, `documentation`, `question`.

First PR tip: keep the change small (docs, messages, a focused bug fix, or a single test).

## Development setup

### Prerequisites

- **JDK 21+** (plugin bytecode targets Java 21 via the Gradle toolchain)
- **Gradle** via the included wrapper (`./gradlew`)

See [README.md](README.md) for Minecraft / server requirements.

### Quick start

```bash
git clone https://github.com/<your-username>/PureEconomy.git
```

```bash
cd PureEconomy
```

```bash
./gradlew clean build
```

The shaded JAR appears in `build/libs/` as `PureEconomy-<version>.jar`. Copy it into a test server’s `plugins/` folder when you need an in-game check.

### Tests and formatting

```bash
./gradlew test
```

```bash
./gradlew spotlessApply
```

The build runs Spotless `check` with **ktlint** on `src/**/*.kt`. Apply formatting before you open a PR if `spotlessCheck` would fail.

Performance and threading behavior is part of the compatibility contract. Tests that touch persistence should preserve these invariants:

- PlaceholderAPI/`peekBalance` paths must perform no JDBC on the caller thread.
- Do not move an entire Bukkit command executor to an async scheduler; keep Bukkit API access on the appropriate Paper/Folia thread and schedule only explicit background work.
- Dirty cache inspection must not drain or discard evicted accounts.
- Network mutations must retain their precise failure status rather than collapsing failures into a Boolean.

There is no separate env file for local builds.

## Making changes

1. Fork the repo and create a branch from `main`.
2. Keep the PR small and focused on one concern.
3. Match existing naming and the message style in `src/main/resources/lang/en.yml` when you touch player-facing text.
4. Add or update unit tests when you change logic covered under `src/test/`.
5. Update docs (`README.md`, `CHANGELOG.md`) when behavior or config changes.
6. Before opening the PR, run:

```bash
./gradlew test
```

```bash
./gradlew spotlessApply
```

```bash
./gradlew clean build
```

## Style guides

- **Language:** Kotlin 2 / Java 21 toolchain (bytecode release 21)
- **Formatter:** Spotless with ktlint (`./gradlew spotlessApply` / build-time `spotlessCheck`) — not Google Java Format
- **Messages:** keep keys and tone consistent with `src/main/resources/lang/en.yml`
- **Commits:** recent history uses [Conventional Commits](https://www.conventionalcommits.org/) style (see below)

## Commit messages

Prefer a short Conventional Commits subject:

```text
feat: add update checker, configurable permissions, and admin bank tools
```

Types already common in this repo include `feat`. Use a clear type that matches the change (`fix`, `docs`, `test`, `chore`, …) when it fits.

## Pull requests

- Open PRs against [TamaWish/PureEconomy](https://github.com/TamaWish/PureEconomy) (`main`)
- Link the related issue when there is one
- Include tests for logic changes when practical
- Update docs when user-facing behavior, commands, permissions, or config change
- Mark the PR ready for review when tests pass, Spotless is clean, and the description explains *why* the change exists

There is no pull request template yet. A short summary plus test notes is enough.

## Legal

By contributing, you agree that your contributions are licensed under the project’s [MIT License](LICENSE).

## Community / getting help

| Channel | Use for |
| ------- | ------- |
| [Discord](https://discord.gg/kbKZzxDETU) | Chat, support, private security contact |
| [GitHub Issues](https://github.com/TamaWish/PureEconomy/issues) | Bugs and feature proposals |
| [README.md](README.md) | Install, commands, config, development overview |
| [CHANGELOG.md](CHANGELOG.md) | Version history |
| [GitHub Releases](https://github.com/TamaWish/PureEconomy/releases) | Downloadable builds |

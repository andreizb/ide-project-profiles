# IDE Project Profiles

Automatic project-specific plugin selection through the normal IntelliJ IDEA and Cursor application workflows.

## Install or update on macOS

Quit IntelliJ completely first. Keep IntelliJ IDEA and/or Cursor installed in `/Applications`. Install Python 3.9+ and Node.js 22+ with npm; IntelliJ supplies its own JDK and SDK. Then run:

```sh
gh api -H 'Accept: application/vnd.github.raw+json' repos/andreizb/ide-project-profiles/contents/install.sh | bash
```

The installer resolves an exact GitHub commit, downloads it to a unique short-path temporary directory under `/private/tmp`, builds from source, runs the checks, installs the available components, and verifies the installed IntelliJ agent and Cursor extension hosts. Its exit trap removes the downloaded source, npm cache, dependencies, generated test data and build output on success or failure. No checkout is retained or needed at runtime. It does not install IDEs, buy or activate licenses, or change account credentials.

Only this project's plugins are installed. Other language plugins/extensions remain managed through their native marketplaces. Existing configuration, Java VM flags, extension identities and workspace choices are preserved. A new IntelliJ JVM or Cursor window/reload applies an update. Installation fails before replacing runtime files if source build or tests fail. Backups and a persistent installation receipt are retained locally; a failure after replacement is reported with those backups available for recovery.

To verify the installed components later, without a checkout:

```sh
python3 "$HOME/Library/Application Support/IDE Project Profiles/manage.py" verify
```

The private repository is downloaded through authenticated GitHub CLI calls. Run `gh auth login` once if needed; credentials stay in gh/OS credential storage and never enter the installer or repository. To select a specific maintenance commit, fetch `install.sh` through the authenticated contents API at that commit and pass `IDE_PROFILES_REF=<40-character-commit>` to its Bash process.

## Behavior

**IntelliJ:** a fingerprint-checked Java agent intercepts project opening. A companion plugin detects source/manifests/imported metadata, retains required dependencies and manual choices, and prepares independent configuration, system, downloaded-plugin and log directories for each canonical project path. Multiple projects use separate processes. Opening the same project reuses its process. The IDE-owned 2026.3 Home project bypasses user-project routing. The empty welcome process leaves the Dock before launching the project process and recovers if startup fails.

Git projects use tracked and unignored files. Other projects use a bounded file count with no arbitrary package-depth limit. Unclassified plugins remain enabled. Empty/incomplete detection retains plugins and warns. Backup and Sync is disabled only in managed configurations to prevent project states merging.

The optional `macOS Priority` keymap is enabled by this installer. It derives from JetBrains' macOS System Shortcuts keymap and removes competing IDE bindings reported by macOS. Native Command-M is preserved. This does not edit macOS shortcut settings or fix unrelated JetBrains Runtime native window-tiling issues.

**Cursor:** the MIT-licensed source fork uses native workspace-only enablement with a validated internal storage channel, write read-back/rollback, serialized workspace-local history, manual toggle preservation, dependency closure and conservative incomplete scans. Built-ins, application-scoped extensions and remote access remain available. A changed selection can reload that window once. The lightning status item offers a report, overrides and Enable All Extensions Here.

## Local installed data

- Agent/bridge: `~/Library/LaunchAgents/intellij-project-profiles/`.
- Companion: the current IntelliJ configuration's `plugins/automatic-project-plugins/`.
- Agent flag: the effective custom IntelliJ `idea.vmoptions`, ordinarily inside that same configuration directory. Existing overrides are respected if valid.
- Independent IntelliJ profiles: `~/Library/Application Support/JetBrains/AutomaticProjectPlugins/`. The legacy `262.10968` namespace stays stable for settings continuity; it is not the currently installed IDE build.
- Cursor extension: the normal `~/.cursor/extensions/` registry and runtime directories. Decision history stays in each Cursor workspace's own storage.
- Installer receipt, verification result, maintenance helper and rollback files: `~/Library/Application Support/IDE Project Profiles/`.

All account credentials remain with the native applications/Keychain. None are needed in this repository. Local receipts, project inventories, logs, configurations, caches and historical repair evidence are deliberately excluded.

## Build and maintain

```sh
python3 build.py
python3 run_tests.py
cd cursor
npm ci --ignore-scripts
npm run typecheck
npm run build
npm test
python3 pack.py
```

There are eight Java suites covering the exact IDE opening bytecode, unsupported fingerprint and missing-bridge recovery, plugin selection/dependencies, snapshot independence and migration, Dock failure/retry behavior, and the 2026.3 Home exception. Cursor tests cover language/dependency selection, independent state, manual choices, workspace scope and rollback. The installer additionally runs the **installed** agent against the real SDK class and the installed Cursor bundle in isolated Java/LaTeX/Python native hosts. The generated integration workspaces are synthetic; normal workspace/global settings are not used for that check.

See [MAINTENANCE.md](MAINTENANCE.md), [dependencies.json](dependencies.json), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). This release targets the inspected IntelliJ 2026.3 SDK APIs and was prepared on build `263.7307.200`; an IDE update may need a source adjustment. A new target fingerprint is only installed after compilation and JVM verification succeed. Cursor internal APIs can change; unsupported shapes stop enablement writes.

## Removal and rollback

Use **Enable All Extensions Here** in Cursor workspaces before uninstalling the Cursor component through its Extensions UI; its automatic disable entries are workspace state. Never delete all workspace databases to undo it.

For IntelliJ, remove only the `-javaagent:.../intellij-project-profiles/profile-agent.jar` option from the recorded VM-options file and uninstall Automatic Project Plugins through the IDE, then restart. Keep the inactive agent JARs while any older per-project VM-options copies may still reference them. The installation receipt names the backup directory. Restoring that directory's `idea.vmoptions`, old agent/bridge and companion files while IntelliJ is closed reverts the update. Preserve project profile directories unless you deliberately want to reset settings.

Integration checks keep existing app integrations enabled. On a BYOK-patched Cursor, the disposable process uses a private HOME/database and separate loopback listener; it must not bind, stop or reconfigure the normal user bridge. The bootstrap and commit-pinned archive are fetched through authenticated GitHub API requests, avoiding raw-URL caching and keeping private access in gh credential storage.

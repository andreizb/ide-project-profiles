# Maintenance

GitHub is the authoritative source. Work in a temporary clone, keep credentials in normal gh/OS credential storage, push reviewed source and lockfile changes, and remove the clone after verifying the remote commit. Never commit runtime receipts, user project names/paths, extension inventories, VM-options contents or diagnostics.

## IntelliJ upgrade

The installed IDEA distribution is the SDK dependency; it is not redistributed. `build.py` compiles using its bundled JDK, packages the bridge and repackaged ASM agent, and generates exact descriptor bounds and the target class fingerprint from that SDK. `AgentVerification` loads the real class with full JVM verification and exercises the injected early return. Unknown fingerprints stay unmodified. This is not a promise that future SDK APIs will remain compatible: inspect compile failures, plugin catalog APIs and the normal Home/Open/Recent Projects behavior after upgrades. Keep disabled descriptors in the catalog; resolving only currently enabled plugins breaks switching stacks.

Do not treat IntelliJ Home as a user project. Do not toggle process-global plugins inside a process containing another user project. Preserve the normal trust prompt. Never copy someone else's credentials into fixtures. Defer paid-feature UI checks if licensing is not active; the source and installed-agent checks require no paid account.

## Cursor fork

`cursor/UPSTREAM.json` pins the original MIT source. The fork is editable TypeScript, not patches against a generated bundle. `package-lock.json` locks runtime and build tools with npm integrity hashes. Run `npm ci --ignore-scripts`, typecheck, build and tests. Package the runtime with `pack.py`; generated bundles/VSIX stay out of Git. Keep `andrei-local.automatic-project-extensions` stable to preserve existing configuration/history. The name is an upgrade identifier, not a credential.

The internal workspace channel must have `_shared === false`. Never write the global disabled list. Verify writes and restore on mismatch. `verify_host.py` clones only the installed component into disposable native Cursor hosts with synthetic language extensions; it checks three concurrent workspaces and removes its own test processes/files.

## Installer and publication

`install.sh` pins the downloaded archive to the API-resolved commit and rejects traversal, symlinks and special archive entries. Cleanup owns only the unique mktemp directory. `manage.py` validates/tests all build outputs before replacement, preserves other VM options and unrelated extension registrations, backs up the previous installation, and writes the source commit in its local receipt. A failure after replacement is explicit; backups are the recovery path. Do not describe this installer as an atomic multi-application transaction.

For release acceptance: audit the Git index/history for private data and generated files; push to the canonical GitHub repository; verify the intended public or authenticated README path from an unrelated directory; check the receipt's sourceCommit matches the published SHA and that no install temporary directory remains; run the installed verify helper after removing the download tree. Retain only installed runtime/configuration/backups on the Mac. Never remove the last source checkout until a fresh commit-pinned GitHub archive matches its tree.

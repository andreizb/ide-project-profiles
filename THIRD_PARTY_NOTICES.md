# Third-party source and SDK dependencies

- Cursor component: Auto Disable Extensions 0.2.2 by ElPandaP, MIT. The upstream URL and exact source commit are in cursor/UPSTREAM.json. Its license is retained in cursor/LICENSE.upstream; modified source and original icon assets are included.
- picomatch and smol-toml are MIT dependencies pinned with npm integrity hashes. The VSIX includes their license texts alongside the generated bundle.
- esbuild, TypeScript and type declarations are pinned build-only npm dependencies, not retained by installation.
- IntelliJ API classes, Gson, Kotlin and JetBrains Runtime are provided by the locally installed IDEA SDK and are not uploaded or redistributed here.
- The agent incorporates JetBrains-repackaged ASM classes from the local SDK. ASM's BSD license is retained under licenses/ASM.txt and in generated agent JARs. Inspect the SDK's third-party notices if its ASM implementation changes.

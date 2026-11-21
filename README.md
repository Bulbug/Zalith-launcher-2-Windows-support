# Desk Launcher (Windows)

Windows desktop port of the idea behind Zalith Launcher 2. Phase 1 is a working
vanilla launcher: pick a version, it downloads everything (game, libraries,
assets, Java if needed) and starts Minecraft with an offline account.

**Unofficial Modified Version.** Based on ideas and (from phase 2) code of
Zalith Launcher 2 (GPL-3.0 with additional terms). Keep their copyright notices,
keep this project GPL-3.0, and don't use "ZalithLauncher" or "ZL" in the name.
The app name is the `NAME` constant in `src/main/kotlin/launcher/Config.kt`.

## Run it

You need a JDK 17 or 21 (to run Gradle). Then, in this folder:

```
gradlew run
```

Or open the folder in IntelliJ IDEA and run `launcher.MainKt`.

To build a portable app folder (no extra tools needed):

```
gradlew createDistributable
```

(`packageExe` / `packageMsi` also work but need the WiX Toolset installed.)

## Where files go

`%APPDATA%\.desklauncher\` holds versions, libraries, assets, downloaded Java
runtimes, and the `game` folder (saves, mods, resource packs).

## What works / what doesn't yet

Works: vanilla release/snapshot/old versions (about 1.6 and newer), automatic Java download,
parallel downloads with checksums, log4j fix config, offline accounts.

Not yet (phase 2+): Microsoft login, Fabric/Forge/OptiFine, mod browser,
multiple instances, Zalith's UI screens.

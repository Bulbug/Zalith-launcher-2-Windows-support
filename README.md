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

## Microsoft login (needs a one-time setup)

The Azure app ID is not in this repo, so you need your own:

1. Go to https://portal.azure.com -> App registrations -> New registration.
   Name: anything (avoid "Minecraft" or "Mojang" in it). Supported account types:
   **Personal Microsoft accounts only**. No redirect URI needed.
2. In the app's **Authentication** page, turn on **Allow public client flows**.
3. Copy the **Application (client) ID** and save it in
   `%APPDATA%\.desklauncher\oauth_client_id.txt` (or set the `DESK_OAUTH_CLIENT_ID`
   environment variable).
4. **Mojang has to approve the app ID** before it can sign players in. Apply at
   https://aka.ms/mce-reviewappid. Until it is approved, sign-in fails at the Minecraft
   step with "Invalid app registration" (HTTP 403). Approval can take days to weeks.

Until then, use the **Offline** account.

The login (a refresh token) is saved as plain JSON in `%APPDATA%\.desklauncher\microsoft_account.json`.

## What works / what doesn't yet

Works: vanilla release/snapshot/old versions (about 1.6 and newer), **Fabric and Quilt**
(newest stable loader, picked automatically), automatic Java download, parallel downloads
with checksums, log4j fix config, offline accounts, Microsoft login (once your app ID is approved).
Mods go in the **Mods folder** button's location.

Not yet (phase 3+): Forge/NeoForge/OptiFine, mod and modpack browser (Modrinth/CurseForge),
multiple instances, Zalith's UI screens.

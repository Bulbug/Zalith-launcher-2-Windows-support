# Desk Launcher (beta)

Windows desktop Minecraft launcher inspired by Zalith Launcher 2. A working launcher:
pick a version, it downloads everything (game, libraries, assets, Java if needed)
and starts Minecraft. Supports OptiFine, Fabric, Quilt, Forge (1.13+), NeoForge,
Modrinth mods and modpacks.

**Unofficial Modified Version.** Based on ideas and (from phase 2) code of
Zalith Launcher 2 (GPL-3.0 with additional terms). This project preserves
all copyright notices and is licensed under GPL-3.0.
Original project: https://github.com/ZalithLauncher/ZalithLauncher2
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

## License compliance

This project is a modified version of Zalith Launcher 2 (GPL-3.0 with additional terms).
Per the upstream license terms:
- The application uses a distinct name ("Desk Launcher") that doesn't contain "ZalithLauncher" or "ZL"
- "Unofficial Modified Version" is displayed on the main screen
- All upstream copyright notices are preserved
- This project remains GPL-3.0 licensed

## Microsoft login (needs a one-time setup)

The Azure app ID is not in this repo, so you need your own:

1. Go to https://portal.azure.com -> App registrations -> New registration.
   Name: anything (avoid "Minecraft" or "Mojang" in it). Supported account types:
   **Personal Microsoft accounts only**. No redirect URI needed.
2. In the app's **Authentication** page, turn on **Allow public client flows**.
3. Copy the **Application (client) ID** and save it in
   `%APPDATA%\.desklauncher\oauth_client_id.txt` (or set the `DESK_OAUTH_CLIENT_ID`
   environment variable).
4. **Mojang Approval Required**: After registering your Azure app, submit it for approval at
   https://aka.ms/mce-reviewappid. Until it is approved, sign-in fails at the Minecraft
   step with "Invalid app registration" (HTTP 403). Approval can take days to weeks.

Until then, use the **Offline** account.

The login (a refresh token) is stored securely using Windows DPAPI in `%APPDATA%\.desklauncher\microsoft_account_secure.dat`.

## Instances and modpacks

An **instance** is one setup: a Minecraft version + mod loader, with its own saves, mods and
settings in `%APPDATA%\.desklauncher\instances\<name>`. Make one with **New**, or get one
from a modpack:

- **Import .mrpack**: pick a Modrinth modpack file you downloaded.
- **Browse packs**: search Modrinth modpacks and install one with a click.

A modpack install creates the instance and downloads its mods. Minecraft itself (and Forge,
NeoForge, Fabric or Quilt) is downloaded the first time you press PLAY.

## What works / what doesn't yet

Works (written, not yet tested by a real build - report problems!):
- Vanilla release/snapshot/old versions (about 1.6 and newer), automatic Java download,
  parallel downloads with checksums, log4j fix config.
- Offline accounts, and Microsoft login once your app ID is approved.
- **Fabric and Quilt**, and **Forge and NeoForge** for Minecraft 1.13+ (experimental; they use
  the official installers). Modpacks can pin an exact loader version.
- **OptiFine** (standalone, or with Forge/NeoForge/Fabric/Quilt): pick a build in the New dialog; the installer
  is downloaded from optifine.net and patched onto the vanilla jar. Can be combined with any loader.
- **Mod browser** (Modrinth): search, install with required dependencies, remove.
- **Instances** and **Modrinth modpacks** (.mrpack).

Not yet: CurseForge-format modpacks (full support), Forge for Minecraft 1.12
and older, Zalith's UI screens.

## Recent Improvements

- **OptiFine integration**: Can now be combined with any loader (Forge, NeoForge, Fabric, Quilt, Vanilla)
- **CurseForge integration**: Search and install mods/modpacks from CurseForge
- **Instance filtering**: Filter instances by type (Vanilla, Modloader, Modpack)
- **Instance export/import**: Export instances as .dlpack files (includes mods, resource packs, shaders) and import them
- **Loader-aware mod installation**: Mods are now installed with the instance's loader context
- **Enhanced modpack support**: Modrinth modpacks now properly create instances with correct loaders

- **CurseForge integration**: Search and install mods/modpacks from CurseForge
- **Instance filtering**: Filter instances by type (Vanilla, Modloader, Modpack)
- **Loader-aware mod installation**: Mods are now installed with the instance's loader context
- **Enhanced modpack support**: Modrinth modpacks now properly create instances with correct loaders

- **Instance filtering**: Filter instances by type (Vanilla, Modloader, Modpack)
- **Loader-aware mod installation**: Mods are now installed with the instance's loader context
- **Enhanced modpack support**: Modrinth modpacks now properly create instances with correct loaders

Upgrading from 0.3: the old `instances\<version>-<loader>` folders (without an `instance.json`)
are left alone and don't show up in the list. Copy their saves into a new instance if you
want them.

## Automatic builds (GitHub Actions)

`.github/workflows/build.yml` builds the app on a Windows runner on every push, and uploads
the portable zip, the .jar and the installers as artifacts (Actions tab -> the run -> Artifacts).
Push a tag like `v0.1.0` to publish them as a GitHub Release.

# Desk Launcher Development - Windows Support

**Project**: Desktop Minecraft Launcher for Windows
**License**: GPL-3.0 (modified version of Zalith Launcher 2)
**Build System**: Gradle + Kotlin + Compose Multiplatform

## Quick Start (Windows)

```bash
# Build and run
./gradlew run

# Build distributable (portable folder with bundled Java)
./gradlew createDistributable

# Build installers (requires WiX Toolset)
./gradlew packageExe packageMsi
```

## Windows-Specific Features

### App Data Location
- Root: `%APPDATA%\.desklauncher\`
- Saves, mods, configs go in `instances\<name>\`

### Secure Storage
- Microsoft OAuth tokens stored via Windows DPAPI
- Path: `%APPDATA%\.desklauncher\microsoft_account_secure.dat`

### OAuth Setup
1. Register Azure app at https://portal.azure.com
2. Enable "Allow public client flows"
3. Set `DESK_OAUTH_CLIENT_ID` env var or create `oauth_client_id.txt` in app data folder

---

## Superpowers Development Workflow

### 1. BRAINSTORM
Clarify requirements before building. Ask:
- What feature are you adding?
- How will it work on Windows?
- What are the success criteria?

### 2. PLAN
Break work into 2-5 minute tasks using git worktrees:
```bash
git worktree add -b feature-windows-support ../desk-launcher-windows
```

### 3. DEV
Use Test-Driven Development (TDD):
- Write failing test first (RED)
- Implement fix (GREEN)
- Refactor if needed (REFACTOR)

### 4. REVIEW
- Run `./gradlew test`
- Verify against plan
- Check for edge cases

### 5. FINISH
- `./gradlew build`
- Merge to main
- Delete worktree when done

---

## Key Directories

| Directory | Purpose |
|-----------|---------|
| `src/main/kotlin/launcher/` | Core launcher code |
| `src/main/kotlin/launcher/ui/` | Compose UI components |
| `.github/workflows/` | CI/CD pipelines (Windows runner) |
| `build/compose/binaries/` | Build outputs |

---

## Common Tasks

### Add new mod loader support
1. Add to `Loaders.kt`
2. Update `NewInstanceDialog` for version detection
3. Test installer integration

### Add new download source
1. Create API client (e.g., `Modrinth.kt`, `CurseForge.kt`)
2. Add to dependency graph
3. Update UI browsing screens
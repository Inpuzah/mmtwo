# Build Output & Deployment Guide

## Build Output Locations

### Game Plugin (Paper/Spigot 1.8.8)
```
📁 c:\Users\Shane\mm-two\game-plugin\build\libs\
   └── game-plugin-0.1.0-SNAPSHOT.jar  (~0.08 MB)
```
**Deployment Path:** `{Spigot Server}/plugins/game-plugin-0.1.0-SNAPSHOT.jar`

### Proxy Plugin (Velocity)
```
📁 c:\Users\Shane\mm-two\proxy-plugin\build\libs\
   └── proxy-plugin-0.1.0-SNAPSHOT.jar  (~0.02 MB)
```
**Deployment Path:** `{Velocity Server}/plugins/proxy-plugin-0.1.0-SNAPSHOT.jar`

### Shared API (Dependency)
```
📁 c:\Users\Shane\mm-two\shared-api\build\libs\
   └── shared-api-0.1.0-SNAPSHOT.jar  (~0.01 MB)
```
**Note:** Automatically included via shadowJar; no direct deployment needed

## Build Directory Structure

```
mm-two/
├── game-plugin/
│   ├── src/main/java/com/mmhq/game/
│   │   ├── MmGamePlugin.java
│   │   ├── GameManager.java
│   │   ├── arena/
│   │   ├── commands/
│   │   ├── utils/
│   │   ├── handlers/
│   │   ├── api/
│   │   └── boot/
│   ├── src/main/resources/
│   │   ├── plugin.yml (Bukkit plugin descriptor)
│   │   └── config.yml (Game configuration)
│   ├── build.gradle (Game plugin build config)
│   └── build/
│       ├── classes/
│       ├── libs/
│       │   └── game-plugin-0.1.0-SNAPSHOT.jar ⬅️ DEPLOYMENT JAR
│       └── resources/
│
├── proxy-plugin/
│   ├── src/main/java/com/mmhq/proxy/
│   ├── src/main/resources/velocity-plugin.json
│   ├── build.gradle
│   └── build/
│       ├── classes/
│       ├── libs/
│       │   └── proxy-plugin-0.1.0-SNAPSHOT.jar ⬅️ DEPLOYMENT JAR
│       └── resources/
│
├── shared-api/
│   ├── src/main/java/com/mmhq/sharedapi/
│   ├── build.gradle
│   └── build/
│       ├── classes/
│       ├── libs/
│       │   ├── shared-api-0.1.0-SNAPSHOT.jar
│       │   └── shared-api-0.1.0-SNAPSHOT-sources.jar
│       └── resources/
│
├── build.gradle (Root/multi-module)
├── settings.gradle (Module definitions)
├── gradlew (Gradle wrapper - Unix)
├── gradlew.bat (Gradle wrapper - Windows) ⬅️ USE THIS
└── gradle/
    └── wrapper/
        ├── gradle-wrapper.jar
        └── gradle-wrapper.properties
```

## Build Commands

### Full Build (All Modules)
```bash
# Windows
cd C:\Users\Shane\mm-two
.\gradlew.bat build

# macOS/Linux
./gradlew build
```

### Build Specific Module
```bash
# Game Plugin Only
.\gradlew.bat :game-plugin:build

# Proxy Plugin Only
.\gradlew.bat :proxy-plugin:build

# Shared API Only
.\gradlew.bat :shared-api:build
```

### Clean Build
```bash
.\gradlew.bat clean build
```

### Skip Tests
```bash
.\gradlew.bat build -x test
```

### Build Output (Verbose)
```bash
.\gradlew.bat build --info
```

## Deployment Instructions

### Paper/Spigot Server (1.8.8 + ViaVersion)

1. **Stop the server**
   ```bash
   stop
   ```

2. **Copy game plugin**
   ```bash
   cp C:\Users\Shane\mm-two\game-plugin\build\libs\game-plugin-0.1.0-SNAPSHOT.jar \
      C:\path\to\spigot\plugins\
   ```

3. **Optional: Install ViaVersion** (for 1.9+ client support)
   - Download: https://github.com/ViaVersion/ViaVersion/releases
   - Place in `plugins/` folder

4. **Start server**
   ```bash
   # On Windows Spigot server
   java -Xmx2G -jar spigot-1.8.8.jar nogui
   ```

5. **Verify plugin loaded**
   - Check console for: `MMHQ Murder Mystery Plugin enabled on 1.8.8`
   - Or use `/plugins` command in-game

### Velocity Proxy Server

1. **Copy proxy plugin**
   ```bash
   cp C:\Users\Shane\mm-two\proxy-plugin\build\libs\proxy-plugin-0.1.0-SNAPSHOT.jar \
      C:\path\to\velocity\plugins\
   ```

2. **Restart Velocity**
   ```bash
   # Velocity automatically loads plugins from plugins/ directory
   ```

3. **Verify in Velocity console**
   - Look for plugin load message

## JAR Shadowing & Dependencies

### Game Plugin
- **Includes:** shared-api (via shadowJar)
- **Excludes:** Spigot API (provided at runtime by server)
- **Result:** Single JAR with all dependencies bundled

### Proxy Plugin
- **Includes:** shared-api (via shadowJar)
- **Excludes:** Velocity API (provided at runtime)
- **Result:** Single JAR with all dependencies bundled

### Shared API
- **No external dependencies** (only shared API classes)

**Key:** shadowJar creates a fat JAR that includes the shared-api code, so you only need to deploy the game and proxy JARs.

## Version Information

**Current Release:** `0.1.0-SNAPSHOT`
- Version defined in: `build.gradle` (root)
- Group ID: `com.mmhq.murder`
- Artifact IDs:
  - `game-plugin`
  - `proxy-plugin`
  - `shared-api`

## Building with Different Configurations

### With Custom Version
```bash
.\gradlew.bat build -Dversion=1.0.0
```

### With System Properties
```bash
.\gradlew.bat build -Dorg.gradle.jvmargs="-Xmx2G"
```

### Parallel Build (Faster)
```bash
.\gradlew.bat build --parallel
```

## Gradle Wrapper

The project uses **Gradle Wrapper** (gradlew/gradlew.bat) which:
- ✅ Requires no Gradle installation
- ✅ Ensures consistent Gradle version across machines
- ✅ Automatically downloads Gradle on first run

**Always use `gradlew.bat` (Windows) or `./gradlew` (Unix) - not system gradle**

## Build Output Artifacts

| Artifact | Type | Location | Size |
|----------|------|----------|------|
| game-plugin-0.1.0-SNAPSHOT.jar | Shadow JAR | game-plugin/build/libs/ | ~0.08 MB |
| proxy-plugin-0.1.0-SNAPSHOT.jar | Shadow JAR | proxy-plugin/build/libs/ | ~0.02 MB |
| shared-api-0.1.0-SNAPSHOT.jar | Regular JAR | shared-api/build/libs/ | ~0.01 MB |
| shared-api-0.1.0-SNAPSHOT-sources.jar | Sources | shared-api/build/libs/ | N/A |

## Troubleshooting Build Issues

### Plugin fails to build: "Cannot find symbol"
- **Cause:** Old package files still exist
- **Fix:** Delete old directories and run `./gradlew clean build`
- **Example:**
  ```bash
  rm -rf game-plugin/src/main/java/com/mmhq/game/game
  ./gradlew clean build
  ```

### Gradle daemon issues
- **Fix:** Stop daemon and rebuild
  ```bash
  ./gradlew --stop
  ./gradlew build
  ```

### Memory issues during build
- **Fix:** Increase Gradle heap size
  ```bash
  set GRADLE_OPTS=-Xmx2G
  gradlew.bat build
  ```

### Spigot JAR not found
- **Fix:** Ensure Maven repositories are accessible (check internet connection)
- **Alternative:** Use BuildTools to create local Spigot JAR

## Deployment Checklist

- [ ] Build complete with no errors: `./gradlew build`
- [ ] JAR files exist in build/libs/
- [ ] Target servers have Java 17+ installed
- [ ] Game server running Spigot 1.8.8 or Paper 1.8.8+
- [ ] ViaVersion installed (if supporting 1.9+ clients)
- [ ] Plugins folder writable and accessible
- [ ] Configuration files copied (config.yml, plugin.yml embedded in JAR)
- [ ] Server firewall allows plugin communication
- [ ] Version compatibility verified with `VersionCompat` logging

## Quick Reference

**Build Location:** `c:\Users\Shane\mm-two\`
**Game JAR:** `game-plugin\build\libs\game-plugin-0.1.0-SNAPSHOT.jar`
**Proxy JAR:** `proxy-plugin\build\libs\proxy-plugin-0.1.0-SNAPSHOT.jar`
**Build Command:** `.\gradlew.bat build`
**Clean Command:** `.\gradlew.bat clean build`

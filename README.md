<<<<<<< HEAD
# mmtwo
Murder Mystery clone of Hypixel's mode for running tournaments and custom games, also a starting point for developing Murder Mystery as a Mineplex Studio Partner game.
=======
# MMHQ Murder Mystery

Gradle multi-module Java 17 project targeting a Velocity proxy plugin and a Paper 1.8.8-compatible game plugin with a shared API.

## Modules
- shared-api: Common models, presets, and lightweight events shared by both plugins.
- proxy-plugin: Velocity plugin handling queue/party/announcement stubs.
- game-plugin: Paper 1.8.8 plugin with core game loop scaffolding, simple commands, and config skeleton.

## Build
1. Ensure Java 17 is available.
2. Build with the provided wrapper: `./gradlew build` (macOS/Linux) or `gradlew.bat build` (Windows).

## Deploy
- Proxy: place `proxy-plugin/build/libs/proxy-plugin-<version>.jar` in your Velocity `plugins` folder. The descriptor is in `velocity-plugin.json`.
- Game: place `game-plugin/build/libs/game-plugin-<version>.jar` in your Paper/Spigot 1.8.8 server `plugins` folder. `plugin.yml` is shaded into the jar.

## Commands (stubs)
- Proxy: `mmqueue <preset>` join queue; `mmparty` party placeholder; `mmannounce` broadcast placeholder.
- Game: `mmjoin`, `mmleave`, `mmstart` (requires `mm.start`).

## ViaVersion
Add ViaVersion on the proxy and (optionally) the backend servers to allow newer clients to connect to the 1.8.8 game server.
>>>>>>> b3a5f90 (first import)

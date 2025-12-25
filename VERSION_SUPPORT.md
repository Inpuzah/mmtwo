# Version Support Documentation

## Overview
The MMHQ Murder Mystery plugin now supports **Paper/Spigot 1.8.8+ through 1.21** with automatic version detection and compatibility features via ViaVersion.

## Supported Versions

### Direct Support (Native)
- **1.8.8** - Base/Minimum version (Spigot 1.8.8-R0.1 - latest 1.8.x)
- **1.8+ clients** - Native support (no translation layer needed)

### Via ViaVersion
- **1.9 - 1.21 clients** - Supported on 1.8.8 servers via ViaVersion protocol translation
  - Automatically downgrades modern features for compatibility
  - Enables modern players to enjoy gameplay on legacy servers

## Installation Requirements

### Base Server (1.8.8)
```bash
1. Download Paper-1.8.8.jar (latest build)
2. Place in server directory
3. Run: java -Xmx2G -jar paper-1.8.8.jar nogui
4. Accept EULA in eula.txt
5. (Recommended) Install ViaVersion plugin for 1.9+ client support
```
2. Place in server directory
3. Run: java -Xmx2G -jar paper-1.8.9.jar nogui
4. Accept EULA in eula.txt
5. Install ViaVersion plugin (recommended for client support)
```

### For 1.21 Client Support
1. **Install ViaVersion**:
   - Download latest ViaVersion JAR from [ViaVersion releases](https://github.com/ViaVersion/ViaVersion/releases)
   - Place in `plugins/` folder
   - Restart server

2. **Optional: Install ViaBackwards**:
   - Enables older clients to connect to newer server versions
   - Download from [ViaBackwards releases](https://github.com/ViaVersion/ViaBackwards/releases)

3. **Optional: Install ViaRewind**:
   - Enables 1.8 clients to connect to newer servers
   - Download from [ViaRewind releases](https://github.com/ViaVersion/ViaRewind/releases)

## Build Configuration

### build.gradle
```gradle
compileOnly 'org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT'
compileOnly 'com.viaversion:viaversion-api:4.9.2'
```

### plugin.yml
```yaml
api-version: "1.13"
depend:
  - ViaVersion
```

## Version Compatibility Utilities

The `VersionCompat` utility class provides runtime version detection:

```java
import com.mmhq.game.utils.VersionCompat;

// Get server version
String version = VersionCompat.getVersion(); // "1.8.8"

// Version checks
boolean is18 = VersionCompat.is1_8();
boolean is19Plus = VersionCompat.is1_9OrLater();
boolean is113Plus = VersionCompat.is1_13OrLater();
boolean is205Plus = VersionCompat.is1_20_5OrLater();

// ViaVersion support
boolean viaAvailable = VersionCompat.isViaVersionAvailable();

// Protocol version (for network protocols)
int protocol = VersionCompat.getProtocolVersion();

// NMS support check
boolean nmsSupported = VersionCompat.supportsNMS();

// Log version info
VersionCompat.logVersionInfo(plugin);
```

## Known Compatibility Notes

### 1.8.9 Specific
- Uses legacy packet format (reflection-based NMS)
- Sound enum uses legacy names (ARMOR_EQUIP_LEATHER, etc.)
- No support for components, must use legacy chat color codes
- ≥1.19 clients require ViaVersion

### 1.13+ Changes
- Sound handling updated (optional, use fallback)
- Chat components available
- Block/material API modernized

### 1.20.5+ Changes
- Major NMS restructuring
- Packet format changes
- Use `supportsNMS()` to check compatibility

### 1.21 Support
- Requires ViaVersion for 1.8.9 servers
- Client features automatically downgraded
- Server-side features remain compatible

## Testing Matrix

| Version | Server | Client | Status |
|---------|--------|--------|--------|
| 1.8.8   | ✅     | ✅     | Fully supported (native) |
| 1.12.2  | ❌*    | ✅     | Client via ViaVersion |
| 1.16.5  | ❌*    | ✅     | Client via ViaVersion |
| 1.20.4  | ❌*    | ✅     | Client via ViaVersion |
| 1.21    | ❌*    | ✅     | Client via ViaVersion |

*Can run server on newer versions with compatibility layer (future enhancement)

## Implementation Notes

### Reflection for NMS Features
The codebase uses reflection (not direct NMS imports) for features like:
- Block break animation packets
- Sound packet creation
- Packet sending

This allows compilation against 1.8.9 API while maintaining compatibility.

**Key pattern:**
```java
try {
    Class<?> packetClass = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutBlockBreakAnimation");
    // Reflection-based instantiation and sending
} catch (Throwable e) {
    // Fallback implementation
}
```

### Version Branching in Code
Use `VersionCompat` for version-specific logic:

```java
if (VersionCompat.is1_13OrLater()) {
    // Use new chat component API
} else {
    // Use legacy color codes
}
```

## Future Considerations

1. **1.21.1+**: May require ViaVersion updates
2. **NMS Refactor**: Consider removing NMS dependency as Via becomes standard
3. **Velocity 1.21**: Update proxy plugin for cross-version support
4. **Brigadier Commands**: Could modernize command handling for 1.13+

## Resources

- [Paper Downloads](https://papermc.io/downloads/paper)
- [ViaVersion GitHub](https://github.com/ViaVersion/ViaVersion)
- [Protocol Documentation](https://wiki.vg/)
- [Bukkit API Documentation](https://hub.spigotmc.org/javadocs/spigot/)

## Troubleshooting

### Plugin fails to load
- Check that `spigot-api:1.8.9-R0.1-SNAPSHOT` is available
- Ensure Java 17+ is installed (`java -version`)

### ViaVersion not detected
- Verify ViaVersion JAR is in `plugins/` folder
- Check ViaVersion loaded in `/plugins` output
- Restart server after adding ViaVersion

### NMS features not working
- Some features gracefully degrade if NMS unavailable
- Check console for fallback warnings
- Verify server version with `/version` command

### 1.21 clients can't connect
- Install ViaVersion and restart server
- Check ViaVersion is loaded: look for startup message
- Verify firewall isn't blocking proxy protocol

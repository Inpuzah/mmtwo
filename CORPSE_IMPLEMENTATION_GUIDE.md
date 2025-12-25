# Corpse System Implementation Guide

## Current State

✅ **Framework Complete** (reflection stubs in place)

- `Corpse.java` - Data model for corpse state
- `CorpseManager.java` - Lifecycle management with detailed logging
- `SkinUtils.java` - Skin copying utilities (reflection-based)
- `CorpseTestCommand.java` - Dev command `/mmcorpse [playerName]`
- `plugin.yml` - Command registration
- `GameManager.java` - Integration + `corpses()` getter

**Build Status**: ✅ Clean (816ms)

---

## Next Steps: Packet Implementation

The packet sending methods in `CorpseManager.java` are currently **stubbed** with logging only:

```java
private void sendPlayerInfoAdd(Corpse corpse) { ... }
private void sendSpawnPacket(Corpse corpse) { ... }
private void sendBedPacket(Corpse corpse) { ... }
private void hideNametag(Corpse corpse) { ... }
private void sendDestroyPacket(Corpse corpse) { ... }
private void revertBedBlock(Corpse corpse) { ... }
```

### Route A: ProtocolLib (Recommended)

**Add dependency to `build.gradle` (game-plugin)**:

```gradle
dependencies {
    compileOnly 'com.comphenix.protocol:ProtocolLib:5.0.0'  // Adjust version as needed
}
```

**Then implement each stub**, e.g.:

```java
private void sendPlayerInfoAdd(Corpse corpse) {
    try {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        
        // Create GameProfile
        GameProfile profile = new GameProfile(corpse.npcUuid, corpse.npcName);
        SkinUtils.copySkin(??victim??, profile);  // Need victim reference
        
        // Build PlayerInfo ADD packet
        WrapperPlayServerPlayerInfo wrapper = new WrapperPlayServerPlayerInfo();
        wrapper.setAction(PlayerInfoAction.ADD_PLAYER);
        wrapper.getPlayerInfoData().add(new PlayerInfoData(profile, 0, EnumWrappers.NativeGameMode.CREATIVE, null));
        
        // Send to all players in arena
        for (Player viewer : world.getPlayers()) {
            manager.sendServerPacket(viewer, wrapper.getHandle());
        }
    } catch (Throwable e) { ... }
}
```

### Route B: NMS Reflection (Fallback)

If ProtocolLib unavailable, use NMS packet classes directly. More fragile but works on 1.8.

---

## Integration Points

### On Player Death

In the game death handler, call:

```java
CorpseManager corpses = gameManager.corpses();
corpses.spawnCorpse(victim, deathLocation);
```

### On Round End

In arena cleanup:

```java
gameManager.corpses().clearAll();
```

### Optional: Armor Copy

Extend `Corpse` to store victim's equipped items:

```java
public ItemStack[] armorEquipment;  // [helmet, chestplate, leggings, boots]
```

Then in packet sending, equip the fake player:

```java
sendEquipmentPacket(corpse);  // EntityEquipment packet
```

---

## Testing

### Command Usage

```
/mmcorpse                 → Spawn corpse of self at current location
/mmcorpse <playerName>    → Spawn corpse of <playerName>
```

Logs show:

```
[Corpse] SPAWN_START victim=PlayerName npcId=50000 npcName=corpse_Player loc=100.5,65.0,-200.3
[Corpse] TAB_ADD npc=corpse_Player uuid=...
[Corpse] ENTITY_SPAWN npcId=50000 loc=100.5,65.0,-200.3
[Corpse] BED_PACKET npcId=50000 bedPos=100.5,64.0,-200.3
[Corpse] NAMETAG_HIDDEN npc=corpse_Player
[Corpse] TAB_REMOVE npc=corpse_Player
[Corpse] SPAWN_COMPLETE victim=PlayerName age=0s
```

After 30 seconds:

```
[Corpse] TTL_EXPIRED victim=PlayerName age=30s
[Corpse] DESPAWN_START victim=PlayerName npcId=50000 age=30s
[Corpse] ENTITY_DESTROYED npcId=50000
[Corpse] BED_REVERTED bedPos=100.5,64.0,-200.3
[Corpse] DESPAWN_COMPLETE victim=PlayerName
```

---

## Known Limitations (Stubs)

- ✅ Data model & lifecycle: Complete
- ⏳ Packet sending: Reflection stubs only (logs instead of sending)
- ⏳ Skin texture lookup: Basic reflection code, not tested
- ⏳ Bed positioning tuning: May need Y offset adjustment per server
- ⏳ Armor copy: Not yet implemented

---

## Future Enhancements

1. **ProtocolLib integration** → Production packet sending
2. **Armor copy** → Full victim loadout on corpse
3. **Corpse rotation** → Rotate body based on death direction
4. **Custom death messages** → Chat notification when corpse spawned
5. **Corpse interaction** → Click to loot (optional game feature)

---

## Files Modified/Created

- `Corpse.java` (NEW)
- `CorpseManager.java` (NEW)
- `SkinUtils.java` (NEW)
- `CorpseTestCommand.java` (NEW)
- `GameManager.java` (modified: added corpseManager field + getter)
- `MmGamePlugin.java` (modified: command registration)
- `plugin.yml` (modified: command entry)

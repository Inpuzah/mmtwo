# Combat & Version Neutrality Audit - Findings & Action Items

**Date:** December 21, 2025  
**Philosophy:** 1.8.9 is the rulebook; 1.21 is just the runtime

---

## 🚨 CRITICAL ISSUES FOUND

### 1. **Damage Calculation Not Locked to 1.8.9**

**Current State:**
- `EntityDamageByEntityEvent` handlers check roles but **do NOT override damage values**
- Relying on vanilla damage calculation which differs between versions:
  - 1.8.9: No attack cooldown, instant full damage
  - 1.9+: Attack cooldown system, damage scales with charge %
  - 1.9+: Base damage values changed for many weapons

**Impact:** 
- 1.21 clients may deal different damage than 1.8 clients
- Attack speed attributes could affect timing
- Sweep attacks could trigger on 1.9+ servers

**Required Fix:**
```java
@EventHandler(priority = EventPriority.HIGHEST)
public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    // ... role checks ...
    
    // CRITICAL: Lock damage to 1.8.9 values
    if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
        // Murderer sword in Murder Mystery = instant kill
        // Override damage completely - ignore vanilla calculation
        event.setDamage(20.0); // Instant death, version-neutral
        
        // Reset attack cooldown for 1.9+ clients (visual only)
        if (attacker instanceof Player) {
            VersionUtils.setMaterialCooldown((Player) attacker, Material.IRON_SWORD, 0);
        }
    }
}
```

---

### 2. **Knockback Not Controlled**

**Current State:**
- NO custom knockback implementation
- Relying on vanilla knockback which differs:
  - 1.8.9: Specific vector formula, sprint resets
  - 1.9+: Knockback resistance attribute exists
  - 1.13+: Different knockback calculations

**Impact:**
- Hit reactions feel different between versions
- Murderer attacks could have inconsistent knockback

**Required Fix:**
```java
@EventHandler(priority = EventPriority.HIGHEST)
public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    // ... after role validation ...
    
    if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && 
        attacker instanceof Player && victim instanceof Player) {
        
        // Custom 1.8.9-locked knockback
        event.setCancelled(true); // Cancel vanilla damage/KB
        
        // Apply manual damage
        victim.damage(20.0); // Instant kill
        
        // Apply 1.8.9 knockback formula
        Vector knockback = calculate18Knockback(
            attacker.getLocation(),
            victim.getLocation(),
            attacker.isSprinting()
        );
        victim.setVelocity(knockback);
        
        // Sprint reset (1.8.9 behavior)
        if (attacker.isSprinting()) {
            attacker.setSprinting(false);
            // Re-enable after 1 tick
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (attacker.isOnline()) attacker.setSprinting(true);
            }, 1L);
        }
    }
}

private Vector calculate18Knockback(Location attackerLoc, Location victimLoc, boolean sprint) {
    // 1.8.9 knockback formula
    Vector direction = victimLoc.toVector().subtract(attackerLoc.toVector()).normalize();
    direction.setY(0); // Horizontal only initially
    
    double horizontalKB = 0.4; // 1.8.9 base
    double verticalKB = sprint ? 0.4 : 0.35; // Sprint adds vertical KB
    
    if (sprint) horizontalKB += 0.5; // Sprint boost
    
    return direction.multiply(horizontalKB).setY(verticalKB);
}
```

---

### 3. **Bow Mechanics Not Validated**

**Current State:**
- `EntityShootBowEvent` exists but **no custom handling**
- Relying on vanilla bow mechanics which differ:
  - 1.8.9: No punch enchantment knockback scaling
  - 1.9+: Spectral arrows, tipped arrows
  - 1.9+: Different charge time calculations

**Impact:**
- Detective bow may behave differently across versions
- Arrow velocity/damage could vary

**Required Fix:**
```java
@EventHandler
public void onEntityShootBow(EntityShootBowEvent event) {
    if (!(event.getEntity() instanceof Player shooter)) return;
    if (state != GameState.IN_GAME) return;
    
    PlayerProfile profile = queue.get(shooter.getUniqueId());
    if (profile == null || profile.lastRole() != MurderRole.DETECTIVE) {
        event.setCancelled(true);
        return;
    }
    
    // Lock to 1.8.9 bow mechanics
    org.bukkit.entity.Arrow arrow = (org.bukkit.entity.Arrow) event.getProjectile();
    
    // Force 1.8.9 velocity calculation
    float charge = event.getForce(); // 0.0 to 1.0
    double velocity = Math.min(charge * 3.0, 3.0); // 1.8.9 max velocity
    arrow.setVelocity(arrow.getVelocity().normalize().multiply(velocity));
    
    // Ensure no modern arrow types
    // (1.8.9 only has normal arrows)
    
    // Lock gravity to 1.8.9 value (if possible via NMS/packets)
    // Default Bukkit gravity should match, but verify
}
```

---

### 4. **Attack Speed Attributes Not Disabled**

**Current State:**
- NO code removing attack speed attributes from items
- 1.9+ clients have attack cooldown indicators
- Cooldown affects damage dealt

**Impact:**
- 1.9+ players see attack cooldown bar (visual)
- Could confuse gameplay expectations

**Required Fix:**
```java
// In loadout giving code:
private ItemStack createMurdererSword() {
    ItemStack sword = new ItemStack(Material.IRON_SWORD);
    ItemMeta meta = sword.getItemMeta();
    meta.setDisplayName(ChatColor.RED + "Murderer's Blade");
    
    // Remove all attributes (including attack speed) for 1.9+
    try {
        meta.addAttributeModifier(
            org.bukkit.attribute.Attribute.GENERIC_ATTACK_SPEED,
            new org.bukkit.attribute.AttributeModifier(
                UUID.randomUUID(),
                "generic.attackSpeed",
                1000.0, // Very high = no cooldown visually
                org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER
            )
        );
    } catch (Throwable e) {
        // 1.8.8 doesn't have attributes, ignore
    }
    
    sword.setItemMeta(meta);
    return sword;
}
```

---

### 5. **Movement Physics Not Audited**

**Current State:**
- NO custom movement handling
- Relying on vanilla which differs:
  - 1.9+: Different sprint FOV
  - 1.13+: Swimming mechanics
  - Movement smoothing varies

**Impact:**
- Player movement "feel" could differ
- Sprint mechanics could vary

**Status:** LOW PRIORITY (most movement is server-side and consistent)

**Watch:** Sprint acceleration, strafe speed, air control

---

### 6. **Entity Hitboxes Assumed, Not Enforced**

**Current State:**
- Hit detection in `SwordThrowHandler` uses `getNearbyEntities(radius)`
- NO verification that hitbox size matches 1.8.9
- 1.9+ changed player hitbox when sneaking/swimming

**Impact:**
- Sword throw hits might be inconsistent

**Required Fix:**
```java
// In SwordThrowHandler collision check:
for (Entity entity : world.getNearbyEntities(initialise, maxHitRange, maxHitRange, maxHitRange)) {
    if (entity instanceof Player victim) {
        // Use 1.8.9 hitbox dimensions explicitly
        // Player: 0.6 width, 1.8 height (standing)
        // Don't rely on Bukkit hitbox - calculate manually
        
        BoundingBox victimBox = calculate18HitBox(victim.getLocation());
        if (victimBox.overlaps(swordLocation, maxHitRange)) {
            // Hit confirmed
        }
    }
}

private BoundingBox calculate18HitBox(Location loc) {
    // 1.8.9 player dimensions
    return new BoundingBox(
        loc.getX() - 0.3, loc.getY(), loc.getZ() - 0.3,
        loc.getX() + 0.3, loc.getY() + 1.8, loc.getZ() + 0.3
    );
}
```

---

### 7. **Reach Distance Not Enforced**

**Current State:**
- Melee damage events processed without reach check
- 1.8.9 reach: 3.0 blocks
- 1.9+: Can vary with attributes

**Impact:**
- Modern clients might hit from further away

**Required Fix:**
```java
@EventHandler(priority = EventPriority.HIGHEST)
public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
        event.getDamager() instanceof Player attacker &&
        event.getEntity() instanceof Player victim) {
        
        // Enforce 1.8.9 reach limit: 3.0 blocks
        double distance = attacker.getLocation().distance(victim.getLocation());
        if (distance > 3.0) {
            plugin.getLogger().warning("[COMBAT] Blocked hit beyond 1.8.9 reach: " + 
                attacker.getName() + " -> " + victim.getName() + " (" + distance + " blocks)");
            event.setCancelled(true);
            return;
        }
        
        // ... rest of damage handling ...
    }
}
```

---

## ✅ SYSTEMS THAT ARE CORRECT

### 1. ✅ Cooldown System (Custom Implementation)
- Sword throw cooldown is fully custom (`swordCooldowns` Map)
- Tick-based, server-authoritative
- Not relying on vanilla attack speed

### 2. ✅ Projectile Visual (Armor Stand)
- Flying sword uses custom armor stand entity
- Not relying on vanilla arrow mechanics
- Fully server-controlled trajectory

### 3. ✅ Version Detection Infrastructure
- `VersionCompat` class provides runtime checks
- `VersionUtils` abstracts API differences
- ViaVersion integration for protocol translation

### 4. ✅ Item Checks
- Using `Material.IRON_SWORD` check (version-neutral)
- No reliance on CustomModelData for logic

---

## 📋 PRIORITY ACTION ITEMS

### Phase 1: Critical Combat Fixes (IMMEDIATE)
1. ☐ Override damage values in `EntityDamageByEntityEvent` to 20.0 (instant kill)
2. ☐ Add reach distance validation (3.0 block cap)
3. ☐ Implement custom 1.8.9 knockback formula
4. ☐ Reset attack cooldown after hits (1.9+ clients)
5. ☐ Remove attack speed attributes from murderer sword

### Phase 2: Bow Mechanics (HIGH)
6. ☐ Lock bow charge velocity to 1.8.9 formula
7. ☐ Verify arrow gravity matches 1.8.9
8. ☐ Add bow damage normalization

### Phase 3: Entity & Hitbox (MEDIUM)
9. ☐ Implement manual 1.8.9 hitbox calculations
10. ☐ Validate entity reach in sword throw logic
11. ☐ Audit corpse/body system for version neutrality

### Phase 4: Testing & Validation (ONGOING)
12. ☐ Create test arena for 1.8 vs 1.21 combat comparison
13. ☐ Log all damage events with version info
14. ☐ Document any feel differences as bugs

---

## 🧠 DEVELOPER NOTES

**Key Principle:**
> If a mechanic feels different between versions,  
> the engine is wrong — not the player.

**Testing Protocol:**
1. Spawn 1.8.9 client vs 1.8.9 client - record combat feel
2. Spawn 1.8.9 client vs 1.21 client - compare
3. Spawn 1.21 client vs 1.21 client - compare
4. **Any divergence = regression bug**

**Logging Standards:**
```java
// All combat events should log version info:
plugin.getLogger().info("[COMBAT-1.8.9] Damage: " + damage + 
    " | Attacker: " + attacker.getName() + " (proto " + getProtocolVersion(attacker) + ")" +
    " | Victim: " + victim.getName() + " (proto " + getProtocolVersion(victim) + ")");
```

**Version Check Pattern:**
```java
// WRONG: Checking server version for logic
if (VersionCompat.is1_9OrLater()) {
    // Apply different damage
}

// RIGHT: Version-agnostic logic, version-aware visuals only
event.setDamage(20.0); // Always 1.8.9 value
if (VersionCompat.is1_9OrLater()) {
    VersionUtils.setMaterialCooldown(player, material, 0); // Visual only
}
```

---

## 📊 COMPLIANCE CHECKLIST

Use this to track implementation:

```
Combat & Damage Engine:
☐ Damage values locked to 1.8.9
☐ No vanilla combat mechanics leaking
☐ Attack cooldowns fixed/removed
☐ Sword damage verified
☐ No sweep attacks possible
☐ No attack speed attribute effects
☐ Sprint reset matches 1.8.9
☐ Critical hits match 1.8.9
☐ Block-hitting supported

Knockback:
☐ All KB sources audited
☐ Manual KB calculation implemented
☐ No modern KB resistance applies
☐ Vertical/horizontal ratios validated

Bow Mechanics:
☐ Charge timing matches 1.8.9
☐ Arrow velocity matches 1.8.9
☐ Arrow gravity matches 1.8.9
☐ Hit detection server-controlled
☐ Damage scaling verified
☐ Knockback matches 1.8.9

Movement:
☐ Speed calculations audited
☐ Sprint accel/decel verified
☐ Air control verified
☐ Jump height matches 1.8.9
☐ Hitbox size enforced

Entity Handling:
☐ Hitboxes match 1.8.9
☐ Reach checks manual (3.0 cap)
☐ Line-of-sight server-side

Item System:
☐ All items exist in 1.8
☐ No CustomModelData for logic
☐ No attributes for damage/speed
☐ Offhand disabled for logic
☐ NBT minimal & protocol-safe

Protocol:
☐ No logic checks server version
☐ All version checks client-based
☐ Fallback for unknown versions
☐ Mixed-version tested
```

---

**End of Audit** - Generated: 2025-12-21

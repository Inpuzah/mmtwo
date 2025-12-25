package com.mmhq.game.utils;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;

/**
 * Version-agnostic utility methods for common operations across Minecraft versions.
 */
public final class VersionUtils {

    /**
     * Get the item in the player's hand (works for 1.8.8 and newer).
     * @param player The player
     * @return The item in their hand, or null
     */
    public static ItemStack getItemInHand(Player player) {
        try {
            // Try 1.9+ API first via reflection
            java.lang.reflect.Method method = player.getInventory().getClass().getMethod("getItemInMainHand");
            return (ItemStack) method.invoke(player.getInventory());
        } catch (Throwable e) {
            // 1.8.8 fallback: getItemInHand()
            try {
                return player.getItemInHand();
            } catch (Throwable ex) {
                return null;
            }
        }
    }

    /**
     * Get item from a LivingEntity (ArmorStand, etc).
     * @param entity The entity
     * @return The item, or null
     */
    public static ItemStack getItemInHand(LivingEntity entity) {
        if (entity instanceof Player) {
            return getItemInHand((Player) entity);
        }
        try {
            // Try 1.9+ equipment API via reflection
            java.lang.reflect.Method method = entity.getEquipment().getClass().getMethod("getItemInMainHand");
            return (ItemStack) method.invoke(entity.getEquipment());
        } catch (Throwable e) {
            // 1.8.8 fallback
            try {
                return entity.getEquipment().getItemInHand();
            } catch (Throwable ex) {
                return null;
            }
        }
    }

    /**
     * Set item in a LivingEntity's hand.
     * @param entity The entity
     * @param item The item to set
     */
    public static void setItemInHand(LivingEntity entity, ItemStack item) {
        try {
            // Try 1.9+ equipment API via reflection
            java.lang.reflect.Method method = entity.getEquipment().getClass().getMethod("setItemInMainHand", ItemStack.class);
            method.invoke(entity.getEquipment(), item);
        } catch (Throwable e) {
            // 1.8.8 fallback
            try {
                entity.getEquipment().setItemInHand(item);
            } catch (Throwable ex) {
                // Silently fail
            }
        }
    }

    /**
     * Teleport an entity safely.
     * @param entity The entity
     * @param location The target location
     */
    public static void teleport(org.bukkit.entity.Entity entity, Location location) {
        entity.teleport(location);
    }

    /**
     * Set material cooldown for a player (1.9+).
     * @param player The player
     * @param material The material
     * @param ticks The cooldown ticks
     */
    public static void setMaterialCooldown(Player player, org.bukkit.Material material, int ticks) {
        try {
            // This method only exists in 1.9+
            java.lang.reflect.Method method = player.getClass().getMethod("setCooldown", org.bukkit.Material.class, Integer.TYPE);
            method.invoke(player, material, ticks);
        } catch (Throwable e) {
            // 1.8.8 doesn't have cooldown API, silently ignore
        }
    }

    /**
     * Set collidable state for an entity (1.9+).
     * @param entity The entity
     * @param collidable Whether it should be collidable
     */
    public static void setCollidable(LivingEntity entity, boolean collidable) {
        try {
            // This method only exists in 1.9+
            java.lang.reflect.Method method = entity.getClass().getMethod("setCollidable", Boolean.TYPE);
            method.invoke(entity, collidable);
        } catch (Throwable e) {
            // 1.8.8 doesn't have setCollidable, silently ignore
        }
    }

    /**
     * Check if a version is available (simplified version checking).
     * @return The current server version as a string (e.g., "1.8.8")
     */
    public static String getVersion() {
        return VersionCompat.getVersion();
    }

    /**
     * Send an action bar message to a player.
     * Uses Adventure (Paper) > Spigot/Bungee ACTION_BAR.
     * @param player The player
     * @param msg The message (supports &-color codes)
     */
    public static void sendActionBar(Player player, String msg) {
        if (player == null) return;

        final boolean DEBUG = true;
        final String colored = ChatColor.translateAlternateColorCodes('&', msg);

        if (DEBUG) Bukkit.getLogger().info("[MMHQ DEBUG] sendActionBar CALLED for " + player.getName() + " msg=" + colored);

        // ------------------------------------------------------------
        // 1) Paper/Adventure: Player#sendActionBar(Component)
        // (Use methods from PUBLIC types only)
        // ------------------------------------------------------------
        try {
            // Find Player.sendActionBar(net.kyori.adventure.text.Component) on the PUBLIC Player API
            java.lang.reflect.Method sendActionBar = null;
            Class<?> componentClz = Class.forName("net.kyori.adventure.text.Component");

            for (java.lang.reflect.Method m : Player.class.getMethods()) {
                if (!m.getName().equals("sendActionBar")) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length == 1 && pt[0].equals(componentClz)) {
                    sendActionBar = m;
                    break;
                }
            }

            if (sendActionBar != null) {
                // LegacyComponentSerializer.legacySection().deserialize(colored)
                Class<?> legacySerApiClz = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
                Object serializer = legacySerApiClz.getMethod("legacySection").invoke(null);

                // IMPORTANT: grab deserialize() from the PUBLIC interface type, not the impl
                Object component = legacySerApiClz.getMethod("deserialize", String.class).invoke(serializer, colored);

                sendActionBar.invoke(player, component);

                if (DEBUG) Bukkit.getLogger().info("[MMHQ DEBUG] ActionBar OK (Adventure Player#sendActionBar(Component))");
                return;
            } else {
                if (DEBUG) Bukkit.getLogger().info("[MMHQ DEBUG] Adventure path: Player#sendActionBar(Component) not present; trying Spigot path.");
            }
        } catch (Throwable t) {
            if (DEBUG) Bukkit.getLogger().info("[MMHQ DEBUG] Adventure path FAILED: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        // ------------------------------------------------------------
        // 2) Spigot/Bungee ACTION_BAR:
        // Invoke sendMessage from PUBLIC Player$Spigot, not CraftPlayer$2
        // ------------------------------------------------------------
        try {
            // Get spigot object via PUBLIC API type
            Object spigotObj = Player.class.getMethod("spigot").invoke(player);

            Class<?> chatMessageTypeClz = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBarType = Enum.valueOf((Class<? extends Enum>) chatMessageTypeClz.asSubclass(Enum.class), "ACTION_BAR");

            Class<?> textComponentClz = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object baseComponents = textComponentClz.getMethod("fromLegacyText", String.class).invoke(null, colored); // BaseComponent[]

            // IMPORTANT: reflect sendMessage from PUBLIC nested type org.bukkit.entity.Player$Spigot
            Class<?> playerSpigotClz = Class.forName("org.bukkit.entity.Player$Spigot");

            // Signature: sendMessage(ChatMessageType, BaseComponent[])
            // (array type check kept loose because some builds use varargs)
            java.lang.reflect.Method sendMessage = null;
            for (java.lang.reflect.Method m : playerSpigotClz.getMethods()) {
                if (!m.getName().equals("sendMessage")) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length == 2 && pt[0].equals(chatMessageTypeClz) && pt[1].isArray()) {
                    sendMessage = m;
                    break;
                }
            }

            if (sendMessage == null) {
                if (DEBUG) Bukkit.getLogger().info("[MMHQ DEBUG] Spigot path FAILED: Player$Spigot#sendMessage not found.");
                return;
            }

            sendMessage.invoke(spigotObj, actionBarType, baseComponents);

            if (DEBUG) Bukkit.getLogger().info("[MMHQ DEBUG] ActionBar OK (Spigot/Bungee ACTION_BAR via Player$Spigot)");
            return;
        } catch (Throwable t) {
            if (DEBUG) Bukkit.getLogger().info("[MMHQ DEBUG] Spigot/Bungee path FAILED: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        // ------------------------------------------------------------
        // 3) No fallback to chat. If both fail, do nothing.
        // ------------------------------------------------------------
        if (DEBUG) Bukkit.getLogger().info("[MMHQ DEBUG] ActionBar FAILED: no working method on this server build.");
    }

    /**
     * Detects the NMS version token (e.g., "v1_8_R3") from the server.
     * Returns null if not found.
     */
    private static String detectNmsVersionToken() {
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            for (String part : pkg.split("\\.")) {
                if (part.startsWith("v1_")) return part;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Spawn redstone dust particles (works on 1.8+ and modern).
     * @param loc The location to spawn at
     * @param count Number of particles
     */
    public static void spawnRedstoneDust(Location loc, int count) {
        if (loc == null || loc.getWorld() == null) return;
        World w = loc.getWorld();
        count = Math.max(1, count);

        // Modern (1.13+): Particle.REDSTONE with DustOptions
        try {
            Class<?> particleClz = Class.forName("org.bukkit.Particle");
            Object redstone = Enum.valueOf((Class<Enum>) particleClz.asSubclass(Enum.class), "REDSTONE");

            Class<?> dustOptionsClz = Class.forName("org.bukkit.Particle$DustOptions");
            Class<?> colorClz = Class.forName("org.bukkit.Color");
            
            java.lang.reflect.Method fromRGB = colorClz.getMethod("fromRGB", int.class);
            Object red = fromRGB.invoke(null, 0xFF0000);
            
            java.lang.reflect.Constructor<?> dustCtor = dustOptionsClz.getConstructor(colorClz, float.class);
            Object dust = dustCtor.newInstance(red, 1.2f);

            java.lang.reflect.Method spawnParticle = w.getClass()
                    .getMethod("spawnParticle", particleClz, Location.class, int.class, double.class, double.class, double.class, double.class, Object.class);
            spawnParticle.invoke(w, redstone, loc, count, 0.02, 0.02, 0.02, 0.0, dust);
            return;
        } catch (Throwable ignored) {
        }

        // Legacy (1.8.9): NMS EnumParticle.REDSTONE
        try {
            String v = detectNmsVersionToken();
            if (v == null) return;
            Class<?> enumParticleClz = Class.forName("net.minecraft.server." + v + ".EnumParticle");
            Object redstone = Enum.valueOf((Class<Enum>) enumParticleClz.asSubclass(Enum.class), "REDSTONE");

            Class<?> pktClz = Class.forName("net.minecraft.server." + v + ".PacketPlayOutWorldParticles");

            // Find compatible constructor
            java.lang.reflect.Constructor<?> ctor = null;
            for (java.lang.reflect.Constructor<?> c : pktClz.getConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length >= 10 && params[0].equals(enumParticleClz)) {
                    ctor = c;
                    break;
                }
            }
            if (ctor == null) return;

            Object pkt;
            Class<?>[] paramTypes = ctor.getParameterTypes();
            if (paramTypes.length == 11) {
                // (EnumParticle, boolean, x, y, z, offX, offY, offZ, speed, count, int[])
                pkt = ctor.newInstance(redstone, true,
                        (float) loc.getX(), (float) loc.getY(), (float) loc.getZ(),
                        0.02f, 0.02f, 0.02f,
                        0.0f, count,
                        new int[0]);
            } else {
                // (EnumParticle, boolean, x, y, z, offX, offY, offZ, speed, count)
                pkt = ctor.newInstance(redstone, true,
                        (float) loc.getX(), (float) loc.getY(), (float) loc.getZ(),
                        0.02f, 0.02f, 0.02f,
                        0.0f, count);
            }

            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(loc) > (64 * 64)) continue;
                sendPacket(p, pkt);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void sendPacket(Player p, Object packet) throws Exception {
        String v = detectNmsVersionToken();
        if (v == null) throw new IllegalStateException("No NMS version token detected; cannot send NMS packet.");

        Class<?> craftPlayerClz = Class.forName("org.bukkit.craftbukkit." + v + ".entity.CraftPlayer");
        Object cp = craftPlayerClz.cast(p);
        Object handle = craftPlayerClz.getMethod("getHandle").invoke(cp);

        Object connection;
        try {
            connection = handle.getClass().getField("playerConnection").get(handle);
        } catch (NoSuchFieldException e) {
            connection = handle.getClass().getField("connection").get(handle);
        }

        java.lang.reflect.Method send = null;
        for (java.lang.reflect.Method m : connection.getClass().getMethods()) {
            if (m.getName().equals("sendPacket") && m.getParameterTypes().length == 1) {
                send = m;
                break;
            }
        }
        if (send != null) send.invoke(connection, packet);
    }

    /**
     * Show a block break animation (crack) on a block for all nearby players.
     * @param world The world
     * @param blockLoc The block location
     * @param stage The crack stage (0..9, -1 to clear)
     * @param clearAfterTicks How many ticks before auto-clearing (-1 for no auto-clear)
     */
    public static void showBlockCrack(World world, Location blockLoc, int stage, int clearAfterTicks) {
        if (world == null || blockLoc == null) return;
        java.util.concurrent.atomic.AtomicInteger ID = new java.util.concurrent.atomic.AtomicInteger(10000);
        int entityId = ID.incrementAndGet();

        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(blockLoc) > (64 * 64)) continue;
            sendBlockBreakPacket(p, blockLoc, entityId, stage);
        }

        if (clearAfterTicks > 0) {
            Bukkit.getScheduler().runTaskLater(
                    Bukkit.getPluginManager().getPlugins()[0],
                    () -> {
                        for (Player p : world.getPlayers()) {
                            if (p.getLocation().distanceSquared(blockLoc) > (64 * 64)) continue;
                            sendBlockBreakPacket(p, blockLoc, entityId, -1);
                        }
                    },
                    clearAfterTicks
            );
        }
    }

    private static void sendBlockBreakPacket(Player p, Location loc, int entityId, int stage) {
        try {
            String v = detectNmsVersionToken();
            if (v == null) return;

            // CraftPlayer + handle
            Class<?> craftPlayerClz = Class.forName("org.bukkit.craftbukkit." + v + ".entity.CraftPlayer");
            Object craftPlayer = craftPlayerClz.cast(p);
            Object handle = craftPlayerClz.getMethod("getHandle").invoke(craftPlayer);

            Object connection;
            try {
                java.lang.reflect.Field pc = handle.getClass().getField("playerConnection");
                connection = pc.get(handle);
            } catch (NoSuchFieldException nsf) {
                java.lang.reflect.Field c = handle.getClass().getField("connection");
                connection = c.get(handle);
            }

            // NEW (1.17+): ClientboundBlockDestructionPacket
            try {
                Class<?> blockPosClz = Class.forName("net.minecraft.core.BlockPos");
                java.lang.reflect.Constructor<?> bpCtor = blockPosClz.getConstructor(int.class, int.class, int.class);
                Object bp = bpCtor.newInstance(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

                Class<?> pktClz = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket");
                Object pkt;
                try {
                    java.lang.reflect.Constructor<?> ctor = pktClz.getConstructor(int.class, blockPosClz, int.class);
                    pkt = ctor.newInstance(entityId, bp, stage);
                } catch (NoSuchMethodException e) {
                    java.lang.reflect.Constructor<?> ctor = pktClz.getConstructor(blockPosClz, int.class, int.class);
                    pkt = ctor.newInstance(bp, entityId, stage);
                }

                java.lang.reflect.Method sendPacket = findSendPacket(connection.getClass());
                sendPacket.invoke(connection, pkt);
                return;
            } catch (ClassNotFoundException ignoredModern) {
                // fall through to legacy
            }

            // OLD (<=1.16): PacketPlayOutBlockBreakAnimation
            Class<?> blockPosClz = Class.forName("net.minecraft.server." + v + ".BlockPosition");
            java.lang.reflect.Constructor<?> bpCtor = blockPosClz.getConstructor(int.class, int.class, int.class);
            Object bp = bpCtor.newInstance(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

            Class<?> pktClz = Class.forName("net.minecraft.server." + v + ".PacketPlayOutBlockBreakAnimation");
            java.lang.reflect.Constructor<?> pktCtor = pktClz.getConstructor(int.class, blockPosClz, int.class);
            Object pkt = pktCtor.newInstance(entityId, bp, stage);

            java.lang.reflect.Method sendPacket = findSendPacket(connection.getClass());
            sendPacket.invoke(connection, pkt);

        } catch (Throwable ignored) {
        }
    }

    private static java.lang.reflect.Method findSendPacket(Class<?> connectionClz) throws NoSuchMethodException {
        for (java.lang.reflect.Method m : connectionClz.getMethods()) {
            if (!m.getName().equals("sendPacket")) continue;
            if (m.getParameterTypes().length == 1) return m;
        }
        throw new NoSuchMethodException("sendPacket not found");
    }
}

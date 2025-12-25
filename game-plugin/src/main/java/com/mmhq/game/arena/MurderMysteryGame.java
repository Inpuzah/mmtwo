package com.mmhq.game.arena;

// Shared API imports
import com.mmhq.sharedapi.event.PhaseChangeEvent;
import com.mmhq.sharedapi.game.GameState;
import com.mmhq.sharedapi.game.MatchPreset;
import com.mmhq.sharedapi.game.MurderRole;
import com.mmhq.sharedapi.game.ServerStatus;
import com.mmhq.sharedapi.player.PlayerProfile;

// Local arena imports
import com.mmhq.game.arena.special.CorpseManager;
import com.mmhq.game.arena.managers.DetectiveBowDropManager;
import com.mmhq.game.arena.managers.GameScoreboardManager;
import com.mmhq.game.arena.managers.GoldCollectionManager;
import com.mmhq.game.arena.managers.GoldSpawnManager;
import com.mmhq.game.arena.managers.HeartbeatPublisher;

// Bukkit API imports - core
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

// Bukkit entity imports
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

// Bukkit inventory imports
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

// Bukkit event imports
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

// BungeeCord chat imports
import net.md_5.bungee.api.chat.TextComponent;

// Java standard library imports
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MurderMysteryGame implements Listener {
    // ===== Core Plugin References =====
    private final JavaPlugin plugin;
    private final MatchPreset preset;
    private final List<MapDefinition> maps;

    // ===== Game State =====
    private GameState state = GameState.LOBBY;
    private int roundSecondsLeft;
    private boolean loadoutsGiven;

    // ===== Player Lists & Queues =====
    private final Map<UUID, PlayerProfile> queue = new HashMap<>();
    private final List<UUID> alive = new ArrayList<>();
    private UUID murdererId;
    private UUID detectiveId;

    // ===== Map & Spawn Management =====
    private MapDefinition currentMap;
    private MapDefinition nextMap;
    private final Location lobbySpawn;
    private final double lobbySpawnOffsetY;
    private final Location preGameLobbySpawn;

    // ===== Manager Dependencies =====
    private final GoldCollectionManager goldManager;
    private final GameScoreboardManager scoreboardManager;
    private final HeartbeatPublisher heartbeatPublisher;
    private GoldSpawnManager goldSpawnManager;
    private DetectiveBowDropManager bowDropManager;
    private CorpseManager corpseManager;
    
    // ===== Sword Throwing =====
    private com.mmhq.game.arena.special.SwordThrowHandler swordThrowHandler;

    // ===== Scheduled Tasks =====
    private BukkitTask countdownTask;
    private BukkitTask roundEndTask;
    private BukkitTask timeUpdateTask;

    // ===== Knife Mechanics =====
    private final Map<UUID, Long> knifeCooldowns = new HashMap<>();
    private final Set<UUID> knifeTesters = new HashSet<>();
    private final Set<UUID> knifeCharging = new HashSet<>();
    private double knifeMaxRange;
    private double knifeStep;
    private int knifeCooldownMs;

    // ===== Constants =====
    private static final int VERIFY_LOADOUT_DELAY_TICKS = 5;

    public MurderMysteryGame(JavaPlugin plugin, MatchPreset preset, List<MapDefinition> maps, CorpseManager corpseManager) {
        this.plugin = plugin;
        this.preset = preset;
        this.maps = maps;
        this.corpseManager = corpseManager;
        this.lobbySpawnOffsetY = plugin.getConfig().getDouble("preset.default.lobbySpawnOffsetY", 0.0);
        this.lobbySpawn = resolveLobbySpawn();
        this.preGameLobbySpawn = resolvePreGameLobbySpawn();
        this.goldManager = new GoldCollectionManager(plugin);
        this.scoreboardManager = new GameScoreboardManager(plugin, goldManager, "MM1");
        this.heartbeatPublisher = new HeartbeatPublisher(plugin, this::buildStatus);
        this.roundSecondsLeft = 0;
        this.loadoutsGiven = false;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(goldManager, plugin);
        scoreboardManager.startUpdating();
        // Managers for bow drop
        this.bowDropManager = new DetectiveBowDropManager(plugin);
        this.goldManager.setBowDropManager(bowDropManager);
        this.heartbeatPublisher.start();
        // Load knife settings from config with safe defaults
        this.knifeMaxRange = plugin.getConfig().getDouble("knife.maxRange", 25.0);
        this.knifeStep = plugin.getConfig().getDouble("knife.step", 0.2);
        this.knifeCooldownMs = plugin.getConfig().getInt("knife.cooldownMs", 1100);
        // Register sword throwing handler
        this.swordThrowHandler = new com.mmhq.game.arena.special.SwordThrowHandler(plugin, new com.mmhq.game.arena.special.SwordSkinManager());
        plugin.getServer().getPluginManager().registerEvents(swordThrowHandler, plugin);
    }

    public GameState state() {
        return state;
    }

    public void setNextMap(MapDefinition map) {
        this.nextMap = map;
        heartbeatPublisher.sendHeartbeat();
    }

    public MapDefinition getNextMap() {
        return nextMap;
    }

    public boolean hasPlayer(UUID playerId) {
        return queue.containsKey(playerId);
    }

    public int getQueueSize() {
        return queue.size();
    }

    public int getMaxPlayers() {
        return preset.maxPlayers();
    }

    // Knife test mode management
    public void enableKnifeTest(UUID playerId) {
        knifeTesters.add(playerId);
        try { scoreboardManager.enableKnifeTest(playerId); } catch (Throwable ignored) {}
    }

    public void disableKnifeTest(UUID playerId) {
        knifeTesters.remove(playerId);
        try { scoreboardManager.disableKnifeTest(playerId); } catch (Throwable ignored) {}
    }

    public void addToQueue(Player player, PlayerProfile profile) {
        plugin.getLogger().info("[MM-QUEUE] Player joining: " + player.getName() + " | State: " + state + " | Queue size: " + queue.size());
        plugin.getLogger().info("[MM-QUEUE] nextMap=" + (nextMap != null ? nextMap.name() : "NULL") + 
                               ", currentMap=" + (currentMap != null ? currentMap.name() : "NULL"));
        
        // Block all joins once a round has started
        if (state == GameState.IN_GAME || state == GameState.ENDING) {
            plugin.getLogger().info("[MM-QUEUE] ✗ BLOCKED: Game in progress");
            player.sendMessage(ChatColor.RED + "Game already in progress. Please wait for the next round.");
            return;
        }
        queue.put(player.getUniqueId(), profile);
        
        // Determine spawn location: use selected map's waiting spawn if available
        Location spawnLoc = getWaitingSpawn();
        String mapName = nextMap != null ? nextMap.name() : (currentMap != null ? currentMap.name() : preset.mapName());
        
        plugin.getLogger().info("[MM-QUEUE] Using waiting spawn from: " + mapName);
        plugin.getLogger().info("[MM-QUEUE] Spawn location: world=" + 
                               (spawnLoc.getWorld() != null ? spawnLoc.getWorld().getName() : "NULL") +
                               " x=" + spawnLoc.getBlockX() + " y=" + spawnLoc.getBlockY() + " z=" + spawnLoc.getBlockZ());
        
        player.sendMessage("Joined lobby queue for " + mapName);
        // Clear inventory immediately when joining lobby
        player.getInventory().clear();
        plugin.getLogger().info("[MM-QUEUE] ✓ Added " + player.getName() + " (UUID: " + player.getUniqueId() + ")");
        // Teleport to the appropriate waiting spawn
        player.teleport(spawnLoc);
        plugin.getLogger().info("[MM-QUEUE] Teleported to waiting spawn at " + 
                               (spawnLoc.getWorld() != null ? spawnLoc.getWorld().getName() : "NULL") + " " + 
                               spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ());
        plugin.getLogger().fine(player.getName() + " joined queue. Size now " + queue.size());
        scoreboardManager.setPlayerCounts(queue.size(), preset.maxPlayers(), preset.minPlayers());
        heartbeatPublisher.sendHeartbeat();
        if (queue.size() >= preset.minPlayers() && state == GameState.LOBBY) {
            plugin.getLogger().info("[MM-QUEUE] Queue threshold reached: " + queue.size() + "/" + preset.minPlayers() + ", starting countdown...");
            beginCountdown();
        }
    }

    public void removeFromQueue(UUID playerId) {
        Player p = Bukkit.getPlayer(playerId);
        String playerName = p != null ? p.getName() : playerId.toString();
        plugin.getLogger().info("[MM-QUEUE] Player leaving: " + playerName + " | Queue size before: " + queue.size());
        queue.remove(playerId);
        scoreboardManager.setPlayerCounts(queue.size(), preset.maxPlayers(), preset.minPlayers());
        heartbeatPublisher.sendHeartbeat();
        plugin.getLogger().info("[MM-QUEUE] Queue size after: " + queue.size());
    }

    public void forceStart() {
        if (state == GameState.IN_GAME) {
            return;
        }
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        if (roundEndTask != null) {
            roundEndTask.cancel();
            roundEndTask = null;
        }
        startGame();
    }

    private void beginCountdown() {
        if (countdownTask != null) {
            plugin.getLogger().warning("[MM-COUNTDOWN] ✗ Countdown already running!");
            return;
        }
        plugin.getLogger().info("[MM-COUNTDOWN] ===== COUNTDOWN STARTED =====");
        updateState(GameState.COUNTDOWN);
        scoreboardManager.setState(GameState.COUNTDOWN);
        // Show intended map early if known, otherwise preset default
        scoreboardManager.setMapName(nextMap != null ? nextMap.name() : preset.mapName());
        plugin.getLogger().info("[MM-COUNTDOWN] Map: " + (nextMap != null ? nextMap.name() : preset.mapName()) + " | Players: " + queue.size());
        final int[] seconds = { 60 }; // 60 seconds
        scoreboardManager.setCountdownSeconds(seconds[0]);
        scoreboardManager.setPlayerCounts(queue.size(), preset.maxPlayers(), preset.minPlayers());
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (seconds[0] <= 0) {
                plugin.getLogger().info("[MM-COUNTDOWN] ===== COUNTDOWN COMPLETE - STARTING GAME =====");
                countdownTask.cancel();
                countdownTask = null;
                startGame();
                return;
            }
            // Broadcast at key intervals (60, 30, 10, 5, 4, 3, 2, 1)
            if (seconds[0] == 60 || seconds[0] == 30 || seconds[0] <= 10) {
                broadcastToQueue(ChatColor.GOLD + "Game starting in " + seconds[0] + "s");
            }
            scoreboardManager.setCountdownSeconds(seconds[0]); // Update scoreboard
            seconds[0]--;
        }, 0L, 20L); // Every second (20 ticks)
    }

    private void startGame() {
        plugin.getLogger().info("[MM-START] ===== GAME START SEQUENCE =====");
        plugin.getLogger().info("[MM-START] Players: " + queue.size() + " | Min: " + preset.minPlayers());
        if (queue.size() < preset.minPlayers()) {
            plugin.getLogger().warning("[MM-START] ✗ Not enough players, aborting");
            broadcastToQueue("Not enough players to start.");
            updateState(GameState.LOBBY);
            return;
        }
        updateState(GameState.IN_GAME);
        currentMap = pickMap();
        MapDefinition map = currentMap;
        loadoutsGiven = false;
        plugin.getLogger().info("[MM-START] Selected map: " + map.name() + " (world: " + map.world() + ")");
        assignRoles();
        alive.clear();
        alive.addAll(queue.keySet());
        plugin.getLogger().info("[MM-START] Assigned roles - Murderer: " + (murdererId != null ? Bukkit.getPlayer(murdererId).getName() : "NONE") + " | Detective: " + (detectiveId != null ? Bukkit.getPlayer(detectiveId).getName() : "NONE"));
        // Detective status & bow status
        scoreboardManager.setDetectiveAlive(true);
        scoreboardManager.setBowDropped(false);
        
        // Initialize gold spawning - but first clear any stray gold from the world
        goldManager.reset();
        if (goldSpawnManager != null) {
            goldSpawnManager.stopSpawning();
        }
        // Clear any lingering gold items in the map world
        World gameWorld = map.world();
        if (gameWorld != null) {
            for (Item item : gameWorld.getEntitiesByClass(Item.class)) {
                if (item.getItemStack().getType() == Material.GOLD_INGOT) {
                    item.remove();
                }
            }
        }
        goldSpawnManager = new GoldSpawnManager(plugin, map.gameSpawns(), queue);
        // Do NOT start spawning yet - wait until after grace period
        
        // Setup scoreboard
        scoreboardManager.setState(GameState.IN_GAME);
        scoreboardManager.setMapName(map.name());
        roundSecondsLeft = (int) preset.roundLength().getSeconds();
        scoreboardManager.setTimeLeft(roundSecondsLeft);
        updateInnocentsCount();
        heartbeatPublisher.sendHeartbeat();
        
        // Start time tracker
        if (timeUpdateTask != null) {
            timeUpdateTask.cancel();
        }
        timeUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (roundSecondsLeft > 0) {
                roundSecondsLeft--;
                scoreboardManager.setTimeLeft(roundSecondsLeft);
            }
        }, 20L, 20L); // Every second
        
        teleportAndLoadoutPlayers(map);
        scheduleRoundTimer();
        broadcastToQueue(ChatColor.GOLD + "Round started on map " + ChatColor.YELLOW + map.name());
    }

    private void assignRoles() {
        List<PlayerProfile> profiles = new ArrayList<>(queue.values());
        Collections.shuffle(profiles);
        if (profiles.isEmpty()) {
            return;
        }
        PlayerProfile murderer = profiles.get(0);
        murderer.lastRole(MurderRole.MURDERER);
        murdererId = murderer.uniqueId();
        goldManager.setPlayerRole(murderer.uniqueId(), MurderRole.MURDERER);
        scoreboardManager.setPlayerRole(murderer.uniqueId(), MurderRole.MURDERER);
        plugin.getLogger().info("[MM] Assigned MURDERER: " + Bukkit.getPlayer(murderer.uniqueId()).getName());
        
        if (profiles.size() > 1) {
            PlayerProfile det = profiles.get(1);
            det.lastRole(MurderRole.DETECTIVE);
            detectiveId = det.uniqueId();
            goldManager.setPlayerRole(det.uniqueId(), MurderRole.DETECTIVE);
            scoreboardManager.setPlayerRole(det.uniqueId(), MurderRole.DETECTIVE);
            plugin.getLogger().info("[MM] Assigned DETECTIVE: " + Bukkit.getPlayer(det.uniqueId()).getName());
        }
        for (int i = 2; i < profiles.size(); i++) {
            profiles.get(i).lastRole(MurderRole.INNOCENT);
            goldManager.setPlayerRole(profiles.get(i).uniqueId(), MurderRole.INNOCENT);
            scoreboardManager.setPlayerRole(profiles.get(i).uniqueId(), MurderRole.INNOCENT);
            Player p = Bukkit.getPlayer(profiles.get(i).uniqueId());
            if (p != null) {
                plugin.getLogger().info("[MM] Assigned INNOCENT: " + p.getName());
            }
        }
    }

    private void teleportAndLoadoutPlayers(MapDefinition map) {
        plugin.getLogger().info("[MM] teleportAndLoadoutPlayers() called for map: " + map.name());
        // Assign unique spawns to each player
        List<Location> availableSpawns = new ArrayList<>(map.gameSpawns());
        java.util.Collections.shuffle(availableSpawns);
        plugin.getLogger().info("[MM] Available spawns count: " + availableSpawns.size() + ", queue size: " + queue.size());
        
        int spawnIndex = 0;
        for (UUID playerId : new ArrayList<>(queue.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                plugin.getLogger().warning("[MM] Player not found for UUID: " + playerId);
                continue;
            }
            
            // Try to find a safe spawn, cycling through available spawns if needed
            Location safeSpawn = null;
            int attempts = 0;
            int maxAttempts = Math.min(availableSpawns.size(), 10); // Try up to 10 spawns
            
            while (safeSpawn == null && attempts < maxAttempts) {
                Location candidateSpawn = availableSpawns.get(spawnIndex % availableSpawns.size());
                safeSpawn = checkAndGetSafeSpawn(candidateSpawn, map.name());
                if (safeSpawn == null) {
                    spawnIndex++; // Try next spawn
                }
                attempts++;
            }
            
            // Fallback if no safe spawn found
            if (safeSpawn == null) {
                plugin.getLogger().severe("[MM] NO SAFE SPAWNS FOUND! Using fallback for " + player.getName());
                safeSpawn = availableSpawns.get(spawnIndex % availableSpawns.size());
            }
            
            plugin.getLogger().info("[MM] Teleporting " + player.getName() + " to spawn at Y=" + safeSpawn.getY());
            spawnIndex++;
            
            player.teleport(safeSpawn);
            
            // CRITICAL: Reset movement speeds to 1.8.9 defaults (version-neutral)
            // Walk speed: 0.2 (1.8.9 default), Fly speed: 0.1 (1.8.9 default)
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setMaxHealth(20.0); // Show 10 hearts
            player.setHealth(20.0); // Start with full health
            // Do NOT clear inventory - preserve any gold items
            
            PlayerProfile profile = queue.get(playerId);
            if (profile != null) {
                // Send role title immediately
                sendRoleTitle(player, profile.lastRole());
                plugin.getLogger().info("[MM] Sent role title to " + player.getName() + ": " + profile.lastRole());
                // Grace period: no equipment yet, will be given after grace period
            }
        }
        
        // Wait 10s, then 5s grace countdown with message/sound, then give loadouts
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            new org.bukkit.scheduler.BukkitRunnable() {
                int grace = 5;
                @Override
                public void run() {
                    if (grace <= 0) {
                        cancel();
                        plugin.getLogger().info("[MM] Grace period over, giving equipment...");
                        for (UUID playerId : new ArrayList<>(queue.keySet())) {
                            Player player = Bukkit.getPlayer(playerId);
                            if (player == null) continue;
                            PlayerProfile profile = queue.get(playerId);
                            if (profile != null) {
                                plugin.getLogger().info("[MM] Giving loadout to " + player.getName() + " as " + profile.lastRole());
                                giveLoadout(player, profile.lastRole());
                                verifyLoadout(player.getUniqueId(), profile.lastRole());
                            }
                        }
                        loadoutsGiven = true;
                        ensureRoleItemsForAll();
                        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureRoleItemsForAll(), 40L); // re-ensure after 2s
                        // Start gold spawning now that weapons are given
                        if (goldSpawnManager != null) {
                            goldSpawnManager.startSpawning();
                        }
                        // Hide player names via empty scoreboard (after equipment is given)
                        for (UUID playerId : new ArrayList<>(queue.keySet())) {
                            Player player = Bukkit.getPlayer(playerId);
                            if (player != null) {
                                player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                            }
                        }
                        broadcastToQueue(ChatColor.YELLOW + "Grace period over! Weapons enabled!");
                        return;
                    }
                    String msg = ChatColor.YELLOW + "The " + ChatColor.RED + "Murderer" + ChatColor.YELLOW + " will receive their knife in " + grace + "...";
                    for (UUID pid : queue.keySet()) {
                        Player p = Bukkit.getPlayer(pid);
                        if (p != null) {
                            try {
                                p.playSound(p.getLocation(), resolveChimeSound(), 1.0f, 1.0f);
                            } catch (Throwable ignored) {}
                            p.sendMessage(msg);
                        }
                    }
                    grace--;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }, 200L); // 10 seconds before grace countdown starts
    }

    private Location resolveSpawnFor(UUID playerId, MapDefinition map) {
        PlayerProfile profile = queue.get(playerId);
        if (profile == null) {
            return lobbySpawn.clone().add(0, lobbySpawnOffsetY, 0);
        }
        List<Location> spawns = map.gameSpawns();
        if (spawns.isEmpty()) {
            return lobbySpawn.clone().add(0, lobbySpawnOffsetY, 0);
        }
        int idx = Math.abs(playerId.hashCode()) % spawns.size();
        return spawns.get(idx);
    }

    private void giveLoadout(Player player, MurderRole role) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        
        // Reserve key inventory slots
        inv.setItem(4, new ItemStack(Material.MAP)); // Slot 5: Map
        
        // Gesture item - make non-placeable
        ItemStack gesture = new ItemStack(Material.ARMOR_STAND);
        ItemMeta gestureMeta = gesture.getItemMeta();
        if (gestureMeta != null) {
            gestureMeta.setDisplayName(ChatColor.YELLOW + "Gesture");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Right-click to perform gesture");
            gestureMeta.setLore(lore);
            gesture.setItemMeta(gestureMeta);
        }
        inv.setItem(6, gesture); // Slot 7: Gesture
        // Slot 8 (index 8) reserved for gold collection - leave empty
        
        switch (role) {
            case MURDERER -> {
                ItemStack sword = createMurdererSword();
                plugin.getLogger().info("[MM] MURDERER LOADOUT: Creating sword for " + player.getName());
                inv.setItem(1, sword); // Slot 2
                plugin.getLogger().info("[MM] MURDERER LOADOUT: Set sword to slot 1");
                inv.setHeldItemSlot(1); // Keep weapon visible in slot 2
                plugin.getLogger().info("[MM] MURDERER LOADOUT: Set held slot to 1");
                // Verify immediately
                ItemStack verify = inv.getItem(1);
                plugin.getLogger().info("[MM] MURDERER LOADOUT VERIFY: Slot 1 contains: " + (verify != null ? verify.getType() : "NULL"));
            }
            case DETECTIVE -> {
                ItemStack bow = createDetectiveBow();
                plugin.getLogger().info("[MM] DETECTIVE LOADOUT: Creating bow for " + player.getName());
                inv.setItem(1, bow); // Slot 2
                plugin.getLogger().info("[MM] DETECTIVE LOADOUT: Set bow to slot 1");
                ItemStack arrow = new ItemStack(Material.ARROW);
                inv.setItem(9, arrow); // Slot 10 (off-hotbar)
                plugin.getLogger().info("[MM] DETECTIVE LOADOUT: Set arrow to slot 9");
                inv.setHeldItemSlot(1); // Hold bow in slot 2
                plugin.getLogger().info("[MM] DETECTIVE LOADOUT: Set held slot to 1");
                // Verify immediately
                ItemStack verifyBow = inv.getItem(1);
                ItemStack verifyArrow = inv.getItem(9);
                plugin.getLogger().info("[MM] DETECTIVE LOADOUT VERIFY: Slot 1=" + (verifyBow != null ? verifyBow.getType() : "NULL") + ", Slot 9=" + (verifyArrow != null ? verifyArrow.getType() : "NULL"));
            }
            default -> {
                // Innocents get nothing extra
            }
        }

        // Redundant saturation refill
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    private void verifyLoadout(UUID playerId, MurderRole role) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p == null) return;
            PlayerInventory inv = p.getInventory();
            plugin.getLogger().info("[MM] VERIFY_LOADOUT: Checking " + p.getName() + " as " + role);
            if (role == MurderRole.MURDERER) {
                ItemStack sword = inv.getItem(1);
                plugin.getLogger().info("[MM] VERIFY_LOADOUT: Murderer slot 1 contains: " + (sword != null ? sword.getType() : "NULL"));
                if (sword == null || sword.getType() != Material.IRON_SWORD) {
                    plugin.getLogger().warning("[MM] VERIFY_LOADOUT: Murderer missing sword! Re-giving...");
                    inv.setItem(1, new ItemStack(Material.IRON_SWORD));
                } else {
                    plugin.getLogger().info("[MM] VERIFY_LOADOUT: Murderer sword OK");
                }
                p.getInventory().setHeldItemSlot(1);
            } else if (role == MurderRole.DETECTIVE) {
                ItemStack bow = inv.getItem(1);
                plugin.getLogger().info("[MM] VERIFY_LOADOUT: Detective slot 1 contains: " + (bow != null ? bow.getType() : "NULL"));
                if (bow == null || bow.getType() != Material.BOW) {
                    plugin.getLogger().warning("[MM] VERIFY_LOADOUT: Detective missing bow! Re-giving...");
                    inv.setItem(1, createDetectiveBow());
                } else {
                    plugin.getLogger().info("[MM] VERIFY_LOADOUT: Detective bow OK");
                }
                ItemStack arrow = inv.getItem(10);
                plugin.getLogger().info("[MM] VERIFY_LOADOUT: Detective slot 10 contains: " + (arrow != null ? arrow.getType() : "NULL"));
                if (arrow == null || arrow.getType() != Material.ARROW) {
                    plugin.getLogger().warning("[MM] VERIFY_LOADOUT: Detective missing arrow! Re-giving...");
                    inv.setItem(10, new ItemStack(Material.ARROW));
                } else {
                    plugin.getLogger().info("[MM] VERIFY_LOADOUT: Detective arrow OK");
                }
                p.getInventory().setHeldItemSlot(1);
            }
        }, VERIFY_LOADOUT_DELAY_TICKS);
    }

    private void ensureRoleItemsForAll() {
        for (UUID pid : new ArrayList<>(queue.keySet())) {
            Player p = Bukkit.getPlayer(pid);
            PlayerProfile profile = queue.get(pid);
            if (p == null || profile == null) continue;
            ensureRoleItems(p, profile.lastRole());
        }
    }

    private void ensureRoleItems(Player p, MurderRole role) {
        PlayerInventory inv = p.getInventory();
        plugin.getLogger().info("[MM] ENSURE_ROLE_ITEMS: Checking " + p.getName() + " as " + role);
        if (role == MurderRole.MURDERER) {
            ItemStack sword = inv.getItem(1);
            plugin.getLogger().info("[MM] ENSURE_ROLE_ITEMS: Murderer slot 1 = " + (sword != null ? sword.getType() : "NULL"));
            if (sword == null || sword.getType() != Material.IRON_SWORD) {
                plugin.getLogger().warning("[MM] ENSURE_ROLE_ITEMS: Re-giving murderer sword");
                inv.setItem(1, new ItemStack(Material.IRON_SWORD));
            }
            inv.setHeldItemSlot(1);
        } else if (role == MurderRole.DETECTIVE) {
            ItemStack bow = inv.getItem(1);
            plugin.getLogger().info("[MM] ENSURE_ROLE_ITEMS: Detective slot 1 = " + (bow != null ? bow.getType() : "NULL"));
            if (bow == null || bow.getType() != Material.BOW) {
                plugin.getLogger().warning("[MM] ENSURE_ROLE_ITEMS: Re-giving detective bow");
                inv.setItem(1, createDetectiveBow());
            }
            ItemStack arrow = inv.getItem(10);
            plugin.getLogger().info("[MM] ENSURE_ROLE_ITEMS: Detective slot 10 = " + (arrow != null ? arrow.getType() : "NULL"));
            if (arrow == null || arrow.getType() != Material.ARROW) {
                plugin.getLogger().warning("[MM] ENSURE_ROLE_ITEMS: Re-giving detective arrow");
                inv.setItem(10, new ItemStack(Material.ARROW));
            }
            inv.setHeldItemSlot(1);
        }
        p.setFoodLevel(20);
        p.setSaturation(20f);
    }

    /**
     * Create murderer sword with 1.8.9-locked combat mechanics.
     * Removes attack speed attributes for 1.9+ clients.
     */
    private ItemStack createMurdererSword() {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = sword.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Murderer's Blade");
            
            // Remove attack speed attribute for 1.9+ clients
            // This prevents visual cooldown bar and ensures instant attacks
            try {
                // Use reflection to avoid compilation errors on 1.8.8
                Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
                Object attackSpeedAttr = attributeClass.getField("GENERIC_ATTACK_SPEED").get(null);
                
                Class<?> modifierClass = Class.forName("org.bukkit.attribute.AttributeModifier");
                Class<?> operationClass = Class.forName("org.bukkit.attribute.AttributeModifier$Operation");
                Object addOp = operationClass.getField("ADD_NUMBER").get(null);
                
                // Create modifier that adds 1000 to attack speed (effectively removes cooldown)
                java.lang.reflect.Constructor<?> constructor = modifierClass.getConstructor(
                    java.util.UUID.class, String.class, double.class, operationClass
                );
                Object modifier = constructor.newInstance(
                    java.util.UUID.randomUUID(),
                    "generic.attackSpeed",
                    1000.0,
                    addOp
                );
                
                // Add to item meta
                java.lang.reflect.Method addAttr = meta.getClass().getMethod(
                    "addAttributeModifier",
                    attributeClass,
                    modifierClass
                );
                addAttr.invoke(meta, attackSpeedAttr, modifier);
                
                plugin.getLogger().info("[COMBAT-1.8.9] Added attack speed override to murderer sword (1.9+ support)");
            } catch (Throwable e) {
                // 1.8.8 doesn't have attributes - this is expected and fine
                plugin.getLogger().info("[COMBAT-1.8.9] Skipped attack speed attribute (1.8.8 server)");
            }
            
            sword.setItemMeta(meta);
        }
        
        return sword;
    }
    
    private ItemStack createDetectiveBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        try {
            ItemMeta meta = bow.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("Detective Bow");
                bow.setItemMeta(meta);
            }
        } catch (Throwable ignored) {}
        return bow;
    }

    private void giveMap(Player player) {
        ItemStack map = new ItemStack(Material.MAP);
        player.getInventory().setItem(4, map); // Slot 5
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    private void sendRoleTitle(Player player, MurderRole role) {
        String title;
        String subtitle;
        switch (role) {
            case MURDERER:
                title = ChatColor.RED + "MURDERER";
                subtitle = ChatColor.RED + "Eliminate all innocent players!";
                break;
            case DETECTIVE:
                title = ChatColor.BLUE + "DETECTIVE";
                subtitle = ChatColor.BLUE + "Find and shoot the murderer!";
                break;
            case INNOCENT:
            default:
                title = ChatColor.GREEN + "INNOCENT";
                subtitle = ChatColor.GREEN + "Stay alive until the end!";
                break;
        }
        player.sendTitle(title, subtitle);
    }

    private MapDefinition pickMap() {
        if (nextMap != null) {
            MapDefinition result = nextMap;
            nextMap = null;
            return result;
        }
        if (!maps.isEmpty()) {
            // Prefer the configured default map name from preset
            for (MapDefinition m : maps) {
                if (m.name().equalsIgnoreCase(preset.mapName())) {
                    return m;
                }
            }
            // Fallback to random if the preset name isn't found
            return maps.get((int) (Math.random() * maps.size()));
        }
        // Final fallback: minimal map using lobby location
        return new MapDefinition(preset.mapName(), preset.mapName(), lobbySpawn, List.of(lobbySpawn), null);
    }

    private void scheduleRoundTimer() {
        long ticks = preset.roundLength().getSeconds() * 20L;
        roundEndTask = Bukkit.getScheduler().runTaskLater(plugin, () -> endRound("Time ran out. Innocents win.", "YOU WIN", ChatColor.GREEN), ticks);
    }

    private void broadcastToQueue(String message) {
        for (UUID playerId : queue.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
                player.playSound(player.getLocation(), resolveChimeSound(), 1.0f, 1.0f);
            }
        }
    }

    private Sound resolveChimeSound() {
        // Prefer modern sound names but fall back to legacy if running on 1.8-era servers.
        try {
            return Sound.valueOf("BLOCK_NOTE_BLOCK_HAT");
        } catch (IllegalArgumentException ignored) {
            try {
                return Sound.valueOf("NOTE_STICKS");
            } catch (IllegalArgumentException ignoredAgain) {
                return Sound.valueOf("CLICK");
            }
        }
    }

    private void updateState(GameState newState) {
        GameState previous = this.state;
        this.state = newState;
        PhaseChangeEvent event = new PhaseChangeEvent(previous, newState);
        plugin.getLogger().info(event.description());
        scoreboardManager.setState(newState);
        heartbeatPublisher.sendHeartbeat();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getLogger().info("[MM] DEBUG: onDeath fired for " + event.getEntity().getName() + ", state=" + state);
        if (state != GameState.IN_GAME) return;
        UUID id = event.getEntity().getUniqueId();
        plugin.getLogger().info("[MM] DEBUG: Player death during game: " + event.getEntity().getName() + ", was alive: " + alive.contains(id));
        if (alive.remove(id)) {
            // Prevent drops and respawn screen
            event.getDrops().clear();
            event.setDeathMessage(null);
            
            Player deadPlayer = event.getEntity();
            plugin.getLogger().info("[MM] DEBUG: Processing death for " + deadPlayer.getName());
            
            // Set spectator mode immediately (don't teleport - keep at death location for spectating)
            deadPlayer.setGameMode(GameMode.SPECTATOR);
            plugin.getLogger().info("[MM] DEBUG: Set " + deadPlayer.getName() + " to spectator mode at death location");
            
            // Play hurt + death sound to ALL players in the game
            Location deathLoc = deadPlayer.getLocation();
            for (UUID playerId : queue.keySet()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    try {
                        player.playSound(deathLoc, Sound.valueOf("ENTITY_PLAYER_HURT"), 1.0f, 1.0f);
                        player.playSound(deathLoc, Sound.valueOf("ENTITY_PLAYER_DEATH"), 1.0f, 0.8f);
                        plugin.getLogger().info("[MM] DEBUG: Played death sounds to " + player.getName());
                    } catch (Exception e) {
                        plugin.getLogger().warning("[MM] DEBUG: Failed to play death sound to " + player.getName() + ": " + e.getMessage());
                    }
                }
            }
            broadcastToQueue(ChatColor.RED + event.getEntity().getName() + ChatColor.YELLOW + " has fallen.");
            // Spawn a corpse
            if (corpseManager != null && corpseManager.isAvailable()) {
                corpseManager.spawnCorpse(deadPlayer, deathLoc);
                plugin.getLogger().info("[MM] DEBUG: Spawned corpse for " + deadPlayer.getName());
            } else {
                plugin.getLogger().info("[MM] DEBUG: CorpseManager unavailable, no corpse spawned");
            }
            // If detective died, drop bow and update scoreboard
            PlayerProfile profile = queue.get(id);
            if (profile != null && profile.lastRole() == MurderRole.DETECTIVE) {
                plugin.getLogger().info("[MM] DEBUG: Detective died, dropping bow");
                scoreboardManager.setDetectiveAlive(false);
                bowDropManager.dropBow(deathLoc);
                scoreboardManager.setBowDropped(true);
                // Title + chat message
                for (UUID pid : queue.keySet()) {
                    Player p = Bukkit.getPlayer(pid);
                    if (p != null) {
                        p.sendTitle(ChatColor.GOLD + "Bow Dropped!", ChatColor.YELLOW + "Find it to defend!");
                    }
                }
                broadcastToQueue(ChatColor.YELLOW + "The detective's bow has been dropped!");
            }
            updateInnocentsCount();
            checkWinConditions();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getLogger().info("[MM] DEBUG: onQuit fired for " + event.getPlayer().getName() + ", state=" + state);
        if (state != GameState.IN_GAME) return;
        UUID id = event.getPlayer().getUniqueId();
        if (alive.remove(id)) {
            plugin.getLogger().info("[MM] DEBUG: Removed " + event.getPlayer().getName() + " from alive list");
            broadcastToQueue(event.getPlayer().getName() + " disconnected.");
            updateInnocentsCount();
            checkWinConditions();
        }
    }

    private void updateInnocentsCount() {
        int innocents = 0;
        for (UUID playerId : alive) {
            PlayerProfile profile = queue.get(playerId);
            // Count all non-murderers (innocents + detective)
            if (profile != null && profile.lastRole() != MurderRole.MURDERER) {
                innocents++;
            }
        }
        plugin.getLogger().info("[MM] DEBUG: updateInnocentsCount - counted " + innocents + " non-murderers");
        scoreboardManager.setInnocentsLeft(innocents);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        // Cancel ALL drops at any time
        plugin.getLogger().info("[MM] DEBUG: Blocked item drop from " + event.getPlayer().getName() + ": " + event.getItemDrop().getItemStack().getType());
        event.setCancelled(true);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() != null && event.getRecipe().getResult() != null &&
                event.getRecipe().getResult().getType() == Material.BOW) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Only lock inventory during active gameplay, allow setup during grace period
        if (state == GameState.IN_GAME && event.getWhoClicked() instanceof Player) {
            plugin.getLogger().info("[MM] DEBUG: Blocked inventory click from " + event.getWhoClicked().getName() + ", loadoutsGiven=" + loadoutsGiven);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onArrowPickup(org.bukkit.event.player.PlayerPickupItemEvent event) {
        if (state != GameState.IN_GAME) return;
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Only block if player is in the game
        if (!queue.containsKey(playerId)) return;
        
        // Block picking up arrows on the ground (from missed shots)
        if (event.getItem().getItemStack().getType() == Material.ARROW) {
            PlayerProfile profile = queue.get(playerId);
            if (profile != null && profile.lastRole() != MurderRole.DETECTIVE) {
                event.setCancelled(true);
                plugin.getLogger().info("[MM] DEBUG: Blocked " + player.getName() + " from picking up arrow (not detective)");
            }
        }
    }

    // ===== SWORD THROWING SYSTEM =====
    // Sword throwing is now handled by SwordThrowHandler in arena/special/
    // See SwordThrowHandler.java for the implementation

    // REMOVED: Old knife handler - see SwordThrowHandler for current implementation
    /*
    @EventHandler
    public void onKnifeUse_REMOVED_OLD(PlayerInteractEvent e) {
        plugin.getLogger().info("[MM] DEBUG: onKnifeUse: action=" + e.getAction() + ", cancelled=" + e.isCancelled());
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            plugin.getLogger().info("[MM] DEBUG: onKnifeUse: not a right-click, ignoring");
            return;
        }

        Player player = e.getPlayer();
        PlayerInventory inv = player.getInventory();
        ItemStack inHand = player.getItemInHand();
        plugin.getLogger().info("[MM] DEBUG: onKnifeUse: player=" + player.getName() + ", item=" + (inHand != null ? inHand.getType() : "NULL") + ", state=" + state);
        if (inHand == null || inHand.getType() != Material.IRON_SWORD) {
            plugin.getLogger().info("[MM] DEBUG: onKnifeUse: item is not IRON_SWORD, ignoring");
            return; // Knife is iron sword
        }

        // Allow test in lobby if player opted in
        boolean tester = knifeTesters.contains(player.getUniqueId());
        plugin.getLogger().info("[MM] DEBUG: onKnifeUse: tester=" + tester + ", inGame=" + (state == GameState.IN_GAME));
        if (state == GameState.IN_GAME) {
            PlayerProfile profile = queue.get(player.getUniqueId());
            if (profile == null || profile.lastRole() != MurderRole.MURDERER) {
                plugin.getLogger().info("[MM] DEBUG: onKnifeUse: not murderer in-game, ignoring");
                return;
            }
        } else if (!tester) {
            plugin.getLogger().info("[MM] DEBUG: onKnifeUse: not tester in lobby, ignoring");
            return; // Not in-game and not a tester
        }

        long now = System.currentTimeMillis();
        long last = knifeCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long delta = now - last;
        if (!canThrowKnife(player)) {
            plugin.getLogger().info("[MM] DEBUG: onKnifeUse: cooldown delta=" + delta + "ms (need > " + knifeCooldownMs + "ms)");
            plugin.getLogger().info("[MM] DEBUG: onKnifeUse: cooldown not ready for " + player.getName());
            player.sendMessage(ChatColor.RED + "Knife is on cooldown.");
            return; // Cooldown not ready
        }
        // Begin 1-second charge with three blips, then throw
        if (knifeCharging.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Already charging throw...");
            return;
        }
        knifeCharging.add(player.getUniqueId());
        // Start cooldown immediately to prevent spam during charge and show HUD
        knifeCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        startKnifeCooldown(player.getUniqueId());
        // Dampen momentum at the start of charging (Hypixel-style feel)
        try {
            Vector v = player.getVelocity();
            player.setVelocity(v.multiply(0.25)); // sharply reduce current movement
            try { player.setSprinting(false); } catch (Throwable ignored) {}
            plugin.getLogger().info("[MM] DEBUG: onKnifeUse: momentum damped at charge start for " + player.getName());
        } catch (Throwable ignored) {}
        plugin.getLogger().info("[MM] DEBUG: onKnifeUse: starting 1s charge for " + player.getName());
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); knifeCharging.remove(player.getUniqueId()); return; }
                
                // Cancel charge if sword is no longer in hand
                ItemStack inHand = player.getItemInHand();
                if (inHand == null || inHand.getType() != Material.IRON_SWORD) {
                    plugin.getLogger().info("[MM] DEBUG: onKnifeUse charge cancelled - sword no longer in hand");
                    knifeCharging.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                
                t++;
                try {
                    // Four HAT blips at much lower pitches; 4th overlaps with throw
                    if (t == 5)  player.playSound(player.getLocation(), resolveBlipSound(), 1.0f, 0.60f);
                    if (t == 10) player.playSound(player.getLocation(), resolveBlipSound(), 1.0f, 0.70f);
                    if (t == 15) player.playSound(player.getLocation(), resolveBlipSound(), 1.0f, 0.80f);
                    if (t == 20) player.playSound(player.getLocation(), resolveBlipSound(), 1.0f, 0.90f);
                } catch (Throwable ignored) {}
                if (t >= 20) {
                    try { player.playSound(player.getLocation(), resolveThrowSound(), 1.0f, 0.7f); } catch (Throwable ignored) {}
                    if (state != GameState.IN_GAME) {
                        player.sendMessage(ChatColor.AQUA + "Knife thrown (test mode)");
                    }
                    throwKnife(player);
                    knifeCharging.remove(player.getUniqueId());
                    try { player.sendMessage(org.bukkit.ChatColor.GREEN + "THROW!"); } catch (Throwable ignored) {}
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean canThrowKnife(Player p) {
        long last = knifeCooldowns.getOrDefault(p.getUniqueId(), 0L);
        return (System.currentTimeMillis() - last) > knifeCooldownMs;
    }

    private void throwKnife(Player player) {
        plugin.getLogger().info("[MM] DEBUG: throwKnife: start for " + player.getName() + ", state=" + state);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        plugin.getLogger().info("[MM] DEBUG: throwKnife: eye loc=" + eye.getX() + "," + eye.getY() + "," + eye.getZ() + " direction=" + direction.getX() + "," + direction.getY() + "," + direction.getZ());
        double maxDistance = knifeMaxRange;
        // In tester/lobby mode, allow longer ray so visuals and collision can reach far walls
        if (state != GameState.IN_GAME && knifeTesters.contains(player.getUniqueId())) {
            maxDistance = Math.max(maxDistance, 50.0); // extend test reach without affecting live games
        }
        double step = knifeStep;
        org.bukkit.World world = eye.getWorld();
        double impactDist = maxDistance;

        for (double d = 0; d <= maxDistance; d += step) {
            Location point = eye.clone().add(direction.clone().multiply(d));
            // Stop at first solid block
            org.bukkit.block.Block block = point.getBlock();
            if (block != null && block.getType().isSolid()) {
                String name = block.getType().name().toUpperCase();
                if (name.contains("PANE") || name.contains("GLASS")) {
                    // allow ray to pass through glass/panes like the visual knife
                } else {
                    plugin.getLogger().info("[MM] DEBUG: throwKnife: hit blocking block " + name + " at d=" + d + " loc=" + block.getLocation());
                    impactDist = d;
                    break;
                }
            }
            // Visual: simple particle trail
            try {
                world.playEffect(point, org.bukkit.Effect.CRIT, 0);
                if (((int)(d * 10)) % 4 == 0) { // every ~0.4 blocks
                    world.playEffect(point, org.bukkit.Effect.SMOKE, 0);
                    try { world.playEffect(point, org.bukkit.Effect.STEP_SOUND, org.bukkit.Material.IRON_BLOCK); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            // Player collision: only during actual gameplay
            if (state == GameState.IN_GAME) {
            for (UUID targetId : new ArrayList<>(alive)) {
                if (targetId.equals(player.getUniqueId())) continue;
                Player target = Bukkit.getPlayer(targetId);
                if (target == null) continue;
                // Ignore spectators or non-participants
                if (target.getGameMode() == GameMode.SPECTATOR) continue;
                if (!queue.containsKey(targetId)) continue;
                // Expand hitbox slightly for forgiveness using distance approximation
                if (point.getWorld().equals(target.getWorld())) {
                    Location base = target.getLocation();
                    // Check near torso height for better feel
                    Location torso = base.clone().add(0, 1.0, 0);
                    double dTorso = point.distance(torso);
                    double dBase = point.distance(base);
                    if (dTorso <= 0.6 || dBase <= 0.6) {
                            plugin.getLogger().info("[MM] DEBUG: throwKnife: hit player " + target.getName() + " at d=" + d);
                        killInstant(target, player);
                        return;
                    }
                }
            }
            }
        }
        plugin.getLogger().info("[MM] DEBUG: throwKnife: finished no hit for " + player.getName());

        // In lobby test mode, show a moving fake knife visual (armor stand helmet)
        if (state != GameState.IN_GAME && knifeTesters.contains(player.getUniqueId())) {
            spawnKnifeVisual(player, eye, direction, impactDist);
        }
    }
    */

    private void spawnKnifeVisual(Player player, Location eye, Vector dir, double impactDist) {
        try {
            org.bukkit.World world = eye.getWorld();
            plugin.getLogger().info("[MM] DEBUG: spawnKnifeVisual: eye=" + eye.getX() + "," + eye.getY() + "," + eye.getZ() + " dir=" + dir.getX() + "," + dir.getY() + "," + dir.getZ() + " impactDist=" + impactDist);
            
            // Offset spawn location to counteract hand rendering offset
            // Right hand renders offset in the direction perpendicular to throw
            // Get perpendicular vector to the throw direction (in XZ plane)
            Vector perp = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
            plugin.getLogger().info("[MM] DEBUG: spawnKnifeVisual: perpendicular offset vector=" + perp.getX() + "," + perp.getY() + "," + perp.getZ());
            
            // Spawn armor stand offset to the left (negative perpendicular) to compensate for right hand offset
            // Keep trajectory exactly on the eye ray; only visuals are offset/raised
            final double standHeightOffset = -0.65; // Lower visual by ~1 block while keeping trajectory on the eye ray
            org.bukkit.Location start = eye.clone();
            start.add(perp.clone().multiply(-0.3)); // Offset left to compensate for right hand (X and Z only)
            start.add(0, standHeightOffset, 0); // Raise visual spawn to avoid early floor contact
            
            org.bukkit.entity.ArmorStand stand = world.spawn(start, org.bukkit.entity.ArmorStand.class);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(false);
            stand.setSmall(false); // Full size
            stand.setBasePlate(false);
            stand.setCanPickupItems(false);
            stand.setArms(true); // Show arms to hold the sword
            
            // Place sword in hand for better orientation
            try {
                stand.setItemInHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD));
            } catch (Throwable t) {
                // Fallback to helmet if hand doesn't work
                stand.setHelmet(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD));
            }
            
            // Calculate yaw and pitch from direction
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
            float pitch = (float) Math.toDegrees(Math.asin(-dir.getY()));
            plugin.getLogger().info("[MM] DEBUG: spawnKnifeVisual: calculated yaw=" + yaw + " pitch=" + pitch);
            plugin.getLogger().info("[MM] DEBUG: spawnKnifeVisual: spawn at=" + start.getX() + "," + start.getY() + "," + start.getZ());
            
            final float finalYaw = yaw;
            final float finalPitch = pitch;
            final java.util.Set<org.bukkit.block.Block> glassBlocks = new java.util.HashSet<>();
            final org.bukkit.Location trajectoryStart = eye.clone(); // True ray origin (matches hit detection)

            // Ray-step once to find the true blocking distance (server-side), skipping glass/panes
            // Ray-trace from the true eye position (crosshair), not the lowered visual start
            final org.bukkit.Location rayOrigin = eye.clone();
            final double maxDistance = impactDist;
            final double rayStep = 0.1;
            double computedTarget = maxDistance;
            for (double d = 0.0; d <= maxDistance; d += rayStep) {
                org.bukkit.Location probe = rayOrigin.clone().add(dir.clone().multiply(d));
                org.bukkit.block.Block b = probe.getBlock();
                if (b != null && b.getType().isSolid()) {
                    String name = b.getType().name().toUpperCase();
                    if (name.contains("PANE") || name.contains("GLASS")) {
                        plugin.getLogger().info("[MM] DEBUG: raystep glass passthrough at d=" + String.format("%.2f", d) + " block=" + name + " loc=" + b.getLocation());
                        continue; // pass through glass and panes
                    }
                    plugin.getLogger().info("[MM] DEBUG: raystep first blocking block at d=" + String.format("%.2f", d) + " block=" + name + " loc=" + b.getLocation());
                    computedTarget = d;
                    break;
                }
            }
            final double targetDistance = computedTarget;
            plugin.getLogger().info("[MM] DEBUG: raystep chosen targetDistance=" + String.format("%.2f", targetDistance) + " (max=" + maxDistance + ")");
            
            new org.bukkit.scheduler.BukkitRunnable() {
                int t = 0;
                double distanceTraveled = 0.0;
                long startTime = System.currentTimeMillis();
                final double travelSpeed = 0.9; // Slightly slower visual movement per tick
                final double standHeightOffset = -0.65; // Lower visual to match trajectory height while leaving ray unchanged
                
                @Override
                public void run() {
                    if (!stand.isValid()) { cancel(); return; }
                    
                    // 30-second timeout for cleanup
                    if (System.currentTimeMillis() - startTime > 30000) {
                        plugin.getLogger().info("[MM] DEBUG: knifeVisual timeout after 30s, removing");
                        stand.remove();
                        cancel();
                        return;
                    }
                    
                    t++;
                    distanceTraveled += travelSpeed;
                    
                                        // KNIFE POINT: advance along the trajectory
                                        org.bukkit.Location knifePoint = trajectoryStart.clone().add(dir.clone().multiply(distanceTraveled));

                                        // If we've reached or passed the precomputed blocking distance, despawn
                                        if (distanceTraveled >= targetDistance) {
                                            plugin.getLogger().info("[MM] DEBUG: knifeVisual reached target distance=" + targetDistance + " at tick=" + t);
                                            stand.remove();
                                            cancel();
                                            return;
                                        }

                                        // Optional: play glass break effects when passing through panes/glass
                                        org.bukkit.block.Block currentBlock = knifePoint.getBlock();
                                        if (currentBlock != null && currentBlock.getType().isSolid()) {
                                            String blockName = currentBlock.getType().name().toUpperCase();
                                            if (blockName.contains("PANE") || blockName.contains("GLASS")) {
                                                if (!glassBlocks.contains(currentBlock)) {
                                                    glassBlocks.add(currentBlock);
                                                    plugin.getLogger().info("[MM] DEBUG: knifeVisual passed through glass pane at " + currentBlock.getLocation());
                                                    try {
                                                        currentBlock.getWorld().playSound(currentBlock.getLocation(), Sound.valueOf("BLOCK_GLASS_BREAK"), 1.0f, 1.0f);
                                                    } catch (Exception e) {
                                                        try {
                                                            currentBlock.getWorld().playSound(currentBlock.getLocation(), Sound.valueOf("ITEM_SHIELD_BREAK"), 0.5f, 1.0f);
                                                        } catch (Exception ignored) {}
                                                    }
                                                    // Show vanilla crack animation via packet (client-side only)
                                                    try {
                                                        final int id = currentBlock.getX() ^ currentBlock.getY() ^ currentBlock.getZ();
                                                        final int bx = currentBlock.getX();
                                                        final int by = currentBlock.getY();
                                                        final int bz = currentBlock.getZ();
                                                        // Reflect NMS classes to avoid compile-time dependency
                                                        Class<?> bpClass = Class.forName("net.minecraft.server.v1_8_R3.BlockPosition");
                                                        java.lang.reflect.Constructor<?> bpCtor = bpClass.getConstructor(int.class, int.class, int.class);
                                                        Object pos = bpCtor.newInstance(bx, by, bz);
                                                        Class<?> packetClass = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutBlockBreakAnimation");
                                                        java.lang.reflect.Constructor<?> pCtor = packetClass.getConstructor(int.class, bpClass, int.class);
                                                        Object crack = pCtor.newInstance(id, pos, 7);
                                                        // Send packet to player
                                                        java.lang.reflect.Method getHandle = player.getClass().getMethod("getHandle");
                                                        Object nmsPlayer = getHandle.invoke(player);
                                                        Object conn = nmsPlayer.getClass().getField("playerConnection").get(nmsPlayer);
                                                        Class<?> packetBase = Class.forName("net.minecraft.server.v1_8_R3.Packet");
                                                        java.lang.reflect.Method sendPacket = conn.getClass().getMethod("sendPacket", packetBase);
                                                        sendPacket.invoke(conn, crack);
                                                        // Clear after 20 ticks
                                                        new org.bukkit.scheduler.BukkitRunnable() {
                                                            @Override public void run() {
                                                                try {
                                                                    Class<?> bpClass2 = Class.forName("net.minecraft.server.v1_8_R3.BlockPosition");
                                                                    java.lang.reflect.Constructor<?> bpCtor2 = bpClass2.getConstructor(int.class, int.class, int.class);
                                                                    Object pos2 = bpCtor2.newInstance(bx, by, bz);
                                                                    Class<?> packetClass2 = Class.forName("net.minecraft.server.v1_8_R3.PacketPlayOutBlockBreakAnimation");
                                                                    java.lang.reflect.Constructor<?> pCtor2 = packetClass2.getConstructor(int.class, bpClass2, int.class);
                                                                    Object clear = pCtor2.newInstance(id, pos2, -1);
                                                                    java.lang.reflect.Method getHandle2 = player.getClass().getMethod("getHandle");
                                                                    Object nmsPlayer2 = getHandle2.invoke(player);
                                                                    Object conn2 = nmsPlayer2.getClass().getField("playerConnection").get(nmsPlayer2);
                                                                    Class<?> packetBase2 = Class.forName("net.minecraft.server.v1_8_R3.Packet");
                                                                    java.lang.reflect.Method sendPacket2 = conn2.getClass().getMethod("sendPacket", packetBase2);
                                                                    sendPacket2.invoke(conn2, clear);
                                                                } catch (Throwable ignored) {}
                                                            }
                                                        }.runTaskLater(plugin, 20L);
                                                    } catch (Throwable animErr) {
                                                        plugin.getLogger().info("[MM] DEBUG: glass crack animation unavailable: " + animErr.getClass().getSimpleName());
                                                    }
                                                }
                                            } else {
                                                // Debug any unexpected solid during flight
                                                plugin.getLogger().info("[MM] DEBUG: knifeVisual encountered solid mid-flight block=" + blockName + " at pos=" + String.format("%.2f,%.2f,%.2f", knifePoint.getX(), knifePoint.getY(), knifePoint.getZ()) + " tick=" + t + " dist=" + String.format("%.2f", distanceTraveled));
                                            }
                                        }
                    
                    // ARMOR STAND LOCATION: Offset above the knife point so hitbox never clips ground
                    // Apply perpendicular offset for hand positioning and vertical offset to keep clear of ground
                    org.bukkit.Location standLoc = knifePoint.clone();
                    standLoc.add(perp.clone().multiply(-0.3)); // Left offset for right-hand rendering
                    standLoc.setY(standLoc.getY() + standHeightOffset); // Lift armor stand above collision point
                    standLoc.setYaw(finalYaw);
                    standLoc.setPitch(finalPitch);
                    stand.teleport(standLoc);
                    
                    plugin.getLogger().info("[MM] DEBUG: knifeVisual tick=" + t + " dist=" + String.format("%.2f", distanceTraveled) + " pos=" + String.format("%.2f", standLoc.getX()) + "," + String.format("%.2f", standLoc.getY()) + "," + String.format("%.2f", standLoc.getZ()));
                }
            }.runTaskTimer(plugin, 0L, 1L);
        } catch (Throwable ex) {
            plugin.getLogger().warning("[MM] ERROR: spawnKnifeVisual failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private Sound resolveBlipSound() {
        try { return Sound.valueOf("BLOCK_NOTE_BLOCK_HAT"); } catch (IllegalArgumentException e) {
            try { return Sound.valueOf("NOTE_STICKS"); } catch (IllegalArgumentException e2) {
                return Sound.valueOf("CLICK");
            }
        }
    }

    private Sound resolveThrowSound() {
        try { return Sound.valueOf("ITEM_ARMOR_EQUIP_LEATHER"); } catch (IllegalArgumentException e) {
            try { return Sound.valueOf("ARMOR_EQUIP_LEATHER"); } catch (IllegalArgumentException e2) {
                return Sound.valueOf("ITEM_BREAK");
            }
        }
    }

    private Sound resolvePopSound() {
        try { return Sound.valueOf("ENTITY_EXPERIENCE_ORB_PICKUP"); } catch (IllegalArgumentException e) {
            try { return Sound.valueOf("ORB_PICKUP"); } catch (IllegalArgumentException e2) {
                try { return Sound.valueOf("ITEM_PICKUP"); } catch (IllegalArgumentException e3) {
                    return Sound.valueOf("CLICK");
                }
            }
        }
    }

    private void sendActionBar(Player p, String text) {
        // Simplified: just use chat messages since action bar rendering is version-dependent
        try {
            p.sendMessage(text);
        } catch (Throwable ignored) {}
    }

    private void startKnifeCooldown(UUID playerId) {
        final long totalMs = Math.max(500L, knifeCooldownMs);
        final long started = System.currentTimeMillis();
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(playerId);
                if (p == null) { cancel(); return; }
                long elapsed = System.currentTimeMillis() - started;
                long remaining = Math.max(0L, totalMs - elapsed);
                // Simplified cooldown indicator
                ChatColor color = remaining <= 500 ? ChatColor.GREEN : ChatColor.RED;
                p.sendMessage(color + "Knife CD: " + String.format("%.1fs", remaining/1000.0));
                if (remaining <= 0L) {
                    // Cooldown expired; ready to throw again (silently, no "Ready!" message)
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void killInstant(Player target, Player killer) {
        // Bypass armor/absorption and force death
        try {
            target.damage(1000.0, killer);
        } catch (Throwable ignored) {
            target.setHealth(0.0);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (state != GameState.IN_GAME) {
            // Prevent all damage during pre-game/countdown
            if (event.getEntity() instanceof Player) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getEntity() instanceof Player player) {
            // Prevent fall and fire damage during gameplay
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL ||
                event.getCause() == EntityDamageEvent.DamageCause.FIRE ||
                event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK) {
                event.setCancelled(true);
                return;
            }
            
            // For proper 1.8.9 PvP mechanics, let arrows and melee work naturally
            // but ensure instant death on any hit
            if (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE ||
                event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                // Allow the damage event to process naturally for 1.8.9 mechanics
                // Then trigger instant death
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && player.getHealth() > 0) {
                        player.setHealth(0); // Instant death
                    }
                });
            } else {
                // Any other damage source = instant death
                event.setDamage(20.0);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        // Disable hunger completely and keep saturation maxed
        event.setCancelled(true);
        if (event.getEntity() instanceof Player p) {
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // Only protect the active game world (mm_active)
        String worldName = event.getBlock().getWorld().getName();
        if (worldName.equals("mm_active")) {
            // Allow ops in creative mode to edit
            if (event.getPlayer().isOp() && event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
                return;
            }
            event.setCancelled(true);
        }
        // Template worlds (map_*) are not protected - admins can edit freely
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // Only protect the active game world (mm_active)
        String worldName = event.getBlock().getWorld().getName();
        if (worldName.equals("mm_active")) {
            // Allow ops in creative mode to edit
            if (event.getPlayer().isOp() && event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
                return;
            }
            event.setCancelled(true);
        }
        // Template worlds (map_*) are not protected - admins can edit freely
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        // CRITICAL: Prevent armor stand manipulation (corpses, sword visuals)
        // Version-neutral: applies to all armor stands regardless of client version
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // CRITICAL: Prevent damaging armor stands (corpses, sword visuals)
        // Ensures visual entities don't interfere with combat logic
        if (event.getEntity() instanceof org.bukkit.entity.ArmorStand) {
            event.setCancelled(true);
            return;
        }

        if (state != GameState.IN_GAME) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        plugin.getLogger().info("[COMBAT] Damage event - Victim: " + victim.getName() + " | Cause: " + event.getCause() + " | Raw damage: " + event.getDamage());

        // ===== MELEE COMBAT: 1.8.9-LOCKED MECHANICS =====
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && event.getDamager() instanceof Player attacker) {
            PlayerProfile attackerProfile = queue.get(attacker.getUniqueId());
            plugin.getLogger().info("[COMBAT-MELEE] Attacker: " + attacker.getName() + " | Role: " + (attackerProfile != null ? attackerProfile.lastRole() : "NONE"));
            
            // Block if attacker not in queue or not murderer
            if (attackerProfile == null) {
                plugin.getLogger().info("[COMBAT-MELEE] ✗ BLOCKED: Attacker not in queue");
                event.setCancelled(true);
                return;
            }
            if (attackerProfile.lastRole() != MurderRole.MURDERER) {
                plugin.getLogger().info("[COMBAT-MELEE] ✗ BLOCKED: Attacker not murderer (role: " + attackerProfile.lastRole() + ")");
                event.setCancelled(true);
                return;
            }
            
            // Murderer must be holding the knife (iron sword) to deal damage
            ItemStack inHand = attacker.getInventory().getItemInHand();
            plugin.getLogger().info("[COMBAT-MELEE] Weapon: " + (inHand != null ? inHand.getType() : "EMPTY"));
            if (inHand == null || inHand.getType() != Material.IRON_SWORD) {
                plugin.getLogger().info("[COMBAT-MELEE] ✗ BLOCKED: Not holding sword");
                event.setCancelled(true);
                return;
            }
            
            // CRITICAL: Enforce 1.8.9 reach limit (3.0 blocks)
            double distance = attacker.getLocation().distance(victim.getLocation());
            plugin.getLogger().info("[COMBAT-MELEE] Distance: " + String.format("%.2f", distance) + "b");
            if (distance > 3.0) {
                plugin.getLogger().warning("[COMBAT-MELEE] ✗ BLOCKED: Beyond 1.8.9 reach (>3.0b)");
                event.setCancelled(true);
                return;
            }
            
            // CRITICAL: Lock damage to instant kill (version-neutral)
            event.setDamage(20.0); // 10 hearts = instant death, matches 1.8.9 iron sword crit
            plugin.getLogger().info("[COMBAT-MELEE] ✓ HIT | Damage: 20.0 | Distance: " + String.format("%.2f", distance) + "b");
            
            // CRITICAL: Apply 1.8.9 knockback formula
            Vector knockback = calculate18Knockback(attacker.getLocation(), victim.getLocation(), attacker.isSprinting());
            victim.setVelocity(knockback);
            plugin.getLogger().info("[COMBAT-MELEE] Knockback: (" + String.format("%.2f", knockback.getX()) + ", " + String.format("%.2f", knockback.getY()) + ", " + String.format("%.2f", knockback.getZ()) + ")");
            
            // CRITICAL: Sprint reset (1.8.9 behavior)
            if (attacker.isSprinting()) {
                attacker.setSprinting(false);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (attacker.isOnline()) attacker.setSprinting(true);
                }, 1L);
            }
            
            // Reset attack cooldown for 1.9+ clients (visual only, doesn't affect damage)
            com.mmhq.game.utils.VersionUtils.setMaterialCooldown(attacker, Material.IRON_SWORD, 0);
        }

        // ===== PROJECTILE COMBAT: 1.8.9-LOCKED MECHANICS =====
        if (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE && event.getDamager() instanceof org.bukkit.entity.Projectile proj) {
            Object shooter = proj.getShooter();
            if (shooter instanceof Player shooterPlayer) {
                PlayerProfile shooterProfile = queue.get(shooterPlayer.getUniqueId());
                MurderRole shooterRole = shooterProfile != null ? shooterProfile.lastRole() : null;
                plugin.getLogger().info("[MM] DEBUG: Projectile from " + shooterPlayer.getName() + ", role: " + shooterRole);
                
                // Block if not in queue or not detective/murderer
                if (shooterProfile == null || (shooterRole != MurderRole.MURDERER && shooterRole != MurderRole.DETECTIVE)) {
                    plugin.getLogger().info("[MM] DEBUG: Blocked projectile from non-detective/murderer");
                    event.setCancelled(true);
                    return;
                }
                
                // CRITICAL: Lock arrow damage to instant kill (version-neutral)
                event.setDamage(20.0); // Detective arrow = instant death
                plugin.getLogger().info("[COMBAT-1.8.9] Projectile damage locked to 20.0 (instant kill)");
            }
        }
    }
    
    /**
     * Calculate 1.8.9-accurate knockback vector.
     * This ensures combat feels identical regardless of client version.
     */
    private Vector calculate18Knockback(Location attackerLoc, Location victimLoc, boolean sprint) {
        // 1.8.9 knockback formula
        Vector direction = victimLoc.toVector().subtract(attackerLoc.toVector()).normalize();
        direction.setY(0); // Horizontal component only initially
        
        // 1.8.9 base knockback values
        double horizontalKB = 0.4;
        double verticalKB = sprint ? 0.4 : 0.35;
        
        // Sprint adds 0.5 to horizontal knockback
        if (sprint) {
            horizontalKB += 0.5;
        }
        
        return direction.multiply(horizontalKB).setY(verticalKB);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Prevent container opening and other interactions
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null) {
                Material type = block.getType();
                // Prevent opening containers
                if (type.name().contains("CHEST") || 
                    type.name().contains("FURNACE") || 
                    type.name().contains("HOPPER") ||
                    type.name().contains("DROPPER") ||
                    type.name().contains("DISPENSER") ||
                    type == Material.ENDER_CHEST ||
                    type == Material.BREWING_STAND ||
                    type == Material.BEACON) {
                    event.setCancelled(true);
                }
            }
        }
    }

    private void checkWinConditions() {
        plugin.getLogger().info("[MM] DEBUG: checkWinConditions - alive count: " + alive.size());
        if (alive.isEmpty()) {
            plugin.getLogger().info("[MM] DEBUG: No players alive - game over");
            endRound("All players eliminated.", "GAME OVER", ChatColor.RED);
            return;
        }

        boolean murdererAlive = alive.contains(murdererId);
        long innocentsAlive = alive.stream()
                .map(queue::get)
                .filter(profile -> profile != null && profile.lastRole() != MurderRole.MURDERER)
                .count();

        plugin.getLogger().info("[MM] DEBUG: murdererAlive=" + murdererAlive + ", innocentsAlive=" + innocentsAlive);

        if (!murdererAlive) {
            plugin.getLogger().info("[MM] DEBUG: Murderer dead - innocents win");
            endRound("Innocents win! Murderer eliminated.", "YOU WIN", ChatColor.GREEN);
            return;
        }

        if (innocentsAlive == 0) {
            plugin.getLogger().info("[MM] DEBUG: All innocents dead - murderer wins");
            endRound("Murderer wins! All innocents are down.", "GAME OVER", ChatColor.RED);
        }
    }

    private void endRound(String reason, String title, ChatColor titleColor) {
        plugin.getLogger().info("[MM] DEBUG: endRound called - reason: " + reason + ", current state: " + state);
        if (state != GameState.IN_GAME) {
            plugin.getLogger().warning("[MM] DEBUG: endRound called but state is not IN_GAME, ignoring");
            return;
        }
        updateState(GameState.ENDING);
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (roundEndTask != null) {
            roundEndTask.cancel();
            roundEndTask = null;
        }
        if (timeUpdateTask != null) {
            timeUpdateTask.cancel();
            timeUpdateTask = null;
        }
        broadcastToQueue(ChatColor.GOLD + reason);
        // Send end-game title to all players
        for (UUID playerId : queue.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                String titleText = "" + titleColor + ChatColor.BOLD + title + ChatColor.RESET;
                player.sendTitle(titleText, "");
            }
        }
        plugin.getLogger().info("[MM] DEBUG: Scheduling reset to lobby in 20 seconds");
        // 20 second wait, then reset
        Bukkit.getScheduler().runTaskLater(plugin, this::resetToLobby, 400L); // 20 seconds
    }

    /**
     * Reset game to lobby state. Can be called externally from GameManager.
     */
    public void resetToLobby() {
        plugin.getLogger().info("[MM-RESET] ===== GAME RESET TO LOBBY =====");
        updateState(GameState.LOBBY);
        alive.clear();
        murdererId = null;
        detectiveId = null;
        currentMap = null;
        
        // Stop gold spawning and clear items
        if (goldSpawnManager != null) {
            plugin.getLogger().info("[MM-RESET] Stopping gold spawning");
            goldSpawnManager.stopSpawning();
        }
        // Clear bow drop and bodies
        if (bowDropManager != null) {
            plugin.getLogger().info("[MM-RESET] Clearing bow drops");
            bowDropManager.clear();
        }
        if (corpseManager != null) {
            plugin.getLogger().info("[MM-RESET] Clearing corpses");
            corpseManager.clearAll();
        }

        // Stop time updates
        if (timeUpdateTask != null) {
            timeUpdateTask.cancel();
            timeUpdateTask = null;
        }
        
        // Reset managers
        goldManager.reset();
        scoreboardManager.reset();
        scoreboardManager.setState(GameState.LOBBY);
        scoreboardManager.setDetectiveAlive(false);
        scoreboardManager.setBowDropped(false);
        
        // Get the current game world and clear all items on ground
        if (currentMap != null) {
            World gameWorld = currentMap.world();
            if (gameWorld != null) {
                int itemCount = 0;
                for (Item item : gameWorld.getEntitiesByClass(Item.class)) {
                    item.remove();
                    itemCount++;
                }
                plugin.getLogger().info("[MM-RESET] Cleared " + itemCount + " items from game world");
            }
        }
        // Clear corpses each reset (safety if any remain)
        if (corpseManager != null) corpseManager.clearAll();
        
        plugin.getLogger().info("[MM-RESET] Resetting " + queue.size() + " players to lobby");
        Location waitingLoc = getWaitingSpawn();
        for (UUID playerId : queue.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.getInventory().clear();
                player.setMaxHealth(20.0);
                player.setHealth(20.0);
                player.setFoodLevel(20);
                player.setSaturation(20f);
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                player.teleport(waitingLoc);
                plugin.getLogger().info("[MM-RESET] Reset " + player.getName() + " to waiting spawn");
            }
        }
        heartbeatPublisher.sendHeartbeat();
        plugin.getLogger().info("[MM-RESET] Complete - ready for new players");
    }

    private ServerStatus buildStatus() {
        // Some server implementations lack getServerName(); rely on config with a safe default
        String serverId = plugin.getConfig().getString("server.id", "mm-game");
        String mapName = nextMap != null ? nextMap.name() : preset.mapName();
        boolean joinable = (state == GameState.LOBBY || state == GameState.COUNTDOWN) && queue.size() < preset.maxPlayers();
        return new ServerStatus(serverId, preset.id(), mapName, state, queue.size(), preset.maxPlayers(), joinable);
    }

    @EventHandler
    public void onArrowShoot(EntityShootBowEvent event) {
        if (state != GameState.IN_GAME) return;
        if (!(event.getEntity() instanceof Player shooter)) return;
        
        PlayerProfile profile = queue.get(shooter.getUniqueId());
        if (profile == null || profile.lastRole() != MurderRole.DETECTIVE) {
            // Only detective can shoot bow
            event.setCancelled(true);
            return;
        }
        
        ItemStack usedBow = event.getBow();
        boolean isDetectiveBow = false;
        if (usedBow != null) {
            ItemMeta meta = usedBow.getItemMeta();
            if (meta != null && "Detective Bow".equals(meta.getDisplayName())) {
                isDetectiveBow = true;
            }
        }
        
        if (isDetectiveBow && event.getProjectile() instanceof org.bukkit.entity.Arrow arrow) {
            // Cancel real arrow and run legacy simulation for consistent 1.8 feel
            float charge = event.getForce(); // 0.0 to 1.0
            double speed = Math.min(charge * 3.0, 3.0); // 1.8-ish max ~3.0

            // Cancel and remove spawned arrow to avoid backend quirks
            event.setCancelled(true);
            try { arrow.remove(); } catch (Throwable ignored) {}

            // Sim start from shooter eye with current look direction
            Location start = shooter.getEyeLocation();
            org.bukkit.util.Vector dir = start.getDirection();

            new com.mmhq.game.combat.LegacyArrowSim(plugin)
                    .shootLegacyArrow(shooter, start, dir, speed);

            plugin.getLogger().info("[COMBAT-LEGACY] Bow shot charge=" + charge + ", speed=" + speed + " (simulated 1.8)");

            shooter.getInventory().setItem(10, null); // Remove arrow from slot 10
            startDetectiveCooldown(shooter.getUniqueId());
        }
    }

    private void startDetectiveCooldown(UUID playerId) {
        final long totalMs = 5000L;
        final long started = System.currentTimeMillis();
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(playerId);
                if (p == null) { cancel(); return; }
                long elapsed = System.currentTimeMillis() - started;
                long remaining = Math.max(0L, totalMs - elapsed);
                // Simplified bow cooldown indicator
                ChatColor color = remaining <= 500 ? ChatColor.GREEN : ChatColor.YELLOW;
                p.sendMessage(color + "Bow CD: " + String.format("%.1fs", remaining/1000.0));
                if (remaining <= 0L) {
                    if (p.isOnline()) {
                        p.getInventory().setItem(10, new ItemStack(Material.ARROW));
                        try {
                            p.sendMessage(ChatColor.GREEN + "Bow Ready!");
                            p.playSound(p.getLocation(), resolvePopSound(), 1.0f, 1.2f);
                        } catch (Throwable ignored) {}
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (state != GameState.IN_GAME) return;
        Player player = event.getPlayer();
        PlayerProfile profile = queue.get(player.getUniqueId());
        if (profile == null) return;
        boolean picked = bowDropManager.tryPickup(player, profile.lastRole());
        if (picked) {
            // Promote to detective
            profile.lastRole(MurderRole.DETECTIVE);
            goldManager.setPlayerRole(player.getUniqueId(), MurderRole.DETECTIVE);
            scoreboardManager.setPlayerRole(player.getUniqueId(), MurderRole.DETECTIVE);
            scoreboardManager.setDetectiveAlive(true);
            player.sendMessage(ChatColor.YELLOW + "You picked up the Detective's bow! You are now the Detective.");
        }
        scoreboardManager.setBowDropped(bowDropManager.isDropped());
    }

    private Location resolveLobbySpawn() {
        String path = "preset.default.lobbySpawn";
        String worldName = plugin.getConfig().getString(path + ".world", preset.mapName());
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
        }
        double x = plugin.getConfig().getDouble(path + ".x", 0.5);
        double y = plugin.getConfig().getDouble(path + ".y", world.getSpawnLocation().getY());
        double z = plugin.getConfig().getDouble(path + ".z", 0.5);
        float yaw = (float) plugin.getConfig().getDouble(path + ".yaw", 0.0);
        float pitch = (float) plugin.getConfig().getDouble(path + ".pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private Location resolvePreGameLobbySpawn() {
        String path = "preset.default.preGameLobbySpawn";
        String worldName = plugin.getConfig().getString(path + ".world", "map_ancient_tomb");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[MM] World '" + worldName + "' not found! Using first available world.");
            // Fallback to lobby spawn world to avoid survival world confusion
            world = lobbySpawn.getWorld() != null ? lobbySpawn.getWorld() : Bukkit.getWorlds().get(0);
        }
        double x = plugin.getConfig().getDouble(path + ".x", 5.5);
        double y = plugin.getConfig().getDouble(path + ".y", 128);
        double z = plugin.getConfig().getDouble(path + ".z", 0.5);
        float yaw = (float) plugin.getConfig().getDouble(path + ".yaw", 0.0);
        float pitch = (float) plugin.getConfig().getDouble(path + ".pitch", 0.0);
        Location loc = new Location(world, x, y, z, yaw, pitch);
        plugin.getLogger().info("[MM] Resolved preGameLobbySpawn: world=" + world.getName() + ", coords=(" + x + ", " + y + ", " + z + ")");
        return loc;
    }

    /**
     * Get the waiting spawn for the currently selected map.
     * Uses nextMap if set, falls back to currentMap, then preGameLobbySpawn.
     */
    private Location getWaitingSpawn() {
        // Priority: nextMap > currentMap > preGameLobbySpawn
        if (nextMap != null && nextMap.waitingSpawn() != null) {
            return nextMap.waitingSpawn();
        }
        if (currentMap != null && currentMap.waitingSpawn() != null) {
            return currentMap.waitingSpawn();
        }
        return preGameLobbySpawn;
    }

    private Location randomSpawn(MapDefinition map) {
        if (map == null || map.gameSpawns().isEmpty()) {
            return lobbySpawn.clone().add(0, lobbySpawnOffsetY, 0);
        }
        List<Location> spawns = map.gameSpawns();
        return spawns.get((int) (Math.random() * spawns.size()));
    }

    private Location checkAndGetSafeSpawn(Location base, String mapName) {
        if (base == null || base.getWorld() == null) return null;
        
        // Check if spawn location is safe (feet at Y, head at Y+1)
        Block feetBlock = base.getBlock();
        Block headBlock = base.clone().add(0, 1, 0).getBlock();
        
        // If both feet and head positions are passable, spawn is safe
        if (isPassable(feetBlock) && isPassable(headBlock)) {
            return base;
        }
        
        // Unsafe spawn - log for removal
        plugin.getLogger().warning("[MM] ========================================");
        plugin.getLogger().warning("[MM] UNSAFE SPAWN DETECTED - REMOVE FROM MAP CONFIG!");
        plugin.getLogger().warning("[MM] Map: " + mapName);
        plugin.getLogger().warning("[MM] Location: X=" + base.getX() + " Y=" + base.getY() + " Z=" + base.getZ());
        plugin.getLogger().warning("[MM] Feet block: " + feetBlock.getType() + " (solid=" + feetBlock.getType().isSolid() + ")");
        plugin.getLogger().warning("[MM] Head block: " + headBlock.getType() + " (solid=" + headBlock.getType().isSolid() + ")");
        plugin.getLogger().warning("[MM] ========================================");
        
        return null; // Signal to try another spawn
    }

    private Location ensureSafeSpawn(Location base) {
        if (base == null || base.getWorld() == null) return base;
        
        // Check if spawn location is safe (feet at Y, head at Y+1, torso at Y+0.5)
        Location feetLoc = base.clone();
        Block feetBlock = feetLoc.getBlock();
        Block headBlock = feetLoc.clone().add(0, 1, 0).getBlock();
        
        plugin.getLogger().info("[MM] DEBUG: Checking spawn safety at " + base.getX() + ", " + base.getY() + ", " + base.getZ());
        plugin.getLogger().info("[MM] DEBUG: Feet block: " + feetBlock.getType() + ", Head block: " + headBlock.getType());
        
        // If both feet and head positions are air/passable, spawn is safe
        if (isPassable(feetBlock) && isPassable(headBlock)) {
            plugin.getLogger().info("[MM] DEBUG: Spawn is safe");
            return base;
        }
        
        plugin.getLogger().warning("[MM] DEBUG: Spawn not safe, searching upward...");
        // Try searching upward for a safe spot
        Location testLoc = base.clone();
        for (int i = 0; i < 10; i++) {
            testLoc.add(0, 1, 0);
            Block testFeet = testLoc.getBlock();
            Block testHead = testLoc.clone().add(0, 1, 0).getBlock();
            
            if (isPassable(testFeet) && isPassable(testHead)) {
                plugin.getLogger().info("[MM] DEBUG: Found safe spawn at Y=" + testLoc.getY());
                return testLoc;
            }
        }
        
        // Last resort: teleport to highest solid block + 1
        int highestY = base.getWorld().getHighestBlockYAt(base);
        Location safeLoc = new Location(base.getWorld(), base.getX(), highestY + 1, base.getZ(), base.getYaw(), base.getPitch());
        plugin.getLogger().warning("[MM] DEBUG: Using fallback spawn at Y=" + safeLoc.getY());
        return safeLoc;
    }
    
    private boolean isPassable(Block block) {
        Material type = block.getType();
        // Air and other non-solid blocks are passable
        return type == Material.AIR || !block.getType().isSolid();
    }
}

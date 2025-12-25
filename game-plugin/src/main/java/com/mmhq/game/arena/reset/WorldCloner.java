package com.mmhq.game.arena.reset;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Handles world operations for the hard reset system.
 * Clones template worlds to the active world for each match.
 */
public final class WorldCloner {
    private final JavaPlugin plugin;

    public WorldCloner(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Unload a world synchronously without saving.
     * Must be called on the main thread.
     */
    public void unloadWorldSync(String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            plugin.getLogger().info("[WorldCloner] World not loaded: " + worldName);
            return;
        }
        
        // Teleport any players in this world to the main world first
        World mainWorld = Bukkit.getWorlds().get(0);
        for (org.bukkit.entity.Player p : w.getPlayers()) {
            p.teleport(mainWorld.getSpawnLocation());
        }
        
        // Do NOT save; we want a fresh copy from template
        boolean ok = Bukkit.unloadWorld(w, false);
        if (!ok) {
            throw new IllegalStateException("Failed to unload world: " + worldName);
        }
        plugin.getLogger().info("[WorldCloner] Unloaded world: " + worldName);
    }

    /**
     * Delete a world folder recursively.
     * Must be called when world is NOT loaded.
     */
    public void deleteWorldFolder(String worldName) throws IOException {
        Path container = Bukkit.getWorldContainer().toPath();
        Path target = container.resolve(worldName);
        
        if (!Files.exists(target)) {
            plugin.getLogger().info("[WorldCloner] World folder does not exist: " + target);
            return;
        }

        plugin.getLogger().info("[WorldCloner] Deleting world folder: " + target);
        
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        
        plugin.getLogger().info("[WorldCloner] Deleted world folder: " + worldName);
    }

    /**
     * Copy a template world folder to the active world location.
     * Must be called when active world is NOT loaded.
     */
    public void copyTemplateToActive(String templateWorldName, String activeWorldName) throws IOException {
        Path container = Bukkit.getWorldContainer().toPath();
        Path src = container.resolve(templateWorldName);
        Path dst = container.resolve(activeWorldName);

        if (!Files.exists(src)) {
            throw new IllegalArgumentException("Template world folder does not exist: " + src);
        }

        plugin.getLogger().info("[WorldCloner] Copying " + templateWorldName + " -> " + activeWorldName);
        
        Files.createDirectories(dst);

        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = src.relativize(dir);
                Path outDir = dst.resolve(rel);
                Files.createDirectories(outDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                // Skip session.lock to avoid file lock issues
                if (name.equalsIgnoreCase("session.lock")) {
                    return FileVisitResult.CONTINUE;
                }

                Path rel = src.relativize(file);
                Path out = dst.resolve(rel);
                Files.copy(file, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });

        // Delete uid.dat to avoid duplicate world UID issues
        Files.deleteIfExists(dst.resolve("uid.dat"));
        
        plugin.getLogger().info("[WorldCloner] Finished copying template to " + activeWorldName);
    }

    /**
     * Load or create the active world.
     * Must be called on the main thread.
     */
    public World loadActiveWorldSync(String activeWorldName) {
        World w = Bukkit.getWorld(activeWorldName);
        if (w != null) {
            plugin.getLogger().info("[WorldCloner] World already loaded: " + activeWorldName);
            return w;
        }

        plugin.getLogger().info("[WorldCloner] Loading world: " + activeWorldName);
        
        WorldCreator creator = new WorldCreator(activeWorldName);
        w = creator.createWorld();
        
        if (w == null) {
            throw new IllegalStateException("Failed to create/load world: " + activeWorldName);
        }
        
        plugin.getLogger().info("[WorldCloner] World loaded: " + activeWorldName);
        return w;
    }
}

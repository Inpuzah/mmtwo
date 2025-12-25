package com.mmhq.game.arena.reset;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates the hard reset pipeline for the arena.
 * Handles unload → delete → copy → load sequence with progress reporting.
 */
public final class ResetPipeline {

    /**
     * Callback interface for reset progress updates.
     */
    public interface ProgressListener {
        void onProgress(String step, int pct);
    }

    private final JavaPlugin plugin;
    private final WorldCloner cloner;

    public ResetPipeline(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cloner = new WorldCloner(plugin);
    }

    /**
     * Execute a hard reset: unload active world, delete it, copy template, reload.
     * File operations run async; world operations run sync on main thread.
     *
     * @param templateWorld Name of the template world folder
     * @param activeWorld   Name of the active world (mm_active)
     * @param progress      Callback for progress updates
     * @return CompletableFuture that completes with the loaded World
     */
    public CompletableFuture<World> hardResetToTemplate(String templateWorld, String activeWorld, ProgressListener progress) {
        progress.onProgress("LOCK_JOIN", 5);

        CompletableFuture<World> result = new CompletableFuture<>();

        // Step 1: Unload world on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                progress.onProgress("UNLOAD_ACTIVE_SYNC", 15);
                cloner.unloadWorldSync(activeWorld);

                // Step 2: Run file operations async
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        progress.onProgress("DELETE_ACTIVE_FILES", 35);
                        cloner.deleteWorldFolder(activeWorld);

                        progress.onProgress("COPY_TEMPLATE_FILES", 65);
                        cloner.copyTemplateToActive(templateWorld, activeWorld);

                        // Step 3: Load world on main thread
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                progress.onProgress("LOAD_ACTIVE_SYNC", 90);
                                World w = cloner.loadActiveWorldSync(activeWorld);
                                progress.onProgress("DONE", 100);
                                result.complete(w);
                            } catch (Exception e) {
                                plugin.getLogger().severe("[ResetPipeline] Failed to load world: " + e.getMessage());
                                result.completeExceptionally(e);
                            }
                        });

                    } catch (Exception e) {
                        plugin.getLogger().severe("[ResetPipeline] File operation failed: " + e.getMessage());
                        e.printStackTrace();
                        result.completeExceptionally(e);
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().severe("[ResetPipeline] Failed to unload world: " + e.getMessage());
                result.completeExceptionally(e);
            }
        });

        return result;
    }
}

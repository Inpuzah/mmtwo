package com.mmhq.game.arena.special;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Manages sword skins and visual customization for the murderer.
 */
public class SwordSkinManager {
    private final ItemStack murdererSword;

    public SwordSkinManager() {
        // Create default murderer sword (IRON_SWORD with custom name/lore)
        this.murdererSword = new ItemStack(org.bukkit.Material.IRON_SWORD);
    }

    /**
     * Get the murderer's sword skin.
     * @param player The player (unused in basic implementation)
     * @return The murderer sword ItemStack
     */
    public ItemStack getMurdererSword(Player player) {
        return murdererSword.clone();
    }
}

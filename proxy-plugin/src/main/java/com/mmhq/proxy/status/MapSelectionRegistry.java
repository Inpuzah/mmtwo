package com.mmhq.proxy.status;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Stores the currently selected map for mm1.
 * This allows lobby to set the map before players join mm1.
 */
public class MapSelectionRegistry {
    
    private final AtomicReference<String> selectedMap = new AtomicReference<>("AncientTomb");
    
    /**
     * Set the selected map for the next game.
     */
    public void setSelectedMap(String mapName) {
        selectedMap.set(mapName);
    }
    
    /**
     * Get the currently selected map.
     */
    public String getSelectedMap() {
        return selectedMap.get();
    }
    
    /**
     * Clear the selection (reset to default).
     */
    public void clearSelection() {
        selectedMap.set("AncientTomb");
    }
}

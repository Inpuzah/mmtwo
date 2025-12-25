package com.mmhq.sharedapi;

/**
 * Shared constants across all mm-two plugins.
 */
public final class Constants {
    public static final String PLUGIN_MESSAGE_CHANNEL_CONTROL = "mmhq:control";
    public static final String PLUGIN_MESSAGE_CHANNEL_STATUS = "mmhq:status";

    private Constants() {
        throw new AssertionError("Cannot instantiate Constants");
    }
}

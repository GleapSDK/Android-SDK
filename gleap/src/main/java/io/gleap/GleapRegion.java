package io.gleap;

import java.util.Locale;

/**
 * Data regions of the Gleap cloud.
 * <p>
 * This is the single place where the regional hosts are defined. A region only
 * covers the API, the websocket and the realtime host. The static widget hosts
 * (messenger frame, banner, modal, sdk assets) are global and not part of a region.
 */
enum GleapRegion {
    EU("eu", "https://api.eu.gleap.ai", "wss://ws.eu.gleap.ai", "sockets.eu.gleap.ai"),
    US("us", "https://api.us.gleap.ai", "wss://ws.us.gleap.ai", "sockets.us.gleap.ai");

    private final String key;
    private final String apiUrl;
    private final String wsApiUrl;
    private final String realtimeHost;

    GleapRegion(String key, String apiUrl, String wsApiUrl, String realtimeHost) {
        this.key = key;
        this.apiUrl = apiUrl;
        this.wsApiUrl = wsApiUrl;
        this.realtimeHost = realtimeHost;
    }

    public String getKey() {
        return key;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getWsApiUrl() {
        return wsApiUrl;
    }

    public String getRealtimeHost() {
        return realtimeHost;
    }

    /**
     * Resolves a region by its key ("eu" | "us"), case-insensitively.
     *
     * @param region the region key
     * @return the region or null, if the key is unknown
     */
    public static GleapRegion fromString(String region) {
        if (region == null) {
            return null;
        }

        String normalized = region.trim().toLowerCase(Locale.ROOT);
        for (GleapRegion gleapRegion : values()) {
            if (gleapRegion.key.equals(normalized)) {
                return gleapRegion;
            }
        }

        return null;
    }
}

package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

public class GleapRegionTest {
    private static final String FRAME_URL = "https://messenger-app.gleap.io/appnew";
    private static final String BANNER_URL = "https://outboundmedia.gleap.io";
    private static final String MODAL_URL = "https://outboundmedia.gleap.io/modal";

    @Before
    public void resetConfig() {
        GleapConfig config = GleapConfig.getInstance();
        config.setRegion(GleapRegion.EU);
        config.setRealtimeHost(null);
        config.setiFrameUrl(FRAME_URL);
        config.setBannerUrl(BANNER_URL);
        config.setModalUrl(MODAL_URL);
    }

    @Test
    public void resolvesRegionsCaseInsensitively() {
        assertEquals(GleapRegion.EU, GleapRegion.fromString("eu"));
        assertEquals(GleapRegion.EU, GleapRegion.fromString("EU"));
        assertEquals(GleapRegion.US, GleapRegion.fromString("us"));
        assertEquals(GleapRegion.US, GleapRegion.fromString("Us"));
        assertEquals(GleapRegion.US, GleapRegion.fromString(" US "));
    }

    @Test
    public void unknownRegionsResolveToNull() {
        assertNull(GleapRegion.fromString(null));
        assertNull(GleapRegion.fromString(""));
        assertNull(GleapRegion.fromString("apac"));
    }

    @Test
    public void regionTableMatchesTheSpec() {
        assertEquals("https://api.eu.gleap.ai", GleapRegion.EU.getApiUrl());
        assertEquals("wss://ws.eu.gleap.ai", GleapRegion.EU.getWsApiUrl());
        assertEquals("sockets.eu.gleap.ai", GleapRegion.EU.getRealtimeHost());

        assertEquals("https://api.us.gleap.ai", GleapRegion.US.getApiUrl());
        assertEquals("wss://ws.us.gleap.ai", GleapRegion.US.getWsApiUrl());
        assertEquals("sockets.us.gleap.ai", GleapRegion.US.getRealtimeHost());
    }

    @Test
    public void defaultsStayEuWithoutRealtimeHost() {
        GleapConfig config = GleapConfig.getInstance();
        assertEquals("https://api.eu.gleap.ai", config.getApiUrl());
        assertEquals("wss://ws.eu.gleap.ai", config.getWsApiUrl());
        assertNull(config.getRealtimeHost());
    }

    @Test
    public void setRegionSetsAllRegionalHostsAtOnce() {
        GleapConfig config = GleapConfig.getInstance();
        config.setRegion(GleapRegion.US);

        assertEquals("https://api.us.gleap.ai", config.getApiUrl());
        assertEquals("wss://ws.us.gleap.ai", config.getWsApiUrl());
        assertEquals("sockets.us.gleap.ai", config.getRealtimeHost());
    }

    @Test
    public void setRegionKeepsTheStaticWidgetHosts() {
        GleapConfig config = GleapConfig.getInstance();
        config.setRegion(GleapRegion.US);

        assertEquals(FRAME_URL, config.getiFrameUrl());
        assertEquals(BANNER_URL, config.getBannerUrl());
        assertEquals(MODAL_URL, config.getModalUrl());
    }

    @Test
    public void manualSetterAfterSetRegionOverridesASingleHost() {
        GleapConfig config = GleapConfig.getInstance();
        config.setRegion(GleapRegion.US);
        config.setApiUrl("https://api.example.com");

        assertEquals("https://api.example.com", config.getApiUrl());
        assertEquals("wss://ws.us.gleap.ai", config.getWsApiUrl());
        assertEquals("sockets.us.gleap.ai", config.getRealtimeHost());
    }

    @Test
    public void unknownRegionLeavesTheHostsUnchanged() {
        GleapConfig config = GleapConfig.getInstance();
        config.setRegion(GleapRegion.US);
        config.setRegion(GleapRegion.fromString("apac"));

        assertEquals("https://api.us.gleap.ai", config.getApiUrl());
    }
}

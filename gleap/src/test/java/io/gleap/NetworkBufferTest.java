package io.gleap;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

public class NetworkBufferTest {
    @After
    public void clearSharedBuffer() {
        GleapBug.getInstance().getNetworkBuffer().clear();
    }

    private static Networklog log(int index) {
        return new Networklog("https://api.example.com/items/" + index, RequestType.GET, 200, 1, null, null);
    }

    // The buffer used to drop the newest entry once full and keep the oldest ones forever.
    @Test
    public void keepsTheNewestEntries() {
        NetworkBuffer buffer = new NetworkBuffer();
        for (int i = 0; i < NetworkBuffer.MAX_AMOUNT + 5; i++) {
            buffer.addNetworkLog(log(i));
        }

        Networklog[] logs = buffer.getNetworklogs();
        assertEquals(NetworkBuffer.MAX_AMOUNT, logs.length);
        assertEquals("https://api.example.com/items/5", logs[0].getUrl());
        assertEquals("https://api.example.com/items/" + (NetworkBuffer.MAX_AMOUNT + 4), logs[logs.length - 1].getUrl());
    }

    // Reading the logs for a conversation used to clear them before a later bug report.
    @Test
    public void readingTheLogsDoesNotClearThem() throws Exception {
        GleapBug.getInstance().addRequest(log(1));
        GleapBug.getInstance().getNetworkBuffer().attachNetworkLogs(new JSONArray().put(
                new JSONObject().put("date", "2020-01-01T00:00:00.000Z").put("type", "PROPFIND").put("url", "https://api.example.com/dav")));

        assertEquals(2, GleapBug.getInstance().getNetworklogs().length());
        JSONArray secondRead = GleapBug.getInstance().getNetworklogs();
        assertEquals(2, secondRead.length());
        assertEquals("PROPFIND", secondRead.getJSONObject(0).getString("type"));
    }

    @Test
    public void attachingReplacesThePreviouslyAttachedLogs() {
        NetworkBuffer buffer = GleapBug.getInstance().getNetworkBuffer();
        buffer.addNetworkLog(log(1));
        buffer.attachNetworkLogs(new Networklog[]{log(2), log(3)});
        buffer.attachNetworkLogs(new Networklog[]{log(4), null});

        assertEquals(2, GleapBug.getInstance().getNetworklogs().length());

        buffer.attachNetworkLogs((Networklog[]) null);
        assertEquals(1, GleapBug.getInstance().getNetworklogs().length());
    }
}

package io.gleap;

import java.nio.Buffer;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class NetworkBuffer {
    private  int ringBufferCounter = 0;
    private Networklog[] networklogs;
    private final int MAX_AMOUNT = 25;

    public NetworkBuffer() {
        networklogs = new Networklog[MAX_AMOUNT-1];
    }

    public Networklog[] getNetworklogs() {
        return networklogs;
    }

    public void addNetworkLog(Networklog networklog) {
        if (ringBufferCounter > networklogs.length - 1) {
            ringBufferCounter = 0;
        }
        networklogs[ringBufferCounter++] = networklog;
    }

    public void attachNetworkLogs(Networklog[] networklogs) {
        this.networklogs = networklogs;
    }

    public void clear() {
        networklogs = new Networklog[MAX_AMOUNT];
    }
}

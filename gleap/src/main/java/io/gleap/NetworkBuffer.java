package io.gleap;

import java.nio.Buffer;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class NetworkBuffer {
    private LinkedList<Networklog> networklogs;
    private final int MAX_AMOUNT = 25;

    public NetworkBuffer() {
        networklogs = new LinkedList<>();
    }

    public Networklog[] getNetworklogs() {
        try {
            return networklogs.toArray(networklogs.toArray(new Networklog[0]));
        }catch (Exception ignore) {}

        return new Networklog[0];
    }

    public void addNetworkLog(Networklog networklog) {
        try {
            if (networklogs.size() == 25) {
                networklogs.removeFirst();
            }

            System.out.println(networklogs.size());

            networklogs.push(networklog);
        }catch (Exception ignore) {}
    }

    public void attachNetworkLogs(Networklog[] networklogs) {
        try {
            for (Networklog networklog : networklogs) {
                addNetworkLog(networklog);
            }
        }catch (Exception ignore) {}
    }

    public void clear() {
        networklogs = new LinkedList<>();
    }
}

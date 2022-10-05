package io.gleap;

import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

class GleapActionQueueHandler {
    private static GleapActionQueueHandler instance;
    private List<GleapAction> messagesQueue = new LinkedList();
    private GleapActionQueueHandler() {
    }

    public static GleapActionQueueHandler getInstance() {
        if(instance == null) {
            instance = new GleapActionQueueHandler();
        }
        return instance;
    }

    public void addActionMessage(GleapAction message) {
        this.messagesQueue.add(message);
    }

    public List<GleapAction> getActionQueue() {
        return messagesQueue;
    }

    public void clearActionMessageQueue() {
        this.messagesQueue = new LinkedList<>();
    }


}

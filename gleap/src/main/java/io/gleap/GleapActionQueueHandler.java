package io.gleap;

import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

class GleapActionQueueHandler {
    private static GleapActionQueueHandler instance;
    private List<JSONObject> messagesQueue = new LinkedList();
    private GleapActionQueueHandler() {
    }

    public static GleapActionQueueHandler getInstance() {
        if(instance == null) {
            instance = new GleapActionQueueHandler();
        }
        return instance;
    }

    public void addActionMessage(JSONObject message) {
        this.messagesQueue.add(message);
    }

    public List<JSONObject> getActionQueue() {
        return messagesQueue;
    }

    public void clearActionMessageQueue() {
        this.messagesQueue = new LinkedList<>();
    }


}

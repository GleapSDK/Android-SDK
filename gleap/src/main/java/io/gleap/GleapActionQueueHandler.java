package io.gleap;

import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

class GleapActionQueueHandler {
    // Created with the class: getInstance() is called from several threads.
    private static final GleapActionQueueHandler instance = new GleapActionQueueHandler();
    private List<GleapAction> messagesQueue = new LinkedList();
    private GleapActionQueueHandler() {
    }

    public static GleapActionQueueHandler getInstance() {
        return instance;
    }

    public void addActionMessage(GleapAction message) {
        if (Gleap.getInstance().isOpened()) {
            return;
        }

        this.messagesQueue.add(message);
    }

    public List<GleapAction> getActionQueue() {
        return messagesQueue;
    }

    public void clearActionMessageQueue() {
        this.messagesQueue = new LinkedList<>();
    }


}

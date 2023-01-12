package io.gleap;

import org.json.JSONObject;

interface OnHttpResponseListener {
    void onTaskComplete(JSONObject response) throws GleapAlreadyInitialisedException;
}

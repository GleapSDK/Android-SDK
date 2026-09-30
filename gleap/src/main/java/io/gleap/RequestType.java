package io.gleap;

/**
 * Request methods for {@link Networklog} and {@link Gleap#logNetwork(String, RequestType, int, int, org.json.JSONObject, org.json.JSONObject)}.
 * Other methods can be attached as strings with {@link Gleap#attachNetworkLogs(org.json.JSONArray)}.
 */
public enum RequestType {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
}

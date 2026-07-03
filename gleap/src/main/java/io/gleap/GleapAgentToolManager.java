package io.gleap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.gleap.callbacks.GleapAgentToolHandler;
import io.gleap.callbacks.GleapAgentToolResultCallback;

/**
 * Manages handlers for dashboard-defined Frontend tools and their executions.
 * Executions always complete with a result string for the AI — missing
 * handlers and thrown exceptions become error messages instead of leaving the
 * agent waiting. Deduped by toolCallId: duplicate requests while running are
 * dropped, requests for an already completed toolCallId replay the stored
 * result. Execution state is cleared when the widget closes.
 */
class GleapAgentToolManager {
    private static GleapAgentToolManager instance;

    private final Map<String, GleapAgentToolHandler> registeredAgentTools = new ConcurrentHashMap<>();
    private final Set<String> runningToolCallIds = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Map<String, JSONObject> completedToolResults = new ConcurrentHashMap<>();

    private GleapAgentToolManager() {
    }

    public static synchronized GleapAgentToolManager getInstance() {
        if (instance == null) {
            instance = new GleapAgentToolManager();
        }
        return instance;
    }

    public void registerAgentTool(String name, GleapAgentToolHandler handler) {
        if (name == null || name.length() == 0 || handler == null) {
            return;
        }
        registeredAgentTools.put(name, handler);
    }

    public void executeTool(JSONObject data, final GleapAgentToolResultCallback resultConsumer) {
        if (data == null || resultConsumer == null) {
            return;
        }

        final String toolCallId = data.optString("toolCallId", "");
        final String name = data.optString("name", "");
        JSONObject params = data.optJSONObject("params");
        if (params == null) {
            params = new JSONObject();
        }
        if (name.length() == 0) {
            return;
        }

        if (toolCallId.length() > 0) {
            JSONObject storedResult = completedToolResults.get(toolCallId);
            if (storedResult != null) {
                resultConsumer.onResult(storedResult);
                return;
            }
            if (!runningToolCallIds.add(toolCallId)) {
                // Already in-flight — the running execution will deliver the result.
                return;
            }
        }

        GleapAgentToolHandler handler = registeredAgentTools.get(name);
        if (handler == null) {
            finishToolCall(toolCallId, name, "No handler registered for tool '" + name + "' in the app. Register one via Gleap.registerAgentTool('" + name + "', handler).", resultConsumer);
            return;
        }

        final AtomicBoolean completed = new AtomicBoolean(false);
        GleapAgentToolResultCallback handlerCallback = new GleapAgentToolResultCallback() {
            @Override
            public void onResult(Object result) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                finishToolCall(toolCallId, name, stringFromToolResult(result), resultConsumer);
            }
        };

        try {
            handler.execute(params, handlerCallback);
        } catch (Error | Exception exp) {
            String message = exp.getMessage() != null ? exp.getMessage() : "unknown error";
            handlerCallback.onResult("Tool execution failed: " + message);
        }
    }

    private void finishToolCall(String toolCallId, String name, String result, GleapAgentToolResultCallback resultConsumer) {
        try {
            JSONObject resultData = new JSONObject();
            resultData.put("toolCallId", toolCallId);
            resultData.put("name", name);
            resultData.put("result", result);

            if (toolCallId.length() > 0) {
                runningToolCallIds.remove(toolCallId);
                completedToolResults.put(toolCallId, resultData);
            }

            resultConsumer.onResult(resultData);
        } catch (Error | Exception ignore) {
        }
    }

    private static String stringFromToolResult(Object result) {
        String stringResult = null;

        if (result instanceof String) {
            stringResult = (String) result;
        } else if (result instanceof JSONObject || result instanceof JSONArray) {
            stringResult = result.toString();
        } else if (result != null && result != JSONObject.NULL) {
            stringResult = String.valueOf(result);
        }

        if (stringResult == null || stringResult.length() == 0) {
            return "The action completed without returning a result.";
        }

        return stringResult;
    }

    public void clearExecutionState() {
        runningToolCallIds.clear();
        completedToolResults.clear();
    }
}

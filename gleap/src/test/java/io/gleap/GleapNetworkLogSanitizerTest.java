package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GleapNetworkLogSanitizerTest {
    @After
    public void resetLocalLists() {
        Gleap.getInstance().setNetworkLogPropsToIgnore(null);
        Gleap.getInstance().setNetworkLogsBlacklist(null);
    }

    private static GleapNetworkLogSanitizer sanitizer(String... props) {
        return new GleapNetworkLogSanitizer(Arrays.asList(props), Collections.<String>emptyList());
    }

    private static JSONObject entry(String url, JSONObject requestHeaders, String payload,
                                    JSONObject responseHeaders, String responseText) throws Exception {
        JSONObject request = new JSONObject();
        request.put("headers", requestHeaders);
        if (payload != null) {
            request.put("payload", payload);
        }
        JSONObject response = new JSONObject();
        response.put("status", 200);
        response.put("statusText", "OK");
        response.put("headers", responseHeaders);
        if (responseText != null) {
            response.put("responseText", responseText);
        }
        JSONObject entry = new JSONObject();
        entry.put("date", "2026-09-27T10:00:00.123Z");
        entry.put("type", "POST");
        entry.put("url", url);
        entry.put("duration", 12);
        entry.put("success", true);
        entry.put("request", request);
        entry.put("response", response);
        return entry;
    }

    private static JSONObject headers(String... namesAndValues) throws Exception {
        JSONObject headers = new JSONObject();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            headers.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return headers;
    }

    // Regression: the merge copied the string entries with optJSONObject, so every prop and
    // blacklist entry became null and none of them was ever applied.
    @Test
    public void mergeMultiJsonArrayKeepsStringEntries() throws Exception {
        JSONArray merged = Networklog.mergeMultiJsonArray(
                new JSONArray().put("password"), null, new JSONArray().put("internal.example.com"));

        assertEquals(2, merged.length());
        assertEquals("password", merged.getString(0));
        assertEquals("internal.example.com", merged.getString(1));
    }

    @Test
    public void mergeStringsDedupesTrimsAndSkipsNonStrings() {
        JSONArray remote = new JSONArray().put("token").put(" password ").put(3).put("");
        JSONArray local = new JSONArray().put("password").put("secret");

        List<String> merged = GleapNetworkLogSanitizer.mergeStrings(remote, local, null);

        assertEquals(Arrays.asList("token", "password", "secret"), merged);
    }

    // Would have caught the merge bug: the local props were never applied to a sent entry.
    @Test
    public void localPropsToIgnoreAreAppliedToSentEntries() throws Exception {
        Gleap.getInstance().setNetworkLogPropsToIgnore(new String[]{"x-api-key", "password"});

        JSONObject request = new JSONObject();
        request.put("headers", headers("X-Api-Key", "secret-key", "Accept", "application/json"));
        request.put("payload", "{\"password\":\"hunter2\",\"user\":\"ada\"}");
        JSONObject sent = new Networklog("https://api.example.com/login", RequestType.POST, 200, 40, request, null).toJSON();

        JSONObject sentRequest = sent.getJSONObject("request");
        assertFalse(sentRequest.getJSONObject("headers").has("X-Api-Key"));
        assertEquals("application/json", sentRequest.getJSONObject("headers").getString("Accept"));
        JSONObject payload = new JSONObject(sentRequest.getString("payload"));
        assertFalse(payload.has("password"));
        assertEquals("ada", payload.getString("user"));
    }

    @Test
    public void blacklistedEntriesAreDropped() throws Exception {
        Gleap.getInstance().setNetworkLogsBlacklist(new String[]{"internal.example.com"});

        assertNull(new Networklog("https://internal.example.com/health", RequestType.GET, 200, 5, null, null).toJSON());
        assertNull(new Networklog("https://api.eu.gleap.ai/sessions", RequestType.POST, 200, 5, null, null).toJSON());
        assertNull(new Networklog("https://API.GLEAP.IO/config", RequestType.GET, 200, 5, null, null).toJSON());
        assertNotNull(new Networklog("https://api.example.com/items", RequestType.GET, 200, 5, null, null).toJSON());

        JSONArray entries = new JSONArray()
                .put(entry("https://internal.example.com/a", headers(), null, headers(), null))
                .put(entry("https://api.example.com/b", headers(), null, headers(), null));
        JSONArray sanitized = GleapNetworkLogSanitizer.fromConfig().sanitize(entries);
        assertEquals(1, sanitized.length());
        assertEquals("https://api.example.com/b", sanitized.getJSONObject(0).getString("url"));
    }

    @Test
    public void credentialHeadersAreAlwaysMasked() throws Exception {
        JSONObject entry = entry("https://api.example.com/me",
                headers("Authorization", "Bearer abc", "COOKIE", "sid=1", "Proxy-Authorization", "Basic xyz", "Accept", "*/*"),
                null,
                headers("set-cookie", "sid=2; HttpOnly", "Content-Type", "application/json"),
                "{}");

        JSONObject sanitized = sanitizer().sanitizeEntry(entry);

        JSONObject requestHeaders = sanitized.getJSONObject("request").getJSONObject("headers");
        assertEquals("[REDACTED]", requestHeaders.getString("Authorization"));
        assertEquals("[REDACTED]", requestHeaders.getString("COOKIE"));
        assertEquals("[REDACTED]", requestHeaders.getString("Proxy-Authorization"));
        assertEquals("*/*", requestHeaders.getString("Accept"));
        JSONObject responseHeaders = sanitized.getJSONObject("response").getJSONObject("headers");
        assertEquals("[REDACTED]", responseHeaders.getString("set-cookie"));
        assertEquals("application/json", responseHeaders.getString("Content-Type"));
    }

    @Test
    public void headersNamedLikeAPropAreRemovedCaseInsensitively() throws Exception {
        JSONObject entry = entry("https://api.example.com/me",
                headers("X-Session-Token", "abc", "Accept", "*/*"), null,
                headers("x-session-token", "def", "Content-Type", "text/plain"), "ok");

        JSONObject sanitized = sanitizer("X-SESSION-TOKEN").sanitizeEntry(entry);

        assertFalse(sanitized.getJSONObject("request").getJSONObject("headers").has("X-Session-Token"));
        assertTrue(sanitized.getJSONObject("request").getJSONObject("headers").has("Accept"));
        assertFalse(sanitized.getJSONObject("response").getJSONObject("headers").has("x-session-token"));
    }

    @Test
    public void jsonKeysAreRemovedAtAnyDepthCaseInsensitively() throws Exception {
        String payload = "{\"Password\":\"a\",\"user\":{\"name\":\"ada\",\"password\":\"b\","
                + "\"devices\":[{\"id\":1,\"PASSWORD\":\"c\"},{\"id\":2}]},\"list\":[[{\"password\":\"d\"}]]}";
        String responseText = "[{\"token\":\"t1\",\"id\":1},{\"nested\":{\"Token\":\"t2\"}}]";
        JSONObject entry = entry("https://api.example.com/users", headers(), payload, headers(), responseText);

        JSONObject sanitized = sanitizer("password", "token").sanitizeEntry(entry);

        String sentPayload = sanitized.getJSONObject("request").getString("payload");
        assertFalse(sentPayload.toLowerCase().contains("password"));
        JSONObject parsed = new JSONObject(sentPayload);
        assertEquals("ada", parsed.getJSONObject("user").getString("name"));
        assertEquals(2, parsed.getJSONObject("user").getJSONArray("devices").getJSONObject(1).getInt("id"));

        String sentResponse = sanitized.getJSONObject("response").getString("responseText");
        assertFalse(sentResponse.toLowerCase().contains("token"));
        assertEquals(1, new JSONArray(sentResponse).getJSONObject(0).getInt("id"));
    }

    @Test
    public void dottedPropIsAPathAndAWholeKey() throws Exception {
        String payload = "{\"User\":{\"Password\":\"a\",\"name\":\"ada\"},\"account\":{\"password\":\"keep\"},"
                + "\"user.password\":\"b\",\"users\":[{\"user\":{\"password\":\"c\"}}]}";
        JSONObject entry = entry("https://api.example.com/users", headers(), payload, headers(), null);

        JSONObject sanitized = sanitizer("user.password").sanitizeEntry(entry);

        JSONObject parsed = new JSONObject(sanitized.getJSONObject("request").getString("payload"));
        assertFalse(parsed.getJSONObject("User").has("Password"));
        assertEquals("ada", parsed.getJSONObject("User").getString("name"));
        assertFalse(parsed.has("user.password"));
        // A path starts at the body root: the same keys further down are kept.
        assertEquals("keep", parsed.getJSONObject("account").getString("password"));
        assertEquals("c", parsed.getJSONArray("users").getJSONObject(0).getJSONObject("user").getString("password"));
    }

    @Test
    public void formFieldsAndQueryParamsAreRemoved() throws Exception {
        JSONObject entry = entry("https://api.example.com/login?Token=abc&page=2&pass%77ord=x#top",
                headers("Content-Type", "application/x-www-form-urlencoded"), "user=ada&Password=hunter2&remember=1",
                headers(), null);

        JSONObject sanitized = sanitizer("password", "token").sanitizeEntry(entry);

        assertEquals("https://api.example.com/login?page=2#top", sanitized.getString("url"));
        assertEquals("user=ada&remember=1", sanitized.getJSONObject("request").getString("payload"));

        JSONObject onlySecrets = entry("https://api.example.com/login?token=abc", headers(), null, headers(), null);
        assertEquals("https://api.example.com/login", sanitizer("token").sanitizeEntry(onlySecrets).getString("url"));
    }

    @Test
    public void formBodyWithoutContentTypeIsStillRedacted() {
        assertEquals("user=ada", sanitizer("password").sanitizeBody("user=ada&password=x", null));
        assertEquals("plain text password=x", sanitizer("password").sanitizeBody("plain text password=x", null));
    }

    @Test
    public void unchangedAndUnparseableBodiesStayUntouched() throws Exception {
        String formatted = "{ \"url\": \"https://example.com/a/b\",\n  \"count\": 1.50 }";
        String truncated = "{\"password\":\"hunter2\",\"items\":[1,2" + Networklog.TRUNCATED_PREFIX + "more than 150000 bytes]";
        GleapNetworkLogSanitizer sanitizer = sanitizer("password");

        assertSame(formatted, sanitizer.sanitizeBody(formatted, "application/json"));
        assertSame(truncated, sanitizer.sanitizeBody(truncated, "application/json"));
        assertSame("\"password\"", sanitizer.sanitizeBody("\"password\"", "application/json"));
    }

    @Test
    public void legacyShapesAreRedactedToo() throws Exception {
        // The old OkHttp interceptor put form fields into request.body and the parsed JSON body
        // directly into the response object.
        JSONObject request = new JSONObject();
        request.put("body", new JSONObject().put("password", "a").put("user", "ada"));
        request.put("headers", headers("Authorization", "Bearer x"));
        JSONObject response = new JSONObject();
        response.put("token", "t");
        response.put("profile", new JSONObject().put("Token", "t2").put("name", "ada"));
        Gleap.getInstance().setNetworkLogPropsToIgnore(new String[]{"password", "token"});
        JSONObject sent = new Networklog("https://api.example.com/login", RequestType.POST, 200, 5, request, response).toJSON();

        assertFalse(sent.getJSONObject("request").getJSONObject("body").has("password"));
        assertEquals("[REDACTED]", sent.getJSONObject("request").getJSONObject("headers").getString("Authorization"));
        assertFalse(sent.getJSONObject("response").has("token"));
        assertFalse(sent.getJSONObject("response").getJSONObject("profile").has("Token"));
        assertEquals(200, sent.getJSONObject("response").getInt("status"));
    }

    @Test
    public void sanitizeReturnsCopiesAndKeepsTheInput() throws Exception {
        JSONObject original = entry("https://api.example.com/me?token=1", headers("Authorization", "Bearer abc"),
                "{\"token\":\"t\"}", headers(), null);
        JSONArray input = new JSONArray().put(original);

        sanitizer("token").sanitize(input);

        assertEquals("https://api.example.com/me?token=1", original.getString("url"));
        assertEquals("Bearer abc", original.getJSONObject("request").getJSONObject("headers").getString("Authorization"));
        assertEquals("{\"token\":\"t\"}", original.getJSONObject("request").getString("payload"));
    }

    @Test
    public void setterReplacesTheLocalList() throws Exception {
        Gleap.getInstance().setNetworkLogPropsToIgnore(new String[]{"password"});
        Gleap.getInstance().setNetworkLogPropsToIgnore(new String[]{"token", "token", " "});

        assertEquals(1, Gleap.propsToIgnore.length());
        assertEquals("token", Gleap.propsToIgnore.getString(0));
    }
}

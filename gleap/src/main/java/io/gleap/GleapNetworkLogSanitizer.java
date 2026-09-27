package io.gleap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Applies the network log privacy rules right before network logs leave the device, so the
 * current (remote + local) configuration also covers entries that were buffered earlier:
 * <ol>
 *     <li>Entries whose url contains a blacklist entry are dropped (gleap.io / gleap.ai always).</li>
 *     <li>Request and response headers named like a prop are removed.</li>
 *     <li>The credential headers authorization, proxy-authorization, cookie and set-cookie are
 *     always masked with {@value #REDACTED}.</li>
 *     <li>Keys named like a prop are removed from JSON bodies at any depth. A prop containing a dot
 *     is also a path from the body root ({@code user.password}).</li>
 *     <li>Form fields and url query parameters named like a prop are removed.</li>
 * </ol>
 * Props match case-insensitively everywhere. A body that is not changed keeps its exact text, a body
 * that does not parse (for example a truncated one) is left as it is.
 * <p>
 * Pure org.json code, so it runs in the JVM unit tests.
 */
final class GleapNetworkLogSanitizer {
    static final String REDACTED = "[REDACTED]";
    static final List<String> DEFAULT_BLACKLIST = Collections.unmodifiableList(Arrays.asList("gleap.io", "gleap.ai"));

    private static final Set<String> MASKED_HEADERS = new HashSet<>(Arrays.asList(
            "authorization", "proxy-authorization", "cookie", "set-cookie"));
    // Keys that give a request / response its shape. They are never removed by name.
    private static final Set<String> STRUCTURAL_KEYS = new HashSet<>(Arrays.asList(
            "headers", "payload", "responsetext", "status", "statustext", "errortext"));
    // Keys holding a body: payload / responseText (spec), body / data (older Android shapes).
    private static final Set<String> BODY_KEYS = new HashSet<>(Arrays.asList(
            "payload", "responsetext", "body", "data"));

    private final Set<String> props = new HashSet<>();
    private final List<String[]> paths = new ArrayList<>();
    private final List<String> blacklist = new ArrayList<>();

    GleapNetworkLogSanitizer(Collection<String> propsToIgnore, Collection<String> blacklist) {
        if (propsToIgnore != null) {
            for (String prop : propsToIgnore) {
                String normalized = normalize(prop);
                if (normalized == null || !props.add(normalized)) {
                    continue;
                }
                if (normalized.indexOf('.') >= 0) {
                    String[] segments = normalized.split("\\.", -1);
                    boolean valid = true;
                    for (String segment : segments) {
                        if (segment.isEmpty()) {
                            valid = false;
                            break;
                        }
                    }
                    if (valid) {
                        paths.add(segments);
                    }
                }
            }
        }

        Set<String> urls = new LinkedHashSet<>();
        for (String entry : DEFAULT_BLACKLIST) {
            urls.add(entry);
        }
        if (blacklist != null) {
            for (String entry : blacklist) {
                String normalized = normalize(entry);
                if (normalized != null) {
                    urls.add(normalized);
                }
            }
        }
        this.blacklist.addAll(urls);
    }

    /**
     * The rules from the remote config (flowConfig) combined with the local setters.
     */
    static GleapNetworkLogSanitizer fromConfig() {
        GleapConfig config = GleapConfig.getInstance();
        return new GleapNetworkLogSanitizer(
                mergeStrings(config.getNetworkLogPropsToIgnore(), Gleap.propsToIgnore),
                mergeStrings(config.getBlackList(), Gleap.blacklist));
    }

    /**
     * Merges string lists (remote config + local setters): trimmed, without empty entries and
     * duplicates. Non-string elements are ignored.
     */
    static List<String> mergeStrings(JSONArray... arrays) {
        Set<String> merged = new LinkedHashSet<>();
        if (arrays != null) {
            for (JSONArray array : arrays) {
                if (array == null) {
                    continue;
                }
                for (int i = 0; i < array.length(); i++) {
                    Object value = array.opt(i);
                    if (value instanceof String) {
                        String trimmed = ((String) value).trim();
                        if (!trimmed.isEmpty()) {
                            merged.add(trimmed);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(merged);
    }

    /**
     * Checks a url against the current blacklist only (recording time, cheap).
     */
    static boolean isBlacklistedByConfig(String url) {
        GleapConfig config = GleapConfig.getInstance();
        return new GleapNetworkLogSanitizer(null, mergeStrings(config.getBlackList(), Gleap.blacklist)).isBlacklisted(url);
    }

    static boolean isCredentialHeader(String name) {
        return name != null && MASKED_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }

    boolean isBlacklisted(String url) {
        if (url == null) {
            return false;
        }
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        for (String entry : blacklist) {
            if (lowerUrl.contains(entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns sanitized copies of the entries; the input is not modified. Blacklisted entries,
     * entries that are not objects and entries that fail to sanitize are left out.
     */
    JSONArray sanitize(JSONArray entries) {
        JSONArray result = new JSONArray();
        if (entries == null) {
            return result;
        }
        for (int i = 0; i < entries.length(); i++) {
            Object entry = entries.opt(i);
            if (!(entry instanceof JSONObject)) {
                continue;
            }
            JSONObject sanitized = sanitizeEntry((JSONObject) entry);
            if (sanitized != null) {
                result.put(sanitized);
            }
        }
        return result;
    }

    /**
     * Returns a sanitized copy of one entry, or null when it is blacklisted or can't be sanitized.
     */
    JSONObject sanitizeEntry(JSONObject original) {
        try {
            Object url = original.opt("url");
            if (url instanceof String && isBlacklisted((String) url)) {
                return null;
            }

            JSONObject entry = (JSONObject) deepCopy(original);
            if (url instanceof String) {
                entry.put("url", sanitizeUrl((String) url));
            }
            sanitizeSide(entry, "request");
            sanitizeSide(entry, "response");
            return entry;
        } catch (Throwable error) {
            // Never send an entry that could not be redacted.
            return null;
        }
    }

    String sanitizeUrl(String url) {
        if (url == null || props.isEmpty()) {
            return url;
        }
        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return url;
        }
        int fragmentStart = url.indexOf('#', queryStart);
        String query = fragmentStart < 0 ? url.substring(queryStart + 1) : url.substring(queryStart + 1, fragmentStart);
        String fragment = fragmentStart < 0 ? "" : url.substring(fragmentStart);
        String cleaned = removeParams(query);
        if (cleaned == null) {
            return url;
        }
        return url.substring(0, queryStart) + (cleaned.isEmpty() ? "" : "?" + cleaned) + fragment;
    }

    private void sanitizeSide(JSONObject entry, String side) throws Exception {
        Object value = entry.opt(side);
        if (value instanceof JSONObject) {
            sanitizeMessage((JSONObject) value);
            capBodies((JSONObject) value);
        } else if (value instanceof String) {
            entry.put(side, Networklog.capBody(sanitizeBody((String) value, null)));
        }
    }

    private void sanitizeMessage(JSONObject message) throws Exception {
        String contentType = findContentType(message.opt("headers"));
        for (String key : keysOf(message)) {
            String lowerKey = key.toLowerCase(Locale.ROOT);
            Object value = message.opt(key);
            if (lowerKey.equals("headers")) {
                message.put(key, sanitizeHeaders(value));
            } else if (!STRUCTURAL_KEYS.contains(lowerKey) && props.contains(lowerKey)) {
                message.remove(key);
            } else if (BODY_KEYS.contains(lowerKey)) {
                if (value instanceof String) {
                    message.put(key, sanitizeBody((String) value, contentType));
                } else if (value instanceof JSONObject || value instanceof JSONArray) {
                    removeKeys(value);
                    removePaths(value, false);
                }
            } else if (!STRUCTURAL_KEYS.contains(lowerKey) && (value instanceof JSONObject || value instanceof JSONArray)) {
                // Older shapes put body fields directly into the request / response object.
                removeKeys(value);
            }
        }
        // Paths from the object root for the older shapes; never through the structural keys.
        removePaths(message, true);
    }

    private Object sanitizeHeaders(Object headers) {
        JSONObject object = null;
        if (headers instanceof JSONObject) {
            object = (JSONObject) headers;
        } else if (headers instanceof String) {
            Object parsed = parseJson((String) headers);
            if (parsed instanceof JSONObject) {
                object = (JSONObject) parsed;
            }
        }
        if (object == null) {
            return headers;
        }
        for (String name : keysOf(object)) {
            String lowerName = name.toLowerCase(Locale.ROOT);
            try {
                if (props.contains(lowerName)) {
                    object.remove(name);
                } else if (MASKED_HEADERS.contains(lowerName)) {
                    object.put(name, REDACTED);
                }
            } catch (Exception ignore) {
                object.remove(name);
            }
        }
        return object;
    }

    /**
     * Redacts one body text. Returns the same string instance when nothing was removed.
     */
    String sanitizeBody(String body, String contentType) {
        if (body == null || props.isEmpty()) {
            return body;
        }
        char first = firstNonWhitespace(body);
        if (first == '{' || first == '[') {
            Object parsed = parseJson(body);
            if (parsed == null) {
                return body;
            }
            boolean changed = removeKeys(parsed);
            changed |= removePaths(parsed, false);
            return changed ? parsed.toString() : body;
        }
        if (isFormBody(body, contentType)) {
            String cleaned = removeParams(body);
            return cleaned != null ? cleaned : body;
        }
        return body;
    }

    private boolean removeKeys(Object node) {
        boolean changed = false;
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            for (String key : keysOf(object)) {
                if (props.contains(key.toLowerCase(Locale.ROOT))) {
                    object.remove(key);
                    changed = true;
                } else {
                    changed |= removeKeys(object.opt(key));
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                changed |= removeKeys(array.opt(i));
            }
        }
        return changed;
    }

    private boolean removePaths(Object root, boolean skipStructural) {
        boolean changed = false;
        for (String[] path : paths) {
            if (skipStructural && STRUCTURAL_KEYS.contains(path[0])) {
                continue;
            }
            changed |= removePath(root, path, 0);
        }
        return changed;
    }

    private static boolean removePath(Object node, String[] path, int index) {
        boolean changed = false;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                changed |= removePath(array.opt(i), path, index);
            }
        } else if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            for (String key : keysOf(object)) {
                if (!key.toLowerCase(Locale.ROOT).equals(path[index])) {
                    continue;
                }
                if (index == path.length - 1) {
                    object.remove(key);
                    changed = true;
                } else {
                    changed |= removePath(object.opt(key), path, index + 1);
                }
            }
        }
        return changed;
    }

    /**
     * Removes the params named like a prop from a form body or query string. Returns null when
     * nothing was removed.
     */
    private String removeParams(String params) {
        if (params == null || params.isEmpty() || props.isEmpty()) {
            return null;
        }
        String[] parts = params.split("&", -1);
        StringBuilder kept = new StringBuilder();
        boolean changed = false;
        for (String part : parts) {
            int separator = part.indexOf('=');
            String name = separator >= 0 ? part.substring(0, separator) : part;
            if (!name.isEmpty() && props.contains(decodeParamName(name).toLowerCase(Locale.ROOT))) {
                changed = true;
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(part);
        }
        return changed ? kept.toString() : null;
    }

    private static String decodeParamName(String name) {
        try {
            return URLDecoder.decode(name, "UTF-8");
        } catch (Exception malformed) {
            return name;
        }
    }

    private static boolean isFormBody(String body, String contentType) {
        if (contentType != null) {
            return contentType.toLowerCase(Locale.ROOT).contains("x-www-form-urlencoded");
        }
        // No content type known: only bodies that look exactly like a form (a=1&b=2).
        if (body.isEmpty() || body.indexOf('=') <= 0) {
            return false;
        }
        for (int i = 0; i < body.length(); i++) {
            if (Character.isWhitespace(body.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String findContentType(Object headers) {
        if (!(headers instanceof JSONObject)) {
            return null;
        }
        JSONObject object = (JSONObject) headers;
        for (String name : keysOf(object)) {
            if (name.equalsIgnoreCase("content-type")) {
                Object value = object.opt(name);
                return value instanceof String ? (String) value : null;
            }
        }
        return null;
    }

    private static void capBodies(JSONObject message) throws Exception {
        for (String key : keysOf(message)) {
            Object value = message.opt(key);
            if (value instanceof String && BODY_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                message.put(key, Networklog.capBody((String) value));
            }
        }
    }

    /**
     * Parses a JSON object or array. Returns null for anything else, including trailing garbage.
     */
    static Object parseJson(String text) {
        try {
            char first = firstNonWhitespace(text);
            if (first != '{' && first != '[') {
                return null;
            }
            JSONTokener tokener = new JSONTokener(text);
            Object value = tokener.nextValue();
            if (!(value instanceof JSONObject) && !(value instanceof JSONArray)) {
                return null;
            }
            if (tokener.nextClean() != 0) {
                return null;
            }
            return value;
        } catch (Exception notJson) {
            return null;
        }
    }

    /**
     * Copies JSON objects and arrays recursively. Maps and collections are converted to JSON.
     */
    static Object deepCopy(Object value) throws Exception {
        if (value instanceof JSONObject) {
            JSONObject source = (JSONObject) value;
            JSONObject copy = new JSONObject();
            for (String key : keysOf(source)) {
                Object child = source.opt(key);
                if (child != null) {
                    copy.put(key, deepCopy(child));
                }
            }
            return copy;
        }
        if (value instanceof JSONArray) {
            JSONArray source = (JSONArray) value;
            JSONArray copy = new JSONArray();
            for (int i = 0; i < source.length(); i++) {
                Object child = source.opt(i);
                copy.put(child == null ? JSONObject.NULL : deepCopy(child));
            }
            return copy;
        }
        if (value instanceof Map || value instanceof Collection) {
            Object wrapped = JSONObject.wrap(value);
            return wrapped instanceof JSONObject || wrapped instanceof JSONArray ? deepCopy(wrapped) : JSONObject.NULL;
        }
        return value;
    }

    static List<String> keysOf(JSONObject object) {
        List<String> keys = new ArrayList<>();
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }
        return keys;
    }

    private static char firstNonWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c;
            }
        }
        return 0;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}

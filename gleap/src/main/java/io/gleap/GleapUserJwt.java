package io.gleap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Identity tokens ({@link Gleap#setUserJwt(String)}): HS256 JWTs signed by the app's backend.
 * The SDK only reads the payload; the server checks the signature. The token is kept in memory
 * only.
 */
final class GleapUserJwt {
    static final int MAX_LENGTH = 4096;
    // An identity token that expires within this window is renewed through the JWT provider
    // before an identify is sent again (file session refresh, replay).
    static final long REFRESH_WINDOW_MS = 5 * 60 * 1000;

    private GleapUserJwt() {
    }

    /**
     * @return true for a string shaped like a JWT: three non-empty base64url segments, at most
     * 4096 characters
     */
    static boolean isUserJwt(String token) {
        if (token == null || token.isEmpty() || token.length() > MAX_LENGTH) {
            return false;
        }
        int segments = 1;
        int segmentLength = 0;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '.') {
                if (segmentLength == 0) {
                    return false;
                }
                segments++;
                segmentLength = 0;
            } else if (isBase64UrlChar(c)) {
                segmentLength++;
            } else {
                return false;
            }
        }
        return segments == 3 && segmentLength > 0;
    }

    /**
     * @return the payload's {@code userId} (or {@code user_id}) as a string, or null when the
     * token is not a JWT or has no user id (a non-empty string or a finite number)
     */
    static String userIdOf(String token) {
        JSONObject payload = payloadOf(token);
        if (payload == null) {
            return null;
        }
        Object userId = payload.opt("userId");
        if (userId == null || userId == JSONObject.NULL) {
            userId = payload.opt("user_id");
        }
        if (userId instanceof String) {
            return ((String) userId).isEmpty() ? null : (String) userId;
        }
        if (userId instanceof Integer || userId instanceof Long) {
            return userId.toString();
        }
        if (userId instanceof Number) {
            double value = ((Number) userId).doubleValue();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            // 42.0 is the user 42, like JavaScript's String(42).
            return value == Math.rint(value) && Math.abs(value) < 1e15
                    ? Long.toString((long) value) : userId.toString();
        }
        return null;
    }

    /**
     * @return the payload's {@code exp} (seconds since 1970), or null without one
     */
    static Long expirationOf(String token) {
        JSONObject payload = payloadOf(token);
        Object exp = payload != null ? payload.opt("exp") : null;
        return exp instanceof Number ? ((Number) exp).longValue() : null;
    }

    /**
     * @return true when the token carries an {@code exp} less than {@code windowMs} after
     * {@code nowMs}
     */
    static boolean expiresWithin(String token, long windowMs, long nowMs) {
        Long exp = expirationOf(token);
        return exp != null && exp * 1000 - nowMs < windowMs;
    }

    /**
     * @return {@code token_expired} or {@code token_invalid} for an identify refusal (HTTP 401
     * whose body names the code), null for anything else
     */
    static String refusalCode(int status, String body) {
        if (status != 401 || body == null) {
            return null;
        }
        if (body.contains("token_expired")) {
            return "token_expired";
        }
        if (body.contains("token_invalid")) {
            return "token_invalid";
        }
        return null;
    }

    /**
     * @return true for an identify refused because the project enforces identity verification and
     * the identify had no valid signature (HTTP 401 whose body names {@code signature_required})
     */
    static boolean isSignatureRequired(int status, String body) {
        return status == 401 && body != null && body.contains("signature_required");
    }

    /**
     * What the token says, for "was this identify sent already": its {@code kid} and its claims
     * without iat/exp/nbf/jti, as JSON with sorted keys. A token re-minted for the same user and
     * data gives the same value; a changed claim or key does not.
     *
     * @return null when the token is not a JWT
     */
    static String identityFingerprint(String token) {
        JSONObject payload = payloadOf(token);
        if (payload == null) {
            return null;
        }
        JSONObject header = segmentOf(token, 0);
        StringBuilder out = new StringBuilder("{\"kid\":");
        canonical(header != null ? header.opt("kid") : null, out);
        out.append(",\"claims\":");
        JSONObject claims = new JSONObject();
        Iterator<String> keys = payload.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!key.equals("iat") && !key.equals("exp") && !key.equals("nbf") && !key.equals("jti")) {
                try {
                    claims.put(key, payload.opt(key));
                } catch (Exception ignored) {
                }
            }
        }
        canonical(claims, out);
        return out.append('}').toString();
    }

    private static void canonical(Object value, StringBuilder out) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            Iterator<String> it = object.keys();
            while (it.hasNext()) {
                keys.add(it.next());
            }
            Collections.sort(keys);
            out.append('{');
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(JSONObject.quote(keys.get(i))).append(':');
                canonical(object.opt(keys.get(i)), out);
            }
            out.append('}');
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            out.append('[');
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                canonical(array.opt(i), out);
            }
            out.append(']');
        } else if (value == null || value == JSONObject.NULL) {
            out.append("null");
        } else if (value instanceof String) {
            out.append(JSONObject.quote((String) value));
        } else {
            out.append(String.valueOf(value));
        }
    }

    /**
     * @return true for an identify refused because the session belongs to another user (HTTP
     * 400 whose body names {@code identity_mismatch})
     */
    static boolean isIdentityMismatch(int status, String body) {
        return status == 400 && body != null && body.contains("identity_mismatch");
    }

    private static JSONObject payloadOf(String token) {
        return segmentOf(token, 1);
    }

    /** The decoded JSON object of the token's header (0) or payload (1). */
    private static JSONObject segmentOf(String token, int index) {
        if (!isUserJwt(token)) {
            return null;
        }
        String[] segments = token.split("\\.");
        byte[] bytes = decodeBase64Url(segments[index]);
        if (bytes == null) {
            return null;
        }
        try {
            return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception notJson) {
            return null;
        }
    }

    private static boolean isBase64UrlChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
    }

    private static int valueOf(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '-') return 62;
        if (c == '_') return 63;
        return -1;
    }

    /**
     * Base64url without padding (android.util.Base64 is not there in unit tests, java.util.Base64
     * needs API 26).
     */
    static byte[] decodeBase64Url(String value) {
        if (value.length() % 4 == 1) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(value.length() * 3 / 4);
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < value.length(); i++) {
            int v = valueOf(value.charAt(i));
            if (v < 0) {
                return null;
            }
            buffer = (buffer << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}

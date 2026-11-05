package io.gleap;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

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

    private static JSONObject payloadOf(String token) {
        if (!isUserJwt(token)) {
            return null;
        }
        int start = token.indexOf('.') + 1;
        int end = token.indexOf('.', start);
        byte[] bytes = decodeBase64Url(token.substring(start, end));
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

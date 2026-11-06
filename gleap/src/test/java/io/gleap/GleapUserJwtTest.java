package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Identity tokens: the payload is read without checking the signature, only well-shaped tokens
 * with a user id count, and only a 401 naming the code is a token refusal.
 */
public class GleapUserJwtTest {
    static String jwt(String payloadJson) {
        return jwt(payloadJson, "c2lnbmF0dXJl");
    }

    static String jwt(String payloadJson, String signature) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8)) + "." + signature;
    }

    @Test
    public void theUserIdIsReadFromThePayload() {
        assertEquals("user-1", GleapUserJwt.userIdOf(jwt("{\"userId\":\"user-1\",\"email\":\"ada@example.com\"}")));
        assertEquals("42", GleapUserJwt.userIdOf(jwt("{\"user_id\":42}")));
        assertEquals("42", GleapUserJwt.userIdOf(jwt("{\"userId\":42.0}")));
        assertEquals("Jürgen ✓", GleapUserJwt.userIdOf(jwt("{\"userId\":\"Jürgen ✓\"}")));
        assertNull(GleapUserJwt.userIdOf(jwt("{\"email\":\"ada@example.com\"}")));
        assertNull(GleapUserJwt.userIdOf(jwt("{\"userId\":\"\"}")));
        assertNull(GleapUserJwt.userIdOf(jwt("{\"userId\":true}")));
        assertNull(GleapUserJwt.userIdOf(jwt("{\"userId\":{\"id\":1}}")));
        assertNull(GleapUserJwt.userIdOf(jwt("not json")));
    }

    @Test
    public void onlyThreeBase64UrlSegmentsOfAtMost4096CharactersAreAJwt() {
        assertTrue(GleapUserJwt.isUserJwt(jwt("{\"userId\":\"u\"}")));
        assertFalse(GleapUserJwt.isUserJwt("a3f1c9e2"));
        assertFalse(GleapUserJwt.isUserJwt("aaa.bbb"));
        assertFalse(GleapUserJwt.isUserJwt("aaa..ccc"));
        assertFalse(GleapUserJwt.isUserJwt("aaa.bbb."));
        assertFalse(GleapUserJwt.isUserJwt("aa+a.bbb.ccc"));
        assertFalse(GleapUserJwt.isUserJwt("aaa.bbb.ccc=="));
        assertFalse(GleapUserJwt.isUserJwt(null));
        StringBuilder signature = new StringBuilder();
        for (int i = 0; i < 4096; i++) {
            signature.append('s');
        }
        assertNull(GleapUserJwt.userIdOf(jwt("{\"userId\":\"u\"}", signature.toString())));
    }

    @Test
    public void theExpiryWindow() {
        long now = 1800000000000L;
        assertTrue(GleapUserJwt.expiresWithin(jwt("{\"userId\":\"u\",\"exp\":1800000060}"), GleapUserJwt.REFRESH_WINDOW_MS, now));
        assertTrue(GleapUserJwt.expiresWithin(jwt("{\"userId\":\"u\",\"exp\":1799999000}"), GleapUserJwt.REFRESH_WINDOW_MS, now));
        assertFalse(GleapUserJwt.expiresWithin(jwt("{\"userId\":\"u\",\"exp\":1800003600}"), GleapUserJwt.REFRESH_WINDOW_MS, now));
        assertFalse("no exp never expires", GleapUserJwt.expiresWithin(jwt("{\"userId\":\"u\"}"), GleapUserJwt.REFRESH_WINDOW_MS, now));
        assertFalse(GleapUserJwt.expiresWithin("legacy-hash", GleapUserJwt.REFRESH_WINDOW_MS, now));
    }

    @Test
    public void onlyA401NamingTheCodeIsATokenRefusal() {
        assertEquals("token_expired", GleapUserJwt.refusalCode(401, "{\"details\":{\"value\":\"token_expired\"}}"));
        assertEquals("token_invalid", GleapUserJwt.refusalCode(401, "{\"message\":\"token_invalid: bad signature\"}"));
        assertNull(GleapUserJwt.refusalCode(401, "{\"details\":{\"value\":\"signature_required\"}}"));
        assertNull(GleapUserJwt.refusalCode(400, "{\"details\":{\"value\":\"token_expired\"}}"));
        assertNull(GleapUserJwt.refusalCode(401, null));
    }

    @Test
    public void signatureRequiredIsRecognised() {
        assertTrue(GleapUserJwt.isSignatureRequired(401, "{\"details\":{\"value\":\"signature_required\"}}"));
        assertFalse(GleapUserJwt.isSignatureRequired(401, "{\"details\":{\"value\":\"token_invalid\"}}"));
        assertFalse(GleapUserJwt.isSignatureRequired(400, "signature_required"));
    }

    @Test
    public void aTokenSayingSomethingNewMakesAnUnchangedIdentifyDifferent() {
        String first = jwt("{\"userId\":\"user-1\",\"plan\":\"pro\",\"iat\":0,\"exp\":1}");
        String reminted = jwt("{\"exp\":2,\"plan\":\"pro\",\"userId\":\"user-1\",\"iat\":1,\"jti\":\"n2\"}");
        String changed = jwt("{\"userId\":\"user-1\",\"plan\":\"enterprise\",\"exp\":2}");
        GleapSessionProperties sent = new GleapSessionProperties("user-1", "Ada", null, first);

        GleapSessionProperties same = new GleapSessionProperties("user-1", "Ada", null, first);
        GleapSessionProperties renewed = new GleapSessionProperties("user-1", "Ada", null, reminted);
        GleapSessionProperties other = new GleapSessionProperties("user-1", "Ada", null, changed);
        assertTrue(sent.equals(same));
        assertTrue("a token re-minted with the same claims is the same identify", sent.equals(renewed));
        assertFalse(sent.equals(other));

        // A legacy hash is still not compared.
        GleapSessionProperties legacy = new GleapSessionProperties("user-1", "Ada", null, "hash-a");
        assertTrue(new GleapSessionProperties("user-1", "Ada", null, "hash-b").equals(legacy));
    }

    @Test
    public void aJwtOnlyIdentifyComparesTheUserAndTheToken() {
        String token = jwt("{\"userId\":\"user-1\"}");
        GleapSessionProperties current = new GleapSessionProperties("user-1", "Ada", "ada@example.com", token);
        current.setValue(199.95);
        GleapSessionProperties identify = new GleapSessionProperties("user-1");
        identify.setHash(token);
        identify.jwtOnly = true;

        assertTrue(current.equals(identify));
        identify.setUserId("user-2");
        assertFalse(current.equals(identify));
    }
}

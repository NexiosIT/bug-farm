package com.bugfarm.lunch.orders.teams;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Checks the {@code Authorization: HMAC <base64>} header Teams puts on outgoing-webhook calls:
 * the Base64 of HMAC-SHA256 over the raw request body, keyed with the Base64-decoded security token.
 * A blank or malformed token rejects every request.
 */
public class HmacVerifier {

    private static final String PREFIX = "HMAC ";
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;

    public HmacVerifier(String base64SecurityToken) {
        this.key = decodeKey(base64SecurityToken);
    }

    public boolean verify(byte[] body, String authorizationHeader) {
        if (key == null || body == null || authorizationHeader == null
                || !authorizationHeader.startsWith(PREFIX)) {
            return false;
        }
        byte[] given;
        try {
            given = Base64.getDecoder().decode(authorizationHeader.substring(PREFIX.length()).trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return MessageDigest.isEqual(mac.doFinal(body), given);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static byte[] decodeKey(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(token.trim());
            return decoded.length == 0 ? null : decoded;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

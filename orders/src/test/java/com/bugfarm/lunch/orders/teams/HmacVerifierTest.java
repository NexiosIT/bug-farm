package com.bugfarm.lunch.orders.teams;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class HmacVerifierTest {

    private static final byte[] KEY = "test-secret-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final String TOKEN = Base64.getEncoder().encodeToString(KEY);
    private static final byte[] BODY =
            "{\"type\":\"message\",\"text\":\"<at>SandwichBot</at>&nbsp;menu\"}".getBytes(StandardCharsets.UTF_8);

    private final HmacVerifier verifier = new HmacVerifier(TOKEN);

    static String header(byte[] key, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return "HMAC " + Base64.getEncoder().encodeToString(mac.doFinal(body));
    }

    @Test
    void validSignaturePasses() throws Exception {
        assertThat(verifier.verify(BODY, header(KEY, BODY))).isTrue();
    }

    @Test
    void tamperedBodyFails() throws Exception {
        String signed = header(KEY, BODY);
        byte[] tampered = "{\"type\":\"message\",\"text\":\"cancel\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.verify(tampered, signed)).isFalse();
    }

    @Test
    void missingHeaderFails() {
        assertThat(verifier.verify(BODY, null)).isFalse();
        assertThat(verifier.verify(BODY, "")).isFalse();
    }

    @Test
    void headerWithoutHmacPrefixFails() throws Exception {
        String bare = header(KEY, BODY).substring("HMAC ".length());

        assertThat(verifier.verify(BODY, bare)).isFalse();
        assertThat(verifier.verify(BODY, "Bearer " + bare)).isFalse();
    }

    @Test
    void emptyTokenAlwaysFails() throws Exception {
        // An unconfigured token must never accept anything, not even an otherwise valid signature.
        assertThat(new HmacVerifier("").verify(BODY, header(KEY, BODY))).isFalse();
        assertThat(new HmacVerifier("   ").verify(BODY, header(KEY, BODY))).isFalse();
        assertThat(new HmacVerifier(null).verify(BODY, header(KEY, BODY))).isFalse();
    }
}

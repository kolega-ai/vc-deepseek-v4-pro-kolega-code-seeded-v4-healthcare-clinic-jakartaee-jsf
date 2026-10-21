package com.oakfield.clinic.decision;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opaque signed token codec tests: signature verification, expiry, tamper detection. */
class TokenCodecTest {

    @Test
    void sessionTokenRoundTripsAndVerifies() {
        long expiry = Instant.now().getEpochSecond() + 3600;
        String token = TokenCodec.signSession("secret", "token-123", expiry);
        TokenCodec.SessionTokenParts parts = TokenCodec.parseAndVerifySession("secret", token);
        assertThat(parts.tokenId()).isEqualTo("token-123");
        assertThat(parts.expiryEpochSeconds()).isEqualTo(expiry);
    }

    @Test
    void tamperedSessionTokenIsRejected() {
        long expiry = Instant.now().getEpochSecond() + 3600;
        String token = TokenCodec.signSession("secret", "token-123", expiry);
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThatThrownBy(() -> TokenCodec.parseAndVerifySession("secret", tampered))
                .isInstanceOf(NotAuthenticatedException.class);
    }

    @Test
    void wrongSecretIsRejected() {
        long expiry = Instant.now().getEpochSecond() + 3600;
        String token = TokenCodec.signSession("secret-a", "token-123", expiry);
        assertThatThrownBy(() -> TokenCodec.parseAndVerifySession("secret-b", token))
                .isInstanceOf(NotAuthenticatedException.class);
    }

    @Test
    void expiredSessionTokenIsRejected() {
        long expiry = Instant.now().getEpochSecond() - 60;
        String token = TokenCodec.signSession("secret", "token-123", expiry);
        assertThatThrownBy(() -> TokenCodec.parseAndVerifySession("secret", token))
                .isInstanceOf(NotAuthenticatedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void coreTokenCarriesScopeAndSubject() {
        long expiry = Instant.now().getEpochSecond() + 600;
        String token = TokenCodec.signCoreToken("core-secret", "refill.transmit", "42", expiry);
        TokenCodec.CoreTokenParts parts = TokenCodec.parseAndVerifyCore("core-secret", token);
        assertThat(parts.scope()).isEqualTo("refill.transmit");
        assertThat(parts.subject()).isEqualTo("42");
        assertThat(parts.expiryEpochSeconds()).isEqualTo(expiry);
    }

    @Test
    void coreTokenWithWrongSecretIsRejected() {
        long expiry = Instant.now().getEpochSecond() + 600;
        String token = TokenCodec.signCoreToken("core-secret", "refill.transmit", "42", expiry);
        assertThatThrownBy(() -> TokenCodec.parseAndVerifyCore("other-secret", token))
                .isInstanceOf(NotAuthenticatedException.class);
    }
}

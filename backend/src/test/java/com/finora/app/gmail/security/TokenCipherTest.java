package com.finora.app.gmail.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class TokenCipherTest {
    @Test void encryptsAndDecryptsTokens() {
        TokenCipher cipher = new TokenCipher("test-key-that-is-not-used-in-production");
        String encrypted = cipher.encrypt("secret-access-token");
        assertThat(encrypted).isNotEqualTo("secret-access-token");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("secret-access-token");
    }
}

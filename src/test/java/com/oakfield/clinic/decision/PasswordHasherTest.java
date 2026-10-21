package com.oakfield.clinic.decision;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Password hashing: PBKDF2 format, verification, and the strength policy. */
class PasswordHasherTest {

    @Test
    void hashVerifiesAgainstItself() {
        String hash = PasswordHasher.hash("CorrectHorse2026!");
        assertThat(hash).startsWith("pbkdf2$");
        assertThat(PasswordHasher.verify("CorrectHorse2026!", hash)).isTrue();
        assertThat(PasswordHasher.verify("wrong-password", hash)).isFalse();
        assertThat(PasswordHasher.verify(null, hash)).isFalse();
    }

    @Test
    void hashesAreSaltedAndUnique() {
        String a = PasswordHasher.hash("SamePassword1");
        String b = PasswordHasher.hash("SamePassword1");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void malformedHashesAreRejected() {
        assertThat(PasswordHasher.verify("anything", "garbage")).isFalse();
        assertThat(PasswordHasher.verify("anything", "pbkdf2$bad")).isFalse();
        assertThat(PasswordHasher.verify("anything", "")).isFalse();
    }

    @Test
    void strengthPolicy() {
        assertThat(PasswordHasher.strengthIssue("short1A")).isNotNull();       // too short
        assertThat(PasswordHasher.strengthIssue("alllowercase123")).isNotNull();
        assertThat(PasswordHasher.strengthIssue("ALLUPPERCASE123")).isNotNull();
        assertThat(PasswordHasher.strengthIssue("NoDigitsHere")).isNotNull();
        assertThat(PasswordHasher.strengthIssue("GoodPassword2026")).isNull();
    }
}

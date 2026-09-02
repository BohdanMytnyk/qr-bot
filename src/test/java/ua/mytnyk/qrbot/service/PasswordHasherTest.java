package ua.mytnyk.qrbot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesAndMatchesOnlyTheOriginalPassword() {
        PasswordHasher.PasswordValue first = hasher.hash("correct horse battery staple");
        PasswordHasher.PasswordValue second = hasher.hash("correct horse battery staple");
        assertThat(first.salt()).hasSize(16).isNotEqualTo(second.salt());
        assertThat(first.hash()).hasSize(32).isNotEqualTo(second.hash());
        assertThat(hasher.matches("correct horse battery staple", first.salt(), first.hash())).isTrue();
        assertThat(hasher.matches("wrong", first.salt(), first.hash())).isFalse();
    }

    @Test
    void rejectsMissingStoredPasswordData() {
        assertThat(hasher.matches("password", null, new byte[]{1})).isFalse();
        assertThat(hasher.matches("password", new byte[]{1}, null)).isFalse();
    }
}

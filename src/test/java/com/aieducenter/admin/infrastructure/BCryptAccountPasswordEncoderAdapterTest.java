package com.aieducenter.admin.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * BCryptPasswordEncoderAdapter 适配器测试。
 */
class BCryptAccountPasswordEncoderAdapterTest {

    private BCryptPasswordEncoderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new BCryptPasswordEncoderAdapter();
    }

    @Test
    void given_plainPassword_when_encode_then_returnHashedPassword() {
        // Given
        String plainPassword = "Test1234";

        // When
        String encoded = adapter.encode(plainPassword);

        // Then
        assertThat(encoded).isNotNull();
        assertThat(encoded).isNotEqualTo(plainPassword);
        assertThat(encoded).startsWith("$2a$10$"); // BCrypt with strength 10
        assertThat(encoded.length()).isEqualTo(60); // BCrypt hash length
    }

    @Test
    void given_samePassword_when_encodeMultipleTimes_then_returnDifferentHashes() {
        // Given
        String plainPassword = "Test1234";

        // When
        String hash1 = adapter.encode(plainPassword);
        String hash2 = adapter.encode(plainPassword);

        // Then - BCrypt uses random salt, so hashes should be different
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void given_correctPassword_when_matches_then_returnTrue() {
        // Given
        String plainPassword = "Test1234";
        String encoded = adapter.encode(plainPassword);

        // When
        boolean matches = adapter.matches(plainPassword, encoded);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    void given_incorrectPassword_when_matches_then_returnFalse() {
        // Given
        String plainPassword = "Test1234";
        String encoded = adapter.encode(plainPassword);
        String wrongPassword = "WrongPassword";

        // When
        boolean matches = adapter.matches(wrongPassword, encoded);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    void given_emptyPassword_when_encode_then_returnHashedPassword() {
        // Given
        String emptyPassword = "";

        // When
        String encoded = adapter.encode(emptyPassword);

        // Then
        assertThat(encoded).isNotNull();
        assertThat(encoded).startsWith("$2a$10$");
        assertThat(encoded.length()).isEqualTo(60);
    }

    @Test
    void given_nullPassword_when_encode_then_throwIllegalArgumentException() {
        // Given
        String nullPassword = null;

        // When & Then
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> adapter.encode(nullPassword)
        );
    }

    @Test
    void given_nullPlainPassword_when_matches_then_throwIllegalArgumentException() {
        // Given
        String plainPassword = "Test1234";
        String encoded = adapter.encode(plainPassword);

        // When & Then
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> adapter.matches(null, encoded)
        );
    }

    @Test
    void given_nullEncodedPassword_when_matches_then_returnFalse() {
        // Given
        String plainPassword = "Test1234";

        // When
        boolean matches = adapter.matches(plainPassword, null);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    void given_invalidHashFormat_when_matches_then_returnFalse() {
        // Given
        String plainPassword = "Test1234";
        String invalidHash = "invalid_hash_format";

        // When
        boolean matches = adapter.matches(plainPassword, invalidHash);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    void given_specialCharactersPassword_when_encode_and_matches_then_success() {
        // Given
        String specialPassword = "P@$$w0rd!#$%^&*()";

        // When
        String encoded = adapter.encode(specialPassword);
        boolean matches = adapter.matches(specialPassword, encoded);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    void given_longPassword_when_encode_and_matches_then_success() {
        // Given
        String longPassword = "a".repeat(100); // 100 characters

        // When
        String encoded = adapter.encode(longPassword);
        boolean matches = adapter.matches(longPassword, encoded);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    void given_unicodePassword_when_encode_and_matches_then_success() {
        // Given
        String unicodePassword = "密码测试🔒";

        // When
        String encoded = adapter.encode(unicodePassword);
        boolean matches = adapter.matches(unicodePassword, encoded);

        // Then
        assertThat(matches).isTrue();
    }
}

package com.aieducenter.admin.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aieducenter.admin.domain.port.PasswordEncoder;

/**
 * PasswordEncoderService 领域服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PasswordEncoderServiceTest {

    @Mock
    private PasswordEncoder encoderPort;

    private PasswordEncoderService passwordEncoderService;

    @BeforeEach
    void setUp() {
        passwordEncoderService = new PasswordEncoderService(encoderPort);
    }

    @Test
    void given_plainPassword_when_encodePassword_then_delegateToPort() {
        // Given
        String plainPassword = "Test1234";
        String expectedEncoded = "$2a$10$encodedPassword";
        when(encoderPort.encode(plainPassword)).thenReturn(expectedEncoded);

        // When
        String actualEncoded = passwordEncoderService.encodePassword(plainPassword);

        // Then
        assertThat(actualEncoded).isEqualTo(expectedEncoded);
        verify(encoderPort).encode(plainPassword);
    }

    @Test
    void given_correctPassword_when_verifyPassword_then_returnTrue() {
        // Given
        String plainPassword = "Test1234";
        String encodedPassword = "$2a$10$encodedPassword";
        when(encoderPort.matches(plainPassword, encodedPassword)).thenReturn(true);

        // When
        boolean result = passwordEncoderService.verifyPassword(plainPassword, encodedPassword);

        // Then
        assertThat(result).isTrue();
        verify(encoderPort).matches(plainPassword, encodedPassword);
    }

    @Test
    void given_incorrectPassword_when_verifyPassword_then_returnFalse() {
        // Given
        String plainPassword = "WrongPassword";
        String encodedPassword = "$2a$10$encodedPassword";
        when(encoderPort.matches(plainPassword, encodedPassword)).thenReturn(false);

        // When
        boolean result = passwordEncoderService.verifyPassword(plainPassword, encodedPassword);

        // Then
        assertThat(result).isFalse();
        verify(encoderPort).matches(plainPassword, encodedPassword);
    }

    @Test
    void given_nullPassword_when_encodePassword_then_delegateToPort() {
        // Given
        String nullPassword = null;
        String expectedEncoded = "$2a$10$encodedPassword";
        when(encoderPort.encode(nullPassword)).thenReturn(expectedEncoded);

        // When
        String actualEncoded = passwordEncoderService.encodePassword(nullPassword);

        // Then
        assertThat(actualEncoded).isEqualTo(expectedEncoded);
        verify(encoderPort).encode(nullPassword);
    }

    @Test
    void given_emptyPassword_when_verifyPassword_then_delegateToPort() {
        // Given
        String emptyPassword = "";
        String encodedPassword = "$2a$10$encodedPassword";
        when(encoderPort.matches(emptyPassword, encodedPassword)).thenReturn(false);

        // When
        boolean result = passwordEncoderService.verifyPassword(emptyPassword, encodedPassword);

        // Then
        assertThat(result).isFalse();
        verify(encoderPort).matches(emptyPassword, encodedPassword);
    }
}

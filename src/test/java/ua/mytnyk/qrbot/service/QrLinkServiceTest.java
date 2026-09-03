package ua.mytnyk.qrbot.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import ua.mytnyk.qrbot.config.QrBotProperties;
import ua.mytnyk.qrbot.config.QrLinkProperties;
import ua.mytnyk.qrbot.domain.QrCode;
import ua.mytnyk.qrbot.domain.QrStatus;
import ua.mytnyk.qrbot.domain.QrType;
import ua.mytnyk.qrbot.repository.QrCodeRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QrLinkServiceTest {
    private QrCodeRepository repository;
    private SecureRandom random;
    private QrLinkService links;

    @BeforeEach
    void setUp() {
        repository = mock(QrCodeRepository.class);
        random = mock(SecureRandom.class);
        var telegram = new QrBotProperties();
        telegram.setBotUsername("@test_bot");
        var properties = new QrLinkProperties();
        properties.setPublicBaseUrl("https://qr.twob.cc/");
        links = new QrLinkService(repository, telegram, properties, random);
    }

    @Test
    void generatesEightCharacterCaseSensitiveAlphanumericToken() {
        when(random.nextInt(62)).thenReturn(10, 37, 0, 61, 11, 38, 1, 60);
        var expected = qr("Ab0zBc1y");
        when(repository.insert(expected)).thenReturn(expected);

        assertThat(links.insertWithToken(QrLinkServiceTest::qr)).isEqualTo(expected);
        verify(repository).insert(expected);
    }

    @Test
    void retriesInsertAfterUniqueIndexRejectsCollision() {
        when(random.nextInt(62)).thenReturn(
                10, 10, 10, 10, 10, 10, 10, 10,
                11, 11, 11, 11, 11, 11, 11, 11);
        var collision = qr("AAAAAAAA");
        var inserted = qr("BBBBBBBB");
        when(repository.insert(collision)).thenThrow(new DuplicateKeyException("index: qr_token_unique dup key"));
        when(repository.insert(inserted)).thenReturn(inserted);

        assertThat(links.insertWithToken(QrLinkServiceTest::qr)).isEqualTo(inserted);
        verify(repository).insert(collision);
        verify(repository).insert(inserted);
    }

    @Test
    void failsAfterBoundedNumberOfRejectedInsertCandidates() {
        when(random.nextInt(62)).thenReturn(10);
        var collision = qr("AAAAAAAA");
        when(repository.insert(collision)).thenThrow(new DuplicateKeyException("index: qr_token_unique dup key"));

        assertThatThrownBy(() -> links.insertWithToken(QrLinkServiceTest::qr))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not allocate a unique QR short token");
        verify(repository, org.mockito.Mockito.times(32)).insert(collision);
    }

    @Test
    void doesNotRetryAnUnrelatedUniqueIndexViolation() {
        when(random.nextInt(62)).thenReturn(10);
        var duplicateId = new DuplicateKeyException("index: _id_ dup key");
        when(repository.insert(qr("AAAAAAAA"))).thenThrow(duplicateId);

        assertThatThrownBy(() -> links.insertWithToken(QrLinkServiceTest::qr)).isSameAs(duplicateId);
        verify(repository).insert(qr("AAAAAAAA"));
    }

    @Test
    void keepsExistingTokenAndBuildsPublicAndTelegramLinks() {
        var qr = qr("Ab3xY9Kq");

        assertThat(links.publicLink(qr)).isEqualTo("https://qr.twob.cc/Ab3xY9Kq");
        assertThat(links.telegramDeepLink(qr))
                .isEqualTo("https://t.me/test_bot?start=10000000-0000-0000-0000-000000000001");
    }

    private static QrCode qr(String token) {
        return new QrCode("10000000-0000-0000-0000-000000000001", token,
                QrType.CONTENT, QrStatus.ACTIVE, 1, -100123, 20, null, null,
                Instant.EPOCH, 0, List.of(20), null, null, null, null, null, null, null);
    }
}

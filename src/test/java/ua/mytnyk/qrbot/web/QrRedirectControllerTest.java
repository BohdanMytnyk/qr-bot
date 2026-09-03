package ua.mytnyk.qrbot.web;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ua.mytnyk.qrbot.domain.QrCode;
import ua.mytnyk.qrbot.domain.QrStatus;
import ua.mytnyk.qrbot.domain.QrType;
import ua.mytnyk.qrbot.repository.QrCodeRepository;
import ua.mytnyk.qrbot.service.QrLinkService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class QrRedirectControllerTest {
    @Test
    void redirectsKnownShortTokenToUuidTelegramLinkWithoutCaching() {
        var repository = mock(QrCodeRepository.class);
        var links = mock(QrLinkService.class);
        var qr = qr();
        when(repository.findByToken("Ab3xY9Kq")).thenReturn(Optional.of(qr));
        when(links.telegramDeepLink(qr)).thenReturn("https://t.me/test_bot?start=" + qr.id());

        var response = new QrRedirectController(repository, links, mock(MongoTemplate.class)).redirect("Ab3xY9Kq");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString("https://t.me/test_bot?start=" + qr.id());
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void returnsNotFoundForUnknownWellFormedToken() {
        var repository = mock(QrCodeRepository.class);
        var links = mock(QrLinkService.class);
        when(repository.findByToken("Ab3xY9Kq")).thenReturn(Optional.empty());

        assertThat(new QrRedirectController(repository, links, mock(MongoTemplate.class)).redirect("Ab3xY9Kq").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(links, never()).telegramDeepLink(qr());
    }

    @Test
    void rejectsMalformedTokenWithoutQueryingMongo() {
        var repository = mock(QrCodeRepository.class);
        var links = mock(QrLinkService.class);

        assertThat(new QrRedirectController(repository, links, mock(MongoTemplate.class)).redirect("too-short").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(repository, never()).findByToken("too-short");
    }

    @Test
    void redirectsActiveClassicQrToTargetAndCountsOpening() {
        var repository = mock(QrCodeRepository.class);
        var links = mock(QrLinkService.class);
        var mongo = mock(MongoTemplate.class);
        var base = qr();
        var classic = new QrCode(base.id(), base.token(), QrType.CLASSIC, QrStatus.ACTIVE, base.ownerId(),
                0, 0, null, null, base.createdAt(), 0, List.of(), null, null, null, null, null,
                null, "https://example.com/path", "https://example.com/path");
        when(repository.findByToken("Ab3xY9Kq")).thenReturn(Optional.of(classic));

        var response = new QrRedirectController(repository, links, mongo).redirect("Ab3xY9Kq");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString("https://example.com/path");
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(query.capture(), update.capture(), org.mockito.ArgumentMatchers.eq(QrCode.class));
        assertThat(query.getValue().getQueryObject().get("id")).isEqualTo(classic.id());
        assertThat(query.getValue().getQueryObject().get("status")).isEqualTo(QrStatus.ACTIVE);
        assertThat(update.getValue().getUpdateObject().get("$inc", org.bson.Document.class)
                .get("openCount")).isEqualTo(1);
        verify(links, never()).telegramDeepLink(classic);
    }

    private static QrCode qr() {
        return new QrCode("10000000-0000-0000-0000-000000000001", "Ab3xY9Kq",
                QrType.CONTENT, QrStatus.ACTIVE, 1, -100123, 20, null, null,
                Instant.EPOCH, 0, List.of(20), null, null, null, null, null, null, null);
    }
}

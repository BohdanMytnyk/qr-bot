package ua.mytnyk.qrbot.service;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.mytnyk.qrbot.domain.QrCode;
import ua.mytnyk.qrbot.domain.QrContentItem;
import ua.mytnyk.qrbot.domain.QrStatus;
import ua.mytnyk.qrbot.domain.QrType;
import ua.mytnyk.qrbot.telegram.QrContentTelegramService;
import ua.mytnyk.telegram.common.client.TelegramClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ContentQrDeliveryStrategyTest {
    private final TelegramClient telegram = mock(TelegramClient.class);
    private final QrContentTelegramService content = mock(QrContentTelegramService.class);
    private final ContentQrDeliveryStrategy strategy = new ContentQrDeliveryStrategy(telegram, content);

    @Test
    void supportsEveryCurrentQrType() {
        assertThat(strategy.supports(qr(QrType.CONTENT, null))).isTrue();
        assertThat(strategy.supports(qr(QrType.SINGLE_USE, null))).isTrue();
        assertThat(strategy.supports(qr(QrType.COUPON, null))).isTrue();
    }

    @Test
    void deliversStructuredContentDirectly() {
        var items = List.of(new QrContentItem(QrContentItem.Kind.TEXT, "hello", null, null, null, 1));
        strategy.deliver(qr(QrType.CONTENT, items), 88);
        verify(content).sendContent(88, items);
        verifyNoInteractions(telegram);
    }

    @Test
    void copiesLegacyContentForNullAndEmptyStructuredItems() {
        strategy.deliver(qr(QrType.CONTENT, null), 88);
        strategy.deliver(qr(QrType.CONTENT, List.of()), 99);
        verify(telegram).copyMessages(88, -100123, List.of(20));
        verify(telegram).copyMessages(99, -100123, List.of(20));
        verifyNoInteractions(content);
    }

    private static QrCode qr(QrType type, List<QrContentItem> items) {
        return new QrCode("id", null, type, QrStatus.ACTIVE, 1, -100123, 20, null, null,
                Instant.EPOCH, 0, List.of(20), items, null, null, null, null, null, null);
    }
}

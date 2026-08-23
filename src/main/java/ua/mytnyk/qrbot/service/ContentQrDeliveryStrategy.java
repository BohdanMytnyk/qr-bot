package ua.mytnyk.qrbot.service;

import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.domain.QrCode;
import ua.mytnyk.qrbot.domain.QrType;
import ua.mytnyk.qrbot.telegram.TelegramGateway;

@Component
public class ContentQrDeliveryStrategy implements ContentDeliveryStrategy {
    private final TelegramGateway telegram;
    public ContentQrDeliveryStrategy(TelegramGateway telegram) {
        this.telegram = telegram;
    }

    public boolean supports(QrCode qrCode) {
        return qrCode.type() == QrType.CONTENT
                || qrCode.type() == QrType.PROTECTED_CONTENT
                || qrCode.type() == QrType.ONE_TIME_CONTENT;
    }
    public void deliver(QrCode qrCode, long targetChatId) {
        if (qrCode.contentItems() != null && !qrCode.contentItems().isEmpty()) {
            telegram.sendContent(targetChatId, qrCode.contentItems());
            return;
        }
        telegram.copyMessages(targetChatId, qrCode.channelId(), qrCode.contentMessageIds());
    }
}

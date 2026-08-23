package ua.mytnyk.qrbot.service;

import ua.mytnyk.qrbot.domain.QrCode;

public interface ContentDeliveryStrategy {
    boolean supports(QrCode qrCode);
    void deliver(QrCode qrCode, long targetChatId);
}

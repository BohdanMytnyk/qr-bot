package ua.mytnyk.qrbot.telegram.handler.qr.list;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(10)
@Component
public class QrInfoCallbackHandler implements UpdateHandler {
    private final TelegramClient telegram;

    public QrInfoCallbackHandler(TelegramClient telegram) {
        this.telegram = telegram;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getCallbackQuery() != null
                && QrWorkflow.NOOP.equals(update.getCallbackQuery().getData());
    }

    public void handle(UpdateWebhook update) {
        telegram.answerCallback(update.getCallbackQuery().getId(), null);
    }
}

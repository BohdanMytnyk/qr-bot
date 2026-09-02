package ua.mytnyk.qrbot.telegram.handler.qr.create;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(10)
@Component
public class FinishContentQrCallbackHandler implements UpdateHandler {
    private final QrWorkflow workflow;
    private final TelegramClient telegram;

    public FinishContentQrCallbackHandler(QrWorkflow workflow, TelegramClient telegram) {
        this.workflow = workflow;
        this.telegram = telegram;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getCallbackQuery() != null
                && QrWorkflow.CONTENT_DONE.equals(update.getCallbackQuery().getData());
    }

    public void handle(UpdateWebhook update) {
        var callback = update.getCallbackQuery();
        if (!workflow.isCurrentNavigation(callback.getFrom().getId(), callback.getMessage().getMessageId())) {
            telegram.answerCallback(callback.getId(), "Ця панель завантаження застаріла.");
            return;
        }
        workflow.finishContentSelection(callback.getFrom(), callback.getMessage().getChat().getId());
        telegram.answerCallback(callback.getId(), null);
    }
}

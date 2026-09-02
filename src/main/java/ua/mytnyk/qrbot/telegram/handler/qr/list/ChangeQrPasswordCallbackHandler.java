package ua.mytnyk.qrbot.telegram.handler.qr.list;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(10)
@Component
public class ChangeQrPasswordCallbackHandler implements UpdateHandler {
    private final QrWorkflow workflow;
    private final TelegramClient telegram;

    public ChangeQrPasswordCallbackHandler(QrWorkflow workflow, TelegramClient telegram) {
        this.workflow = workflow;
        this.telegram = telegram;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getCallbackQuery() != null && update.getCallbackQuery().getData() != null
                && update.getCallbackQuery().getData().matches("^" + QrWorkflow.CHANGE_PASSWORD_PREFIX
                + "[0-9a-fA-F-]{36}$");
    }

    public void handle(UpdateWebhook update) {
        var callback = update.getCallbackQuery();
        var qrId = callback.getData().substring(QrWorkflow.CHANGE_PASSWORD_PREFIX.length());
        var view = workflow.beginPasswordChange(qrId, callback.getFrom(), callback.getMessage().getChat().getId());
        workflow.replaceNavigation(callback.getFrom(), callback.getMessage().getChat().getId(),
                callback.getMessage().getMessageId(), callback.getMessage().getText() != null, view);
        telegram.answerCallback(callback.getId(), null);
    }
}

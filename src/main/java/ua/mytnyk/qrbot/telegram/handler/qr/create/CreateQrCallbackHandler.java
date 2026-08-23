package ua.mytnyk.qrbot.telegram.handler.qr.create;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.TelegramGateway;
import ua.mytnyk.qrbot.telegram.handler.CallbackHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(10)
@Component
public class CreateQrCallbackHandler implements CallbackHandler {
    private final QrWorkflow workflow;
    private final TelegramGateway telegram;

    public CreateQrCallbackHandler(QrWorkflow workflow, TelegramGateway telegram) {
        this.workflow = workflow;
        this.telegram = telegram;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getCallbackQuery() != null
                && QrWorkflow.MENU_CREATE.equals(update.getCallbackQuery().getData());
    }

    public void handle(UpdateWebhook update) {
        var callback = update.getCallbackQuery();
        var view = workflow.beginCreation(callback.getFrom(), callback.getMessage().getChat().getId());
        workflow.replaceNavigation(callback.getFrom(), callback.getMessage().getChat().getId(),
                callback.getMessage().getMessageId(), callback.getMessage().getText() != null, view);
        telegram.answerCallback(callback.getId(), null);
    }
}

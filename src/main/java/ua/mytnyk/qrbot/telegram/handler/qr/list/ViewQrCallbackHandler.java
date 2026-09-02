package ua.mytnyk.qrbot.telegram.handler.qr.list;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.TelegramGateway;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(10)
@Component
public class ViewQrCallbackHandler implements UpdateHandler {
    private final QrWorkflow workflow;
    private final TelegramGateway telegram;

    public ViewQrCallbackHandler(QrWorkflow workflow, TelegramGateway telegram) {
        this.workflow = workflow;
        this.telegram = telegram;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getCallbackQuery() != null && update.getCallbackQuery().getData() != null
                && update.getCallbackQuery().getData().matches("^" + QrWorkflow.VIEW_PREFIX + "[0-9a-fA-F-]{36}$");
    }

    public void handle(UpdateWebhook update) {
        var callback = update.getCallbackQuery();
        var qrId = callback.getData().substring(QrWorkflow.VIEW_PREFIX.length());
        workflow.showQrDetails(qrId, callback.getFrom(), callback.getMessage().getChat().getId());
        telegram.answerCallback(callback.getId(), null);
    }
}

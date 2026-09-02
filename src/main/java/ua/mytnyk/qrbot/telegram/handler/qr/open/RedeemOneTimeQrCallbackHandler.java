package ua.mytnyk.qrbot.telegram.handler.qr.open;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.TelegramGateway;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(10)
@Component
public class RedeemOneTimeQrCallbackHandler implements UpdateHandler {
    private final QrWorkflow workflow;
    private final TelegramGateway telegram;

    public RedeemOneTimeQrCallbackHandler(QrWorkflow workflow, TelegramGateway telegram) {
        this.workflow = workflow;
        this.telegram = telegram;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getCallbackQuery() != null && update.getCallbackQuery().getData() != null
                && update.getCallbackQuery().getData().matches("^" + QrWorkflow.REDEEM_PREFIX
                + "[0-9a-fA-F-]{36}$");
    }

    public void handle(UpdateWebhook update) {
        var callback = update.getCallbackQuery();
        var qrId = callback.getData().substring(QrWorkflow.REDEEM_PREFIX.length());
        workflow.confirmRedemption(qrId, callback.getFrom(), callback.getMessage().getChat().getId());
        telegram.answerCallback(callback.getId(), null);
    }
}

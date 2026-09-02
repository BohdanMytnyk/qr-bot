package ua.mytnyk.qrbot.telegram.handler.qr.list;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.TelegramGateway;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(10)
@Component
public class ChooseChangedPasswordCaseCallbackHandler implements UpdateHandler {
    private final QrWorkflow workflow;
    private final TelegramGateway telegram;

    public ChooseChangedPasswordCaseCallbackHandler(QrWorkflow workflow, TelegramGateway telegram) {
        this.workflow = workflow;
        this.telegram = telegram;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getCallbackQuery() != null && update.getCallbackQuery().getData() != null
                && update.getCallbackQuery().getData().matches("^" + QrWorkflow.CHANGE_CASE_PREFIX
                + "(?:ignore|exact)$");
    }

    public void handle(UpdateWebhook update) {
        var callback = update.getCallbackQuery();
        workflow.chooseChangedPasswordCase(callback.getFrom(), callback.getMessage().getChat().getId(),
                callback.getData().endsWith("ignore"));
        telegram.answerCallback(callback.getId(), null);
    }
}

package ua.mytnyk.qrbot.telegram.handler.qr.create;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.TelegramGateway;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(10)
@Component
public class ChoosePasswordCaseCallbackHandler implements UpdateHandler {
    private final QrWorkflow workflow;
    private final TelegramGateway telegram;

    public ChoosePasswordCaseCallbackHandler(QrWorkflow workflow, TelegramGateway telegram) {
        this.workflow = workflow;
        this.telegram = telegram;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getCallbackQuery() != null && update.getCallbackQuery().getData() != null
                && update.getCallbackQuery().getData().matches("^" + QrWorkflow.CREATION_CASE_PREFIX
                + "(?:ignore|exact)$");
    }

    public void handle(UpdateWebhook update) {
        var callback = update.getCallbackQuery();
        if (!workflow.isCurrentNavigation(callback.getFrom().getId(), callback.getMessage().getMessageId())) {
            telegram.answerCallback(callback.getId(), "Ця панель керування застаріла.");
            return;
        }
        workflow.chooseCreationPasswordCase(callback.getFrom(), callback.getMessage().getChat().getId(),
                callback.getData().endsWith("ignore"));
        telegram.answerCallback(callback.getId(), null);
    }
}

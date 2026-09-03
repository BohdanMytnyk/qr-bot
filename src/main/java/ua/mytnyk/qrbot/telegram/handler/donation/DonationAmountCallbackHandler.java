package ua.mytnyk.qrbot.telegram.handler.donation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(10)
@Component
public class DonationAmountCallbackHandler implements UpdateHandler {
    private final QrWorkflow workflow;
    private final TelegramClient telegram;

    public DonationAmountCallbackHandler(QrWorkflow workflow, TelegramClient telegram) {
        this.workflow = workflow;
        this.telegram = telegram;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getCallbackQuery() != null && update.getCallbackQuery().getData() != null
                && update.getCallbackQuery().getData().matches("^" + QrWorkflow.DONATE_AMOUNT_PREFIX + "(1|10|50|100|500|other)$");
    }

    public void handle(UpdateWebhook update) {
        var callback = update.getCallbackQuery();
        var value = callback.getData().substring(QrWorkflow.DONATE_AMOUNT_PREFIX.length());
        if ("other".equals(value)) {
            var view = workflow.beginCustomDonation(callback.getFrom());
            workflow.replaceNavigation(callback.getFrom(), callback.getMessage().getChat().getId(),
                    callback.getMessage().getMessageId(), callback.getMessage().getText() != null, view);
        } else {
            workflow.sendDonationInvoice(callback.getFrom(), callback.getMessage().getChat().getId(),
                    Integer.parseInt(value));
        }
        telegram.answerCallback(callback.getId(), null);
    }
}

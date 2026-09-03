package ua.mytnyk.qrbot.telegram.handler.donation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(10)
@Component
public class DonationMenuCallbackHandler implements UpdateHandler {
    private final QrWorkflow workflow;
    private final TelegramClient telegram;

    public DonationMenuCallbackHandler(QrWorkflow workflow, TelegramClient telegram) {
        this.workflow = workflow;
        this.telegram = telegram;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getCallbackQuery() != null
                && QrWorkflow.MENU_DONATE.equals(update.getCallbackQuery().getData());
    }

    public void handle(UpdateWebhook update) {
        var callback = update.getCallbackQuery();
        var view = workflow.donationMenu(callback.getFrom());
        workflow.replaceNavigation(callback.getFrom(), callback.getMessage().getChat().getId(),
                callback.getMessage().getMessageId(), callback.getMessage().getText() != null, view);
        telegram.answerCallback(callback.getId(), null);
    }
}

package ua.mytnyk.qrbot.telegram.handler.qr.open;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.handler.MessageHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(1)
@Component
public class ProvideProtectedQrPasswordMessageHandler implements MessageHandler {
    private final QrWorkflow workflow;

    public ProvideProtectedQrPasswordMessageHandler(QrWorkflow workflow) {
        this.workflow = workflow;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getMessage() != null && update.getMessage().getText() != null
                && !update.getMessage().getText().startsWith("/")
                && update.getMessage().getFrom() != null
                && workflow.isWaitingForOpeningPassword(update.getMessage().getFrom().getId());
    }

    public void handle(UpdateWebhook update) {
        workflow.acceptOpeningPassword(update.getMessage());
    }
}

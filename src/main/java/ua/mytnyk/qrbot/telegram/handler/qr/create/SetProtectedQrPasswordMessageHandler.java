package ua.mytnyk.qrbot.telegram.handler.qr.create;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(30)
@Component
public class SetProtectedQrPasswordMessageHandler implements UpdateHandler {
    private final QrWorkflow workflow;

    public SetProtectedQrPasswordMessageHandler(QrWorkflow workflow) {
        this.workflow = workflow;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getMessage() != null && update.getMessage().getText() != null
                && !update.getMessage().getText().startsWith("/")
                && update.getMessage().getFrom() != null
                && workflow.isWaitingForCreationPassword(update.getMessage().getFrom().getId());
    }

    public void handle(UpdateWebhook update) {
        workflow.acceptCreationPassword(update.getMessage());
    }
}

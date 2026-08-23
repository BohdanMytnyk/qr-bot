package ua.mytnyk.qrbot.telegram.handler.qr.create;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.handler.MessageHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(1000)
@Component
public class SelectContentQrMessageHandler implements MessageHandler {
    private final QrWorkflow workflow;

    public SelectContentQrMessageHandler(QrWorkflow workflow) {
        this.workflow = workflow;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getMessage() != null && update.getMessage().getChat() != null
                && update.getMessage().getFrom() != null
                && workflow.isWaitingForContent(update.getMessage().getFrom().getId())
                && (update.getMessage().getText() == null || !update.getMessage().getText().startsWith("/"));
    }

    public void handle(UpdateWebhook update) {
        workflow.acceptContent(update.getMessage());
    }
}

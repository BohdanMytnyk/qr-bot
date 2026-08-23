package ua.mytnyk.qrbot.telegram.handler.start;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.handler.CommandHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(20)
@Component
public class StartCommandHandler implements CommandHandler {
    private final QrWorkflow workflow;

    public StartCommandHandler(QrWorkflow workflow) {
        this.workflow = workflow;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getMessage() != null && update.getMessage().getText() != null
                && update.getMessage().getText().matches("/(?:start|menu)(?:@\\w+)?");
    }

    public void handle(UpdateWebhook update) {
        workflow.showMainMenu(update.getMessage());
    }
}

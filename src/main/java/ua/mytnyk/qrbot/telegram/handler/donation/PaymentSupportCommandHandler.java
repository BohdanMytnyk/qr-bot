package ua.mytnyk.qrbot.telegram.handler.donation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(20)
@Component
public class PaymentSupportCommandHandler implements UpdateHandler {
    private final QrWorkflow workflow;

    public PaymentSupportCommandHandler(QrWorkflow workflow) {
        this.workflow = workflow;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getMessage() != null && update.getMessage().getText() != null
                && update.getMessage().getText().matches("/paysupport(?:@\\w+)?");
    }

    public void handle(UpdateWebhook update) {
        workflow.showPaymentSupport(update.getMessage());
    }
}

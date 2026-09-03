package ua.mytnyk.qrbot.telegram.handler.donation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(1)
@Component
public class DonationPreCheckoutHandler implements UpdateHandler {
    private final QrWorkflow workflow;

    public DonationPreCheckoutHandler(QrWorkflow workflow) {
        this.workflow = workflow;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getPreCheckoutQuery() != null;
    }

    public void handle(UpdateWebhook update) {
        workflow.handleDonationPreCheckout(update.getPreCheckoutQuery());
    }
}

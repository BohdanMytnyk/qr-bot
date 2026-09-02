package ua.mytnyk.qrbot.telegram;

import org.springframework.stereotype.Component;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;
import ua.mytnyk.telegram.common.webhook.TelegramWebhookHandler;

@Component
public class WebhookUpdateAdapter implements TelegramWebhookHandler {
    private final UpdateDispatcher dispatcher;

    public WebhookUpdateAdapter(UpdateDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void handle(UpdateWebhook update) {
        dispatcher.dispatch(update);
    }
}

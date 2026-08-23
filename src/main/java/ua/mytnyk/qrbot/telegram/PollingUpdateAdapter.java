package ua.mytnyk.qrbot.telegram;

import org.springframework.stereotype.Component;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.config.TelegramProperties;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;
import ua.mytnyk.telegram.common.polling.AbstractTelegramPoller;

@Component
public class PollingUpdateAdapter extends AbstractTelegramPoller {
    private final UpdateDispatcher dispatcher;
    public PollingUpdateAdapter(TelegramClient client, TelegramProperties properties, UpdateDispatcher dispatcher) {
        super(client, properties);
        this.dispatcher = dispatcher;
    }

    protected void handleUpdate(UpdateWebhook update) {
        dispatcher.dispatch(update);
    }
}

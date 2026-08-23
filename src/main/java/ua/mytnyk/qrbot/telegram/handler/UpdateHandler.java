package ua.mytnyk.qrbot.telegram.handler;

import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

public interface UpdateHandler {
    boolean supports(UpdateWebhook update);
    void handle(UpdateWebhook update);
}

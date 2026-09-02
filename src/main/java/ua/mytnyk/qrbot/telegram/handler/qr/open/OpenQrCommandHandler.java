package ua.mytnyk.qrbot.telegram.handler.qr.open;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(1)
@Component
public class OpenQrCommandHandler implements UpdateHandler {
    private static final Pattern START_PAYLOAD = Pattern.compile(
            "^/start(?:@\\w+)?\\s+([0-9a-fA-F-]{36}|[A-Za-z0-9_-]{43})$");
    private final QrWorkflow workflow;
    private final TelegramClient telegram;

    public OpenQrCommandHandler(QrWorkflow workflow, TelegramClient telegram) {
        this.workflow = workflow;
        this.telegram = telegram;
    }

    public boolean supports(UpdateWebhook update) {
        return matcher(update).matches();
    }

    public void handle(UpdateWebhook update) {
        var matcher = matcher(update);
        matcher.matches();
        try {
            workflow.open(matcher.group(1), update.getMessage());
        } catch (RestClientResponseException exception) {
            telegram.sendText(update.getMessage().getChat().getId(), "⚠️ Не вдалося доставити контент QR-коду. Зверніться до його створювача.");
            throw exception;
        }
    }

    private Matcher matcher(UpdateWebhook update) {
        var text = update.getMessage() == null ? "" : update.getMessage().getText();
        return START_PAYLOAD.matcher(text == null ? "" : text.trim());
    }
}

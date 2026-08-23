package ua.mytnyk.qrbot.telegram.handler.qr.create;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.domain.QrType;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.TelegramGateway;
import ua.mytnyk.qrbot.telegram.handler.CallbackHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;
import java.util.Set;

@Order(10)
@Component
public class SelectQrTypeCallbackHandler implements CallbackHandler {
    private static final Set<String> SUPPORTED_DATA = Set.of(
            QrWorkflow.TYPE_PREFIX + QrType.CONTENT,
            QrWorkflow.TYPE_PREFIX + QrType.PROTECTED_CONTENT,
            QrWorkflow.TYPE_PREFIX + QrType.ONE_TIME_CONTENT);
    private final QrWorkflow workflow;
    private final TelegramGateway telegram;

    public SelectQrTypeCallbackHandler(QrWorkflow workflow, TelegramGateway telegram) {
        this.workflow = workflow;
        this.telegram = telegram;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getCallbackQuery() != null
                && SUPPORTED_DATA.contains(update.getCallbackQuery().getData());
    }

    public void handle(UpdateWebhook update) {
        var callback = update.getCallbackQuery();
        var type = QrType.valueOf(callback.getData().substring(QrWorkflow.TYPE_PREFIX.length()));
        var view = workflow.selectType(callback.getFrom(), callback.getMessage().getChat().getId(), type);
        workflow.replaceNavigation(callback.getFrom(), callback.getMessage().getChat().getId(),
                callback.getMessage().getMessageId(), callback.getMessage().getText() != null, view);
        telegram.answerCallback(callback.getId(), null);
    }
}

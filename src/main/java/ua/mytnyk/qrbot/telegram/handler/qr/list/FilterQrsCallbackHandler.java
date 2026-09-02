package ua.mytnyk.qrbot.telegram.handler.qr.list;

import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.domain.QrListSort;
import ua.mytnyk.qrbot.domain.QrType;
import ua.mytnyk.qrbot.domain.QrStatus;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Order(10)
@Component
public class FilterQrsCallbackHandler implements UpdateHandler {
    private static final Set<String> SUPPORTED_DATA = Set.of(
            QrWorkflow.FILTER_TYPE_PREFIX + QrType.CONTENT,
            QrWorkflow.FILTER_TYPE_PREFIX + QrType.SINGLE_USE,
            QrWorkflow.FILTER_TYPE_PREFIX + QrType.COUPON,
            QrWorkflow.FILTER_STATUS_PREFIX + QrStatus.ACTIVE,
            QrWorkflow.FILTER_STATUS_PREFIX + QrStatus.REDEEMED,
            QrWorkflow.SORT_PREFIX + QrListSort.NEWEST,
            QrWorkflow.SORT_PREFIX + QrListSort.OLDEST);
    private final QrWorkflow workflow;
    private final TelegramClient telegram;

    public FilterQrsCallbackHandler(QrWorkflow workflow, TelegramClient telegram) {
        this.workflow = workflow;
        this.telegram = telegram;
    }

    public boolean supports(UpdateWebhook update) {
        return update.getCallbackQuery() != null
                && (SUPPORTED_DATA.contains(update.getCallbackQuery().getData())
                || update.getCallbackQuery().getData().matches("^" + QrWorkflow.PAGE_PREFIX + "\\d+$"));
    }

    public void handle(UpdateWebhook update) {
        var callback = update.getCallbackQuery();
        var view = workflow.updateListPreferences(callback.getFrom(),
                callback.getMessage().getChat().getId(), callback.getData());
        workflow.replaceNavigation(callback.getFrom(), callback.getMessage().getChat().getId(),
                callback.getMessage().getMessageId(), callback.getMessage().getText() != null, view);
        telegram.answerCallback(callback.getId(), null);
    }
}

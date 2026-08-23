package ua.mytnyk.qrbot.telegram;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.telegram.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

@Component
public class UpdateDispatcher {
    private static final Logger log = LoggerFactory.getLogger(UpdateDispatcher.class);
    private static final int LOCK_COUNT = 256;
    private final List<UpdateHandler> handlers;
    private final Object[] userLocks = new Object[LOCK_COUNT];

    public UpdateDispatcher(List<UpdateHandler> handlers) {
        this.handlers = handlers;
        for (var index = 0; index < userLocks.length; index++) {
            userLocks[index] = new Object();
        }
    }

    public void dispatch(UpdateWebhook update) {
        var userId = userId(update);
        if (userId == null) {
            dispatchSafely(update);
            return;
        }
        var lock = userLocks[Math.floorMod(Long.hashCode(userId), userLocks.length)];
        synchronized (lock) {
            dispatchSafely(update);
        }
    }

    private void dispatchSafely(UpdateWebhook update) {
        handlers.stream().filter(handler -> handler.supports(update)).findFirst().ifPresentOrElse(handler -> {
            try {
                handler.handle(update);
            }
            catch (RuntimeException exception) {
                log.error("Update handling failed updateId={} handler={}", update.getUpdateId(), handler.getClass().getSimpleName(), exception);
            }
        }, () -> log.debug("No handler accepted updateId={}", update.getUpdateId()));
    }

    private Long userId(UpdateWebhook update) {
        if (update.getMessage() != null && update.getMessage().getFrom() != null) {
            return update.getMessage().getFrom().getId();
        }
        if (update.getCallbackQuery() != null && update.getCallbackQuery().getFrom() != null) {
            return update.getCallbackQuery().getFrom().getId();
        }
        return null;
    }
}

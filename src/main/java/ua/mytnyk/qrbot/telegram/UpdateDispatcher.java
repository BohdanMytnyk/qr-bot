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
    private final List<UpdateHandler> handlers;
    public UpdateDispatcher(List<UpdateHandler> handlers) {
        this.handlers = handlers;
    }

    public void dispatch(UpdateWebhook update) {
        handlers.stream().filter(handler -> handler.supports(update)).findFirst().ifPresentOrElse(handler -> {
            try {
                handler.handle(update);
            }
            catch (RuntimeException exception) {
                log.error("Update handling failed updateId={} handler={}", update.getUpdateId(), handler.getClass().getSimpleName(), exception);
            }
        }, () -> log.debug("No handler accepted updateId={}", update.getUpdateId()));
    }
}

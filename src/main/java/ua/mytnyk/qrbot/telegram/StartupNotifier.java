package ua.mytnyk.qrbot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupNotifier {
    private static final Logger log = LoggerFactory.getLogger(StartupNotifier.class);
    private final TelegramGateway telegram;
    private final long notificationChatId;

    public StartupNotifier(TelegramGateway telegram,
                           @Value("${telegram.restart-notification-chat-id:0}") long notificationChatId) {
        this.telegram = telegram;
        this.notificationChatId = notificationChatId;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void notifyRestart() {
        try {
            telegram.publishCommands();
            log.info("Telegram bot commands published");
        } catch (RuntimeException exception) {
            log.error("Could not publish Telegram bot commands", exception);
        }
        if (notificationChatId == 0) {
            log.warn("Restart notification skipped: TELEGRAM_RESTART_NOTIFICATION_CHAT_ID is not configured");
            return;
        }
        try {
        telegram.sendText(notificationChatId, "🔄 перезапущено");
            log.info("Restart notification sent chatId={}", notificationChatId);
        } catch (RuntimeException exception) {
            log.error("Could not send restart notification chatId={}", notificationChatId, exception);
        }
    }
}

package ua.mytnyk.qrbot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.model.common.api.BotCommand;

import java.util.List;

@Component
@ConditionalOnProperty(name = "telegram.startup-notifier.enabled", havingValue = "true", matchIfMissing = true)
public class StartupNotifier {
    private static final Logger log = LoggerFactory.getLogger(StartupNotifier.class);
    private final TelegramClient telegram;
    private final long notificationChatId;
    private final boolean restartNotificationEnabled;

    public StartupNotifier(TelegramClient telegram,
                           @Value("${telegram.restart-notification-chat-id:0}") long notificationChatId,
                           @Value("${telegram.startup-notifier.restart-notification-enabled:true}")
                           boolean restartNotificationEnabled) {
        this.telegram = telegram;
        this.notificationChatId = notificationChatId;
        this.restartNotificationEnabled = restartNotificationEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void notifyRestart() {
        try {
            telegram.publishCommands(List.of(new BotCommand("start", "🏠 Головне меню")));
            log.info("Telegram bot commands published");
        } catch (RuntimeException exception) {
            log.error("Could not publish Telegram bot commands", exception);
        }
        if (!restartNotificationEnabled) {
            log.info("Restart notification disabled");
            return;
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

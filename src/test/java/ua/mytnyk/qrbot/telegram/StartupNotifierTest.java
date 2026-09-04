package ua.mytnyk.qrbot.telegram;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.model.common.api.BotCommand;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.eq;

class StartupNotifierTest {
    private static final List<BotCommand> COMMANDS = List.of(new BotCommand("start", "🏠 Головне меню"));

    @Test
    void publishesCommandsAndSkipsNotificationWhenChatIsUnset() {
        var telegram = mock(TelegramClient.class);
        new StartupNotifier(telegram, 0, true).notifyRestart();
        verify(telegram).publishCommands(COMMANDS);
    }

    @Test
    void publishesCommandsAndSendsNotificationToConfiguredChat() {
        var telegram = mock(TelegramClient.class);
        new StartupNotifier(telegram, 123L, true).notifyRestart();
        verify(telegram).publishCommands(COMMANDS);
        var text = ArgumentCaptor.forClass(String.class);
        verify(telegram).sendText(eq(123L), text.capture());
        org.assertj.core.api.Assertions.assertThat(text.getValue()).isNotBlank();
    }

    @Test
    void isolatesFailuresInCommandPublicationAndRestartNotification() {
        var telegram = mock(TelegramClient.class);
        doThrow(new RuntimeException("publish")).when(telegram).publishCommands(List.of(new BotCommand("start", "ðŸ  ÐœÐµÐ½ÑŽ")));
        doThrow(new RuntimeException("notify")).when(telegram).sendText(123L, "ðŸ”„ Ð¿ÐµÑ€ÐµÐ·Ð°Ð¿ÑƒÑ‰ÐµÐ½Ð¾");
        new StartupNotifier(telegram, 123L, true).notifyRestart();
    }

    @Test
    void publishesCommandsWithoutRestartMessageWhenNotificationIsDisabled() {
        var telegram = mock(TelegramClient.class);
        new StartupNotifier(telegram, 123L, false).notifyRestart();
        verify(telegram).publishCommands(COMMANDS);
        verify(telegram, never()).sendText(eq(123L), org.mockito.ArgumentMatchers.anyString());
    }
}

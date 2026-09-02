package ua.mytnyk.qrbot.telegram.handler.start;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.telegram.common.model.common.webhook.Message;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StartCommandHandlerTest {
    private final QrWorkflow workflow = mock(QrWorkflow.class);
    private final StartCommandHandler handler = new StartCommandHandler(workflow);

    @ParameterizedTest
    @ValueSource(strings = {"/start", "/menu", "/start@test_bot", "/menu@Bot123"})
    void supportsEveryValidMenuCommand(String text) {
        assertThat(handler.supports(update(text))).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"start", "/unknown", "/start extra", "/start@", "/START"})
    void rejectsNonMenuMessages(String text) {
        assertThat(handler.supports(update(text))).isFalse();
    }

    @org.junit.jupiter.api.Test
    void rejectsUpdateWithoutMessage() {
        assertThat(handler.supports(new UpdateWebhook())).isFalse();
    }

    @org.junit.jupiter.api.Test
    void delegatesHandlingToWorkflow() {
        var update = update("/start");
        handler.handle(update);
        verify(workflow).showMainMenu(update.getMessage());
    }

    private static UpdateWebhook update(String text) {
        return UpdateWebhook.builder().message(Message.builder().text(text).build()).build();
    }
}

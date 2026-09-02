package ua.mytnyk.qrbot.telegram.handler;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.handler.menu.MainMenuCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.create.ChoosePasswordCaseCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.create.FinishContentQrCallbackHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.create.SkipCreationPasswordCallbackHandler;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.*;
import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

class StaleCallbackHandlingTest {
    @ParameterizedTest
    @MethodSource("handlers")
    void staleNavigationIsRejectedWithoutWorkflowMutation(String data, HandlerFactory factory) {
        var workflow = mock(QrWorkflow.class);
        var telegram = mock(TelegramClient.class);
        var handler = factory.create(workflow, telegram);
        when(workflow.isCurrentNavigation(77L, 9)).thenReturn(false);
        handler.handle(update(data));
        var text = ArgumentCaptor.forClass(String.class);
        verify(telegram).answerCallback(eq("cb"), text.capture());
        assertThat(text.getValue()).isNotBlank();
        verify(workflow).isCurrentNavigation(77L, 9);
        verifyNoMoreInteractions(workflow);
    }

    static Stream<Arguments> handlers() {
        return Stream.of(
                Arguments.of(QrWorkflow.MENU_HOME, (HandlerFactory) MainMenuCallbackHandler::new),
                Arguments.of(QrWorkflow.CONTENT_DONE, (HandlerFactory) FinishContentQrCallbackHandler::new),
                Arguments.of(QrWorkflow.PROTECTION_PREFIX + "skip", (HandlerFactory) SkipCreationPasswordCallbackHandler::new),
                Arguments.of(QrWorkflow.CREATION_CASE_PREFIX + "ignore", (HandlerFactory) ChoosePasswordCaseCallbackHandler::new));
    }
    private static UpdateWebhook update(String data) {
        var user = User.builder().id(77).build();
        return UpdateWebhook.builder().callbackQuery(CallbackQuery.builder().id("cb").from(user)
                .message(Message.builder().messageId(9).from(user).chat(Chat.builder().id(88).build()).build())
                .data(data).build()).build();
    }
    @FunctionalInterface interface HandlerFactory { UpdateHandler create(QrWorkflow workflow, TelegramClient telegram); }
}

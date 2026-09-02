package ua.mytnyk.qrbot.telegram.handler;

import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.handler.qr.create.SelectContentQrMessageHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.create.SetProtectedQrPasswordMessageHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.list.SetQrPasswordMessageHandler;
import ua.mytnyk.qrbot.telegram.handler.qr.open.ProvideProtectedQrPasswordMessageHandler;
import ua.mytnyk.telegram.common.handler.UpdateHandler;
import ua.mytnyk.telegram.common.model.common.webhook.Chat;
import ua.mytnyk.telegram.common.model.common.webhook.Message;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;
import ua.mytnyk.telegram.common.model.common.webhook.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageHandlerContractTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("stateHandlers")
    void passwordHandlersRequireTextActorNonCommandAndMatchingState(
            String name, Function<QrWorkflow, UpdateHandler> factory, StateCheck stateCheck,
            HandlerCall handlerCall) {
        var workflow = mock(QrWorkflow.class);
        var handler = factory.apply(workflow);
        assertThat(handler.supports(new UpdateWebhook())).isFalse();
        assertThat(handler.supports(update(null, actor()))).isFalse();
        assertThat(handler.supports(update("value", null))).isFalse();
        assertThat(handler.supports(update("/cancel", actor()))).isFalse();
        assertThat(handler.supports(update("value", actor()))).isFalse();

        stateCheck.stub(workflow, 77L, true);
        var valid = update("value", actor());
        assertThat(handler.supports(valid)).isTrue();
        handler.handle(valid);
        handlerCall.verify(workflow, valid.getMessage());
    }

    @ParameterizedTest
    @MethodSource("contentState")
    void contentHandlerRequiresMessageChatActorStateAndNonCommand(boolean textPresent) {
        var workflow = mock(QrWorkflow.class);
        var handler = new SelectContentQrMessageHandler(workflow);
        assertThat(handler.supports(new UpdateWebhook())).isFalse();
        assertThat(handler.supports(UpdateWebhook.builder().message(Message.builder().from(actor()).build()).build()))
                .isFalse();
        assertThat(handler.supports(updateWithChat("content", null))).isFalse();
        assertThat(handler.supports(updateWithChat("/cancel", actor()))).isFalse();
        assertThat(handler.supports(updateWithChat(textPresent ? "content" : null, actor()))).isFalse();
        when(workflow.isWaitingForContent(77L)).thenReturn(true);
        var valid = updateWithChat(textPresent ? "content" : null, actor());
        assertThat(handler.supports(valid)).isTrue();
        handler.handle(valid);
        verify(workflow).acceptContent(valid.getMessage());
    }

    static Stream<Arguments> stateHandlers() {
        return Stream.of(
                Arguments.of("creation password", (Function<QrWorkflow, UpdateHandler>) SetProtectedQrPasswordMessageHandler::new,
                        (StateCheck) (workflow, id, value) -> when(workflow.isWaitingForCreationPassword(id)).thenReturn(value),
                        (HandlerCall) (workflow, message) -> verify(workflow).acceptCreationPassword(message)),
                Arguments.of("changed password", (Function<QrWorkflow, UpdateHandler>) SetQrPasswordMessageHandler::new,
                        (StateCheck) (workflow, id, value) -> when(workflow.isWaitingForPasswordChange(id)).thenReturn(value),
                        (HandlerCall) (workflow, message) -> verify(workflow).acceptPasswordChange(message)),
                Arguments.of("opening password", (Function<QrWorkflow, UpdateHandler>) ProvideProtectedQrPasswordMessageHandler::new,
                        (StateCheck) (workflow, id, value) -> when(workflow.isWaitingForOpeningPassword(id)).thenReturn(value),
                        (HandlerCall) (workflow, message) -> verify(workflow).acceptOpeningPassword(message))
        );
    }

    static Stream<Boolean> contentState() {
        return Stream.of(true, false);
    }

    private static UpdateWebhook update(String text, User actor) {
        return UpdateWebhook.builder().message(Message.builder().text(text).from(actor).build()).build();
    }

    private static UpdateWebhook updateWithChat(String text, User actor) {
        return UpdateWebhook.builder().message(Message.builder().text(text).from(actor)
                .chat(Chat.builder().id(88).build()).build()).build();
    }

    private static User actor() {
        return User.builder().id(77).username("alice").build();
    }

    @FunctionalInterface interface StateCheck { void stub(QrWorkflow workflow, long id, boolean value); }
    @FunctionalInterface interface HandlerCall { void verify(QrWorkflow workflow, Message message); }
}

package ua.mytnyk.qrbot.telegram.handler.open;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.handler.qr.open.OpenQrCommandHandler;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.model.common.webhook.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;

class OpenQrCommandHandlerTest {
    @Test
    void rejectsMissingOrMalformedPayloads() {
        var handler = new OpenQrCommandHandler(mock(QrWorkflow.class), mock(TelegramClient.class));
        assertThat(handler.supports(new UpdateWebhook())).isFalse();
        assertThat(handler.supports(UpdateWebhook.builder().message(Message.builder().text("/start").build()).build())).isFalse();
        assertThat(handler.supports(UpdateWebhook.builder().message(Message.builder()
                .text("/start invalid").build()).build())).isFalse();
    }

    @Test
    void reportsTelegramDeliveryFailureAndRethrows() {
        var workflow = mock(QrWorkflow.class);
        var telegram = mock(TelegramClient.class);
        var handler = new OpenQrCommandHandler(workflow, telegram);
        var message = Message.builder().from(User.builder().id(1).build())
                .chat(Chat.builder().id(2).build()).text("/start 123e4567-e89b-12d3-a456-426614174000").build();
        var failure = HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "stub", null, null, null);
        doThrow(failure).when(workflow).open("123e4567-e89b-12d3-a456-426614174000", message);
        assertThatThrownBy(() -> handler.handle(UpdateWebhook.builder().message(message).build()))
                .isSameAs(failure);
        var text = ArgumentCaptor.forClass(String.class);
        verify(telegram).sendText(eq(2L), text.capture());
        org.assertj.core.api.Assertions.assertThat(text.getValue()).isNotBlank();
    }
}

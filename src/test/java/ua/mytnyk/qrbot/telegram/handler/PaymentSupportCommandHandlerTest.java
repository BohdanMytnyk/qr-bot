package ua.mytnyk.qrbot.telegram.handler;

import org.junit.jupiter.api.Test;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.handler.donation.PaymentSupportCommandHandler;
import ua.mytnyk.telegram.common.model.common.webhook.Message;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentSupportCommandHandlerTest {
    @Test
    void supportsPlainAndAddressedPaymentSupportCommandsAndDelegates() {
        var workflow = mock(QrWorkflow.class);
        var handler = new PaymentSupportCommandHandler(workflow);
        assertThat(handler.supports(new UpdateWebhook())).isFalse();
        assertThat(handler.supports(update("/other"))).isFalse();
        assertThat(handler.supports(update("/paysupport"))).isTrue();
        assertThat(handler.supports(update("/paysupport@qr_bot"))).isTrue();
        var update = update("/paysupport");
        handler.handle(update);
        verify(workflow).showPaymentSupport(update.getMessage());
    }

    private UpdateWebhook update(String text) {
        return UpdateWebhook.builder().message(Message.builder().text(text).build()).build();
    }
}

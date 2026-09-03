package ua.mytnyk.qrbot.telegram.handler;

import org.junit.jupiter.api.Test;
import ua.mytnyk.qrbot.service.QrWorkflow;
import ua.mytnyk.qrbot.telegram.handler.donation.DonationPreCheckoutHandler;
import ua.mytnyk.qrbot.telegram.handler.donation.DonationSuccessfulPaymentHandler;
import ua.mytnyk.telegram.common.model.common.webhook.Message;
import ua.mytnyk.telegram.common.model.common.webhook.PreCheckoutQuery;
import ua.mytnyk.telegram.common.model.common.webhook.SuccessfulPayment;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DonationPaymentHandlerTest {
    @Test
    void preCheckoutHandlerSupportsAndDelegatesOnlyPreCheckoutUpdates() {
        var workflow = mock(QrWorkflow.class);
        var handler = new DonationPreCheckoutHandler(workflow);
        var query = new PreCheckoutQuery();
        var update = UpdateWebhook.builder().preCheckoutQuery(query).build();
        assertThat(handler.supports(new UpdateWebhook())).isFalse();
        assertThat(handler.supports(update)).isTrue();
        handler.handle(update);
        verify(workflow).handleDonationPreCheckout(query);
    }

    @Test
    void successfulHandlerSupportsAndDelegatesOnlySuccessfulPayments() {
        var workflow = mock(QrWorkflow.class);
        var handler = new DonationSuccessfulPaymentHandler(workflow);
        var message = Message.builder().successfulPayment(new SuccessfulPayment()).build();
        var update = UpdateWebhook.builder().message(message).build();
        assertThat(handler.supports(new UpdateWebhook())).isFalse();
        assertThat(handler.supports(UpdateWebhook.builder().message(new Message()).build())).isFalse();
        assertThat(handler.supports(update)).isTrue();
        handler.handle(update);
        verify(workflow).acceptSuccessfulDonation(message);
    }
}

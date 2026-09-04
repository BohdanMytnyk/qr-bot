package ua.mytnyk.qrbot.telegram;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import ua.mytnyk.telegram.common.handler.UpdateContext;
import ua.mytnyk.telegram.common.handler.UpdateDispatchResult;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedCustomerInterceptorTest {
    private final AllowedCustomerInterceptor interceptor = new AllowedCustomerInterceptor(441341235L);

    @Test
    void permitsOnlyConfiguredCustomer() {
        var calls = new AtomicInteger();
        var chain = (ua.mytnyk.telegram.common.handler.UpdateInterceptor.Chain) context -> {
            calls.incrementAndGet();
            return UpdateDispatchResult.unhandled();
        };

        interceptor.intercept(context(1L, null), chain);
        interceptor.intercept(context(2L, 12L), chain);
        interceptor.intercept(context(3L, 441341235L), chain);

        assertThat(calls).hasValue(1);
    }

    private UpdateContext context(long updateId, Long customerId) {
        return new UpdateContext(UpdateWebhook.builder().updateId(updateId).build(), updateId, customerId);
    }
}

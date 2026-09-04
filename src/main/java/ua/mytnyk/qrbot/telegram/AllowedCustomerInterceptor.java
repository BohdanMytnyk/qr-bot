package ua.mytnyk.qrbot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.telegram.common.handler.UpdateContext;
import ua.mytnyk.telegram.common.handler.UpdateDispatchResult;
import ua.mytnyk.telegram.common.handler.UpdateInterceptor;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "qr.access.allowed-customer-id")
public class AllowedCustomerInterceptor implements UpdateInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AllowedCustomerInterceptor.class);
    private final long allowedCustomerId;

    public AllowedCustomerInterceptor(
            @org.springframework.beans.factory.annotation.Value("${qr.access.allowed-customer-id}") long allowedCustomerId) {
        this.allowedCustomerId = allowedCustomerId;
    }

    @Override
    public UpdateDispatchResult intercept(UpdateContext context, Chain chain) {
        if (context.customerId() == null || context.customerId() != allowedCustomerId) {
            log.warn("Telegram update rejected by customer allowlist updateId={} customerId={}",
                    context.updateId(), context.customerId());
            return UpdateDispatchResult.unhandled();
        }
        return chain.proceed(context);
    }
}

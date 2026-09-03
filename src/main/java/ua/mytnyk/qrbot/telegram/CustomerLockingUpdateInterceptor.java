package ua.mytnyk.qrbot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ua.mytnyk.telegram.common.handler.UpdateContext;
import ua.mytnyk.telegram.common.handler.UpdateDispatchResult;
import ua.mytnyk.telegram.common.handler.UpdateInterceptor;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class CustomerLockingUpdateInterceptor implements UpdateInterceptor {
    private static final Logger log = LoggerFactory.getLogger(CustomerLockingUpdateInterceptor.class);
    private static final int LOCK_COUNT = 1024;
    private final Object[] customerLocks = new Object[LOCK_COUNT];

    public CustomerLockingUpdateInterceptor() {
        for (int index = 0; index < customerLocks.length; index++) {
            customerLocks[index] = new Object();
        }
    }

    @Override
    public UpdateDispatchResult intercept(UpdateContext context, Chain chain) {
        if (context.customerId() == null) {
            log.debug("Customer lock bypassed updateId={} customerId=null", context.updateId());
            return chain.proceed(context);
        }
        Object lock = customerLocks[Math.floorMod(Long.hashCode(context.customerId()), customerLocks.length)];
        synchronized (lock) {
            log.debug("Customer lock acquired updateId={} customerId={}",
                    context.updateId(), context.customerId());
            return chain.proceed(context);
        }
    }
}

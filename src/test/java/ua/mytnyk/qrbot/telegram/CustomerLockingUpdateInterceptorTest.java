package ua.mytnyk.qrbot.telegram;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import ua.mytnyk.telegram.common.handler.UpdateContext;
import ua.mytnyk.telegram.common.handler.UpdateDispatchResult;
import ua.mytnyk.telegram.common.model.common.webhook.UpdateWebhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerLockingUpdateInterceptorTest {
    private final CustomerLockingUpdateInterceptor interceptor = new CustomerLockingUpdateInterceptor();

    @Test
    void bypassesLockWhenCustomerIsUnknown() {
        AtomicInteger calls = new AtomicInteger();
        UpdateDispatchResult result = interceptor.intercept(context(1L, null), ignored -> {
            calls.incrementAndGet();
            return UpdateDispatchResult.unhandled();
        });
        assertThat(calls).hasValue(1);
        assertThat(result.status()).isEqualTo(UpdateDispatchResult.Status.UNHANDLED);
    }

    @Test
    void serializesConcurrentUpdatesForSameCustomer() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> interceptor.intercept(context(1L, 42L), ignored -> {
                maximumActive.accumulateAndGet(active.incrementAndGet(), Math::max);
                firstEntered.countDown();
                await(releaseFirst);
                active.decrementAndGet();
                return UpdateDispatchResult.unhandled();
            }));
            assertThat(firstEntered.await(2, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> interceptor.intercept(context(2L, 42L), ignored -> {
                maximumActive.accumulateAndGet(active.incrementAndGet(), Math::max);
                active.decrementAndGet();
                return UpdateDispatchResult.unhandled();
            }));
            Thread.sleep(100);
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertThat(maximumActive).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void allowsDifferentCustomersToProceedConcurrently() throws Exception {
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> invokeWaiting(context(1L, 1L), bothEntered, release));
            var second = executor.submit(() -> invokeWaiting(context(2L, 2L), bothEntered, release));
            assertThat(bothEntered.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void releasesLockAfterFailure() {
        UpdateContext context = context(1L, 42L);
        assertThatThrownBy(() -> interceptor.intercept(context, ignored -> {
            throw new IllegalStateException("failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(interceptor.intercept(context, ignored -> UpdateDispatchResult.unhandled()).status())
                .isEqualTo(UpdateDispatchResult.Status.UNHANDLED);
    }

    private void invokeWaiting(UpdateContext context, CountDownLatch entered, CountDownLatch release) {
        interceptor.intercept(context, ignored -> {
            entered.countDown();
            await(release);
            return UpdateDispatchResult.unhandled();
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("Timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private UpdateContext context(Long updateId, Long customerId) {
        return new UpdateContext(UpdateWebhook.builder().updateId(updateId).build(), updateId, customerId);
    }
}
